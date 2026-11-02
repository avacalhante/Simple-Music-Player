# Performance and Maintenance Plan

This is a working plan, not a promise of scope. Update the checkboxes as work lands.

Last updated: 2026-11-01

## Constraints (agreed with the owner)

- **UI/appearance is frozen.** No Material 3, dynamic color, or redesign. Only minor functional tweaks that don't change the look.
- **targetSdk stays as-is.** The app is not published (F-Droid build is used locally) and the target device runs Android 13 (SDK 33). Google Play's API 36 requirement does not apply.
- **`fdroid` is the release flavor.** Release builds are extremely heavy on this machine; iterate with `:app:compileFdroidDebugKotlin` and only run release builds when explicitly asked.
- Use JDK 17 (`JAVA_HOME=/home/v/.jdks/jbr-17.0.14` on this machine) or a JDK 21 that ships `javac`, not the machine's default JDK 27.

## Findings (evidence)

Measured from the code, not guessed:

1. **Missing database indices.** `tracks` only has `(media_store_id, playlist_id)` and `albums` only its primary key (`app/schemas/.../14.json`). Queries filter on `album_id`, `artist_id`, `genre_id`, `folder_name`, `playlist_id`, and `albums.artist_id` — all full scans. `tracks` also duplicates every song per playlist.
2. **O(n²)-ish media tree build.** `MediaItemProvider.reload()` issues one full-table query per album, artist, folder, playlist and genre, and is called when the playback service starts and after every scan.
3. **Whole queue rewritten on every playback event.** `PlayerListener.onEvents` → `updatePlaybackState()` → `saveRecentItemsWithStartPosition()` → `resetQueue()` deletes and re-inserts every queue row for play/pause, seek, track change, timeline change, etc.
4. **Per-comparison allocations.** `AlphanumericComparator()` is constructed inside `compare()` in all six model comparators.
5. **Eager, duplicated MediaItem creation.** `buildTracks()` converts each track twice (node + `titleMap`); the full tree is kept in memory although only external browsers use it.
6. **Scan writes and first-run hashing.** Every scan re-inserts all MediaStore tracks plus an "All Tracks" copy. The new `first_seen` feature reads up to 512 KiB per file on the first scan after upgrading (cached afterwards).
7. **Toolchain vintage.** AGP 8.1.1, Gradle 8.2.1, Kotlin 1.9.10, Media3 1.1.1, Room 2.5.2, target/compileSdk 34, `android.enableJetifier=true`. Current stable is roughly AGP 9.4, Gradle 9.x, Kotlin 2.4, Media3 1.10.
8. **Functional gaps.** The "Gapless playback" setting is wired to skip-silence, which is hardcoded off with a TODO. Android Auto is one commented-out manifest entry away but disabled. `androidx.media:media` is declared but unused.

## Phase 1 — Add the missing indices (highest value, lowest risk)

Schema bump to 15. This is the change most likely to be felt on a large library.

Status: done in `be93b2ac` on `feat/db-indices`; upgrade from an existing v14 database verified on the Android 13 device.

- [x] Add Room indices:
  - `Track`: `album_id`, `artist_id`, `genre_id`, `playlist_id`
  - `Album`: `artist_id`
  - Leave the existing unique `(media_store_id, playlist_id)` index in place.
- [x] `SongsDatabase`: version 15, add `MIGRATION_14_15` with matching `CREATE INDEX IF NOT EXISTS` statements.
- [x] Commit the generated `app/schemas/.../15.json`.
- [x] Verify upgrade from an existing 14 database (no Room schema-mismatch crash) and a fresh install.

Notes:

- `folder_name` is queried `COLLATE NOCASE`; a plain index would not be used. Either leave it out of this phase (folder counts are usually small) or handle collation explicitly in a follow-up.
- Inserts during scans get slightly slower; reads should win by a wide margin.

Acceptance: cold start and tab switching are subjectively faster on a large library; no schema errors in logcat.

## Phase 2 — Stop rewriting the queue on every playback event

The queue must survive process death, but not be rewritten for every button press.

Status: done on `feat/db-indices`, verified on the Android 13 device.

- [x] Add a `QueueItemsDao` helper that resets `is_current` and updates the current row's `last_position` in one statement. `updateCurrentTrack()` uses a `CASE` expression, so it is atomic without a transaction.
- [x] Split persistence in `PlayerListener` / `PlaybackService.updatePlaybackState()`:
  - `EVENT_TIMELINE_CHANGED` → save the full queue, but only after comparing the media ids with the last persisted queue. The player also reports timeline changes for internal reasons such as the shuffle order, and those must not cause a rewrite.
  - media item transition, seek, play/pause, buffering, playlist metadata → update only the current row and position (`saveCurrentTrackProgress()`).
- [x] Keep widget updates and `updatePlaybackInfo()` behaviour unchanged.
- [x] Seed the in-memory queue snapshot from the database, so the first event after a service restart does not rewrite an unchanged queue either.
- [x] Verify: play/pause, seek, next/previous, shuffle on/off, changing the queue by playing a different folder (5 tracks instead of 988), process kill and reopen (queue, current track and position restored). No crashes in logcat.

Note: `Player.EVENT_TRACKS_CHANGED` is **not** a queue event, it fires when the current media item's audio tracks change. Using it as a queue-change signal was the first attempt and still rewrote the queue on every track transition.

Acceptance: no `DELETE FROM queue_items` / bulk insert during normal playback; queue still restores correctly. Confirmed by checking the database after each step: non-current rows keep their `last_position` instead of being reset.

## Phase 3 — Make the media tree build cheap

Status: done on `feat/media-tree`, verified on the Android 13 device.

- [x] Load the library once and group in memory by `albumId` / `artistId` / `genreId` / `folderName` / `playListId`, instead of one query per album/artist/folder/genre/playlist. `AudioHelper.getLibraryTracks()` returns both the deduplicated `all` list and the raw `byPlaylist` memberships, so playlist contents are not lost to the per-playlist row copies.
- [x] Build each `MediaItem` once and reuse it for both the tree node and `titleMap`.
- [x] Rebuild lazily: `reload()` only invalidates the tree; `onGetLibraryRoot` starts the build, and `onGetChildren` / `onGetItem` / `onSubscribe` wait through `whenReady()`. `reloadContent()` does nothing when no browser is subscribed. A reload that arrives while a build is running schedules one rebuild after it.
- [x] Confirm nothing else depends on `MediaItemProvider` being ready immediately; `MediaSessionCallback` has `callWhenSourceReady` for this. `onAddMediaItems` is deliberately not gated: it resolves items on a background thread, from the tree if it exists or with chunked database queries otherwise. In-app playback therefore never waits for the browser tree.
- [x] Split the build and queue-persistence executors so a slow tree build cannot delay queue saves.

Notes:
- Folder children are grouped by the lowercased folder name to mirror the existing `COLLATE NOCASE` query.
- Playlist media items now carry their real track count instead of `0`.
- The tree keeps the previous node-registration order and numeric media-id namespace, so browsing behaviour is unchanged.

Acceptance: media browser still works; startup after a scan no longer performs hundreds/thousands of queries. Verified with a temporary debug broadcast harness: 988 tracks, 855 albums, 320 artists, 20 folders and 1 playlist were grouped correctly; the first build took ~2.0 s and a warm rebuild ~0.3 s. No build ran at service start or after a scan with no browser subscribed, a reload rebuilt only on the next request, and a reload during a build produced exactly one extra build and one ready callback. In-app playback from a cold start (no tree) still started correctly through the batched fallback, with no Room main-thread errors.

## Phase 4 — Cheap list/scan cleanups

Status: done on `feat/cheap-cleanups`, verified on the Android 13 device.

- [x] Hoist `AlphanumericComparator()` to a shared `private val` in each model comparator. The class is stateless (it only walks its arguments), so a single instance is safe across threads.
- [x] Replace the per-path hash-cache deletion loop in `SimpleMediaScanner.cleanupDatabase()` with bulk deletes. The stale paths are computed first and deleted in chunks of 900 with `DELETE ... WHERE path IN (...)`. A chunked `NOT IN` would be wrong: the second chunk would delete rows that were valid in the first one.
- [x] Remove the unused `androidx.media:media` dependency and its version entry.
- [ ] Optional: avoid re-inserting unchanged tracks on every scan. This is a bigger scanner change; only do it if the above phases don't give enough improvement. Left for later.

Acceptance: no behaviour change; sort results identical. Verified by comparing the tracks list order before and after, and by seeding a fake path into `track_hash_cache`: the next scan removed it while keeping all 988 real entries.

## Phase 5 — Dependency/toolchain refresh (separate branch, no UI changes)

Status: done on `feat/toolchain-refresh`, verified on the Android 13 device after every step.

Do these one at a time, each with a device smoke test, because they can affect playback and notifications:

- [x] Gradle `8.2.1` → `8.14.3`, AGP `8.1.1` → `8.13.2`, `compileSdk` `34` → `36`. targetSdk stays at 34 and the Java/Kotlin targets stay at 17.
- [x] Kotlin `1.9.10` → `2.2.21`, KSP → `2.2.21-2.0.5` (KSP2) and Room `2.5.2` → `2.8.5`. Kotlin 2.2 only pairs with KSP2 and Room only supports KSP2 from 2.7, so these move together. Room 2.8 rejects nullable element types in `@Delete` parameters, and `kotlinOptions` was replaced by the `compilerOptions` DSL.
- [x] Media3 `1.1.1` → `1.11.1`. `buildAudioSink` lost its offload flag and `setOffloadMode` (offload was already disabled), and `ForwardingPlayer` now exposes `getAudioSessionId`, so the local helper was removed. The shuffle-order workarounds in `SimpleMusicPlayer.kt` stay: upstream issue #325 is still open.
- [x] Enable Gradle configuration and build caches. A clean F-Droid debug build takes 34 s without them, 53 s while the cache is cold and 14 s once warm; a one-line Kotlin change rebuilds in 7 s. The "incompatible with Gradle 9" warning from KSP1 is gone.
- [x] Keep Jetifier. It is still enabled for the pinned 2023 Commons build and its third-party dependencies.

Notes from the Media3 jump:
- `MediaSession.Callback.onConnect` must not rebuild the result of `super.onConnect()` on 1.11: it returns empty command sets plus a marker telling the session to use the defaults, and rebuilding drops the marker. Without the fix, controllers could not play, pause or change the queue.
- `MediaController` is created asynchronously; commands are now queued until the future completes instead of being silently dropped when the callback ran before the controller was ready. `prepareInProgress` is reset when there is no queue, so a later `onResume` can retry.

Do **not** couple this phase with UI changes. The notification/lock-screen presentation comes from Media3, so check it explicitly. Verified after the update: library and tabs, cold-start playback, play/pause, next/previous, shuffle on/off, seek, queue and position restore across process death, notification with four actions, and the equalizer (audio session id).

## Phase 6 — Optional functional gaps (owner decision needed)

- [ ] Gapless playback: either implement it properly (ExoPlayer does gapless for supported formats by default; stop wiring the setting to skip-silence) or remove the setting. Current state is misleading.
- [ ] Android Auto: uncomment the `automotive_app_desc` meta-data in `AndroidManifest.xml` and verify browsing/playback. No in-app UI change.
- [ ] Simple-Commons migration: long-term only. The pinned 2023 commit is the main maintenance risk and blocks Material 3, but migrating is a project of its own.

## Explicitly out of scope

- Visual redesign, Material You/dynamic colors, new UI features.
- targetSdk upgrades and Play Store compliance.
- Changing distribution away from F-Droid.
- Rewriting playback on a new architecture; keep Media3.

## Follow-ups found during device testing

- Skipping a track used to wait out the full 300 ms seek coalescing window before the player started preparing the next item, so even a single skip felt delayed. The first tap now seeks immediately; only taps that arrive inside the window are coalesced into one final seek. Measured through the media session: item transition ~390 ms → ~90 ms, playback ~640–970 ms → ~370–500 ms, and 1/5/8 rapid skips still advance the queue by exactly 1/5/8.
- The album art flicker while skipping came from stale background loads: a cover requested for an earlier track could finish after the newer one and be applied on top of it. Artwork callbacks are now ignored unless they belong to the newest request (full player and mini player).
- A/B testing Media3 1.1.1 against 1.11.1 with identical measurement showed no playback-engine regression, which is why the fix is in the app's seek and artwork handling rather than in the dependency.
- The notification close button only paused and stopped the player, so Media3 kept showing an idle notification without a seekbar. Closing now also clears the media items, which is what makes Media3 remove the notification.
- Swiping the app away crashed with `Player callback method is called from a wrong thread`: Media3 1.11's default `MediaSessionService.onTaskRemoved` reads `MediaSession.getPlayer` on the main thread, while this app runs the player (and therefore the session) on `PlayerThread`. `onTaskRemoved` is now overridden and runs on the player thread; it keeps playback and the notification alive, and only stops the service when the close button left the player empty.

## Suggested order

1. Phase 1 (indices) — one migration, immediate read speedup.
2. Phase 2 (queue writes) — biggest during-playback win.
3. Phase 3 (media tree) — biggest startup-with-big-library win.
4. Phase 4 (small cleanups) — cheap.
5. Phase 5 (deps) — do when there is time to smoke-test.
6. Phase 6 — only if wanted.

## Verification without a test suite

There are no tests or CI. For each phase: compile `:app:compileFdroidDebugKotlin`, install a debug build on the SDK 33 device, and exercise the affected flow. For startup work, capture a few cold starts with `adb shell am start -W` before and after; for query work, confirm via `EXPLAIN QUERY PLAN` on a pulled debug database if needed. Do not run `:app:assembleFdroidRelease` for verification unless asked.
