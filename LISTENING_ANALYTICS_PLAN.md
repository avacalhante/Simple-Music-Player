# Listening Analytics — Parallel Work Plan

Last updated: 2026-10-31
Branch intent: a new parallel line for song-centric tracking/analytics, independent of `feat/toolchain-refresh` (Phase 5).

## Goal

Add a privacy-local listening-analytics subsystem that records what the user plays and answers questions like the ones in the request: most-played songs/artists/albums/genres/playlists, listening time by period, time-of-day/day-of-week habits, session stats, completion/skip behavior, undiscovered/forgotten tracks, playlist engagement, and fun periodic summaries.

This plan is intentionally scoped to start small and stay independent of the current toolchain refresh.

## Non-goals / frozen areas

- No UI/appearance changes beyond what is needed to expose stats later. The existing freeze on UI still applies; this plan is backend-first.
- No changes to playback behavior, queue persistence, Media3 version, or the toolchain.
- No mixing this feature into `songs.db`. It gets its own database file.
- Sessions are out of scope for the first version; they come later.
- No analytics network calls, no external services, no identifiers leaving the device.

## Independence from current branch

This work is designed to not touch:
- `Track`, `Playlist`, `QueueItem`, `Album`, `Artist`, `Genre`, `TrackSeen`, `TrackHashCache`
- `SongsDao`, `QueueItemsDao`, existing migrations up to 15
- `MediaItemProvider` queue-saving logic
- `PlayerListener` existing split of `updatePlaybackState()` vs `saveCurrentTrackProgress()`
- any Phase 5 toolchain changes

The only coupling is an additive call into a new observer from existing playback callbacks. That is intentionally small and read-only from the playback side: playback code does not depend on analytics working.

## Architecture

### Two databases, not one

- Keep `songs.db` for the library.
- Add a second database, e.g. `analytics.db`, for listening events and summary tables.
- Rationale: this keeps the main library schema, migrations, and scan logic out of the analytics change set. It also avoids accidental coupling between scan/rewrite behavior and analytics writes.

### New package

- `com.simplemobiletools.musicplayer.analytics`
- Contents:
  - database class + Room entities/DAOs
  - a small `ListeningAnalytics` singleton accessed via `context.analytics` extension
  - an `AnalyticsRecorder` used by playback callbacks
  - a read layer for summaries

### Threading

This app does not already use coroutines broadly in the parts I touched, so I would not introduce them for this feature alone. I would use the existing pattern of a dedicated single-thread executor for persistence, similar to `MediaItemProvider.persistenceExecutor`. All Room access for analytics would go through that executor, and the singleton would be created from `Context.applicationContext`.

## Data model

### Event tables

1. `play_start`
   - `id` long primary key autogenerate
   - `track_id` long not null — the `media_store_id`, not the Room `Track.id`
   - `playlist_id` int — the playlist the playback came from, if known; `0` if not
   - `source` text — one of `all_tracks`, `folder`, `playlist`, `album`, `artist`, `genre`, `queue`, `unknown`
   - `started_at` int not null — epoch seconds
   - `session_id` long — reserved for the future sessions feature; for now always `0L`

2. `play_end`
   - `id` long primary key autogenerate
   - `play_start_id` long not null
   - `ended_at` int not null — epoch seconds
   - `position_at_end` int not null — seconds
   - `fully_played` int not null — 1/0
   - `skipped` int not null — 1/0
   - `quick_skip` int not null — 1/0, defined below

I chose two event tables instead of one wider table because "did this play finish, and how?" is a separate concern from "when did listening start", and it keeps completion/skip semantics explicit.

### Reserved semantics

- `track_id` is always the media-store id, because that is what crosses the playback boundary today.
- `playlist_id` is the playlist id when playback originated from a playlist context; otherwise `0`.
- `source` describes the browse origin where possible. I would fill it when the app knows it; otherwise `unknown`.
- `fully_played` means playback reached near the track end without a skip/stop before that. I would define "near the end" as `position_at_end >= duration - small_leeway`.
- `skipped` means playback stopped or jumped away before `fully_played`.
- `quick_skip` means `skipped` and `position_at_end` is under a short threshold. I picked **15 seconds** as the default quick-skip window. That is a judgment call, not a measured one; change it if you want different behavior.

## Ingestion hooks

I would attach one new listener to playback and keep it read-only with respect to the rest of the player:

1. Track start
   - Fires when a new media item starts playing.
   - Record `play_start`.
   - Determine `playlist_id`/`source` from the current playback context if available; otherwise store `0`/`unknown`.

2. Track end
   - Fires when playback of the current item ends or is abandoned.
   - Record `play_end` with final position and completion/skip flags.

3. Position/stop caveats
   - I would not record every position change.
   - "End" should be defined conservatively so reopens, pauses, and seeks do not create nonsense ends. The exact rule I would use:
     - a real media-item transition away from the track
     - playback stopping while the current item is set
     - a seek that leaves the track and does not return before a short grace window
   - This is where sessions would later sharpen the logic. For now, I would keep it simple and treat each media item as its own play attempt.

### Why this stays cheap

- One row at start, one row at end.
- No queue rewrite.
- No per-timeline-change work.
- No scanning or library reload involvement.

## First-version read layer

Before sessions, the meaningful summaries are mostly per-track and per-period. I would implement read-only DAO queries and small Kotlin helpers for:

### Listening habits
- Most-played tracks/artists/albums/genres/playlists
  - Count `play_start` rows grouped by `track_id`, then resolve metadata using `Track` queries when needed.
  - Playlist totals use `playlist_id` on `play_start`.
- Total listening time by day/week/month/year
  - Sum `play_end.position_at_end - play_start.started_at` over buckets of `started_at`.
  - I would exclude unfinished plays from "listening time" or count them only up to `position_at_end`.
- Most-listened-to time of day and day of week
  - Histogram over `started_at`.
- First song played each day and common "day-starter"
  - Min `started_at` per day, then frequency of the resulting tracks.
- Repeat rate within a short window
  - For each `play_start`, check whether the same `track_id` has another `play_start` within N minutes. I would pick **10 minutes** as the default repeat window. Again, a default, not a measured value.

### Discovery
- Never-played tracks: `Track` rows with no matching `play_start`.
- Not played in a long time: `Track` rows whose latest `play_start.started_at` is older than a chosen threshold. I would default to **90 days** for "long time". The app can surface this as "not played recently".

### Engagement
- Completion rate: fraction of `play_end` rows where `fully_played = 1`.
- Skip rate: fraction where `skipped = 1`.
- Quick-skip rate: fraction where `quick_skip = 1`.
- Tracks you regularly stop partway: group by `track_id`, rank by average completion ratio or by count of partial stops.

### Library and playlists
- Most-played playlist and time spent per playlist: from `play_start.playlist_id` and duration sum.
- Songs added versus songs played: compare library track count against count of distinct played `track_id`.
- "Forgotten favorites": played tracks that are now stale by the same "not played recently" rule. This is more meaningful once there is a "favorites" concept; until then I would treat it as "often/ever played but not recently".

### Fun summaries
- Top 5 tracks or artists per week.
- "Soundtrack of the month": top tracks/artists for a month bucket.
- Most-played track on a particular date.
- Milestones: 100 hours, 1,000 plays, etc., computed from aggregate totals over `play_start`/`play_end`.

## What I would not build yet

- Session tables, session reconstruction, longest uninterrupted session, time-between-sessions.
- Playlist completion rate as a first-class metric; that needs session/playlist-end semantics I would rather add after the event pipeline is proven.
- Expectation of perfectly accurate `source`/`playlist_id` for every play. Early on, missing context will be recorded as `unknown`/`0`.

## Migration and version plan

- Analytics schema starts at version 1 in `analytics.db`.
- Because it is a new database, there is no legacy migration burden for the first implementation.
- If the analytics schema later changes, it will get its own migration chain and schema location, independent of `songs.db` version 15.

## Files I expect to add

- `analytics/AnalyticsDatabase.kt`
- `analytics/ListeningAnalytics.kt` and `analytics/extensions/Context.kt`
- `analytics/AnalyticsRecorder.kt`
- `analytics/daos/*` or inline DAOs inside the database class
- entity files for `play_start` and `play_end`
- migration/schema output under `app/schemas/...` for the analytics database if Room is configured to emit it

I would avoid touching existing files except for the small playback-callbacks integration point and the `Context` extension that exposes `analytics`.

## Integration point

I would wire ingestion into `PlayerListener` or a similarly small playback callback, and I would make the analytics recorder tolerant of missing context. The recorder should never block playback, and it should degrade gracefully if the analytics database is unavailable.

## Verification

There are no tests in this project. For this feature I would verify by:
- compiling `:app:compileFdroidDebugKotlin`
- installing a debug build on the SDK 33 device
- exercising start/stop/seek/next and then inspecting the analytics database rows and summary queries
- confirming playback and queue behavior are unchanged

## Implementation notes (current state)

What actually exists on this branch:

- `analytics/AnalyticsDatabase.kt` — `analytics.db`, version 1, schema exported to `app/schemas/.../AnalyticsDatabase/1.json`.
- `analytics/entities/PlayStart.kt` and `PlayEnd.kt` — event tables. `play_start` also holds `duration_seconds`, because the player duration is only known after preparation; it is filled in later via `updateDuration`.
- `analytics/daos/` — `PlayStartDao`, `PlayEndDao` and the query result classes in `AnalyticsQueries.kt` (columns mapped with `@ColumnInfo`).
- `analytics/ListeningAnalytics.kt` — the singleton store with the first-version read layer.
- `analytics/AnalyticsRecorder.kt` — per-play state machine (start / position / end).
- `analytics/AnalyticsPlaybackBridge.kt` — the only playback-facing type; posts every write to `AnalyticsExecutor` (single thread) so playback is never blocked and event order is preserved.
- `analytics/SessionContext.kt` — maps the current library root to `source`.
- `analytics/AnalyticsExecutor.kt`, `analytics/extensions/AnalyticsContext.kt` — executor and `context.analytics`.

Stats screen: `StatsActivity` (opened from the main menu) shows the 20 most played tracks resolved against the library, and listening time for the last 14 days. Daily buckets use device-local dates, and a day's listening time is the sum of the recorded end positions, so plays that never ended are excluded.

End positions come from `Player.Listener.onPositionDiscontinuity`, which Media3 fires for the outgoing item before `onMediaItemTransition`; a same-item seek does not end the play attempt. On service shutdown the player's current position is recorded too.

Known limitation: per-playlist attribution is not wired yet, so `playlist_id` is `ALL_TRACKS_PLAYLIST_ID` only for the all-tracks root and `0` (unknown) elsewhere. The browse tree reports the current root, not the playlist a track was opened from.

## Open decisions I already made for you

Per your direction:
- new database for analytics: yes
- sessions later: yes
- thresholds: I picked defaults
  - quick skip: 15 seconds
  - repeat window: 10 minutes
  - "long time" / "not recently played": 90 days

If you want different defaults, I can change them before implementation.
