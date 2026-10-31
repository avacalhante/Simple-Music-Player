# Performance and Maintenance Plan

This is a working plan, not a promise of scope. Update the checkboxes as work lands.

Last updated: 2026-10-30

## Constraints (agreed with the owner)

- **UI/appearance is frozen.** No Material 3, dynamic color, or redesign. Only minor functional tweaks that don't change the look.
- **targetSdk stays as-is.** The app is not published (F-Droid build is used locally) and the target device runs Android 13 (SDK 33). Google Play's API 36 requirement does not apply.
- **`fdroid` is the release flavor.** Release builds are extremely heavy on this machine; iterate with `:app:compileFdroidDebugKotlin` and only run release builds when explicitly asked.
- Use JDK 17/21 (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk`), not the machine's default JDK 27.

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

- [ ] Add a `QueueItemsDao` transactional helper that resets `is_current` and updates the current row's `last_position` in one call.
- [ ] Split persistence in `PlayerListener` / `PlaybackService.updatePlaybackState()`:
  - queue membership/order changed (`EVENT_TIMELINE_CHANGED`, `EVENT_TRACKS_CHANGED`, shuffle order changes) → save the full queue;
  - current item changed, seek, play/pause, buffering → update only the current row and position.
- [ ] Keep widget updates and `updatePlaybackInfo()` behaviour unchanged.
- [ ] Verify: play/pause, seek, next/previous, shuffle on/off, add/remove/reorder queue items, kill and reopen the app (queue and position restored), Android Auto/media browser queue unchanged.

Acceptance: no `DELETE FROM queue_items` / bulk insert during normal playback; queue still restores correctly.

## Phase 3 — Make the media tree build cheap

- [ ] Load the library once and group in memory by `albumId` / `artistId` / `genreId` / `folderName` / `playListId`, instead of one query per album/artist/folder/genre/playlist. Deduplicate by `mediaStoreId` (rows are duplicated per playlist).
- [ ] Build each `MediaItem` once and reuse it for both the tree node and `titleMap`.
- [ ] Rebuild lazily: mark the provider dirty after a scan and build on the first `onGetLibraryRoot` / `onGetChildren` request instead of at service start and after every scan.
- [ ] Confirm nothing else depends on `MediaItemProvider` being ready immediately; `MediaSessionCallback` has `callWhenSourceReady` for this.

Acceptance: media browser still works; startup after a scan no longer performs hundreds/thousands of queries.

## Phase 4 — Cheap list/scan cleanups

- [ ] Hoist `AlphanumericComparator()` to a shared value in each model comparator (verify it is stateless/thread-safe).
- [ ] Replace the per-path hash-cache deletion loop in `SimpleMediaScanner.cleanupDatabase()` with a bulk delete (chunk the `NOT IN` list to stay under SQLite's variable limit).
- [ ] Remove the unused `androidx.media:media` dependency.
- [ ] Optional: avoid re-inserting unchanged tracks on every scan. This is a bigger scanner change; only do it if the above phases don't give enough improvement.

Acceptance: no behaviour change; sort results identical.

## Phase 5 — Dependency/toolchain refresh (separate branch, no UI changes)

Do these one at a time, each with a device smoke test, because they can affect playback and notifications:

- [ ] Media3 `1.1.1` → current `1.10.x`. This brings years of session and notification fixes plus player/format improvements. The shuffle-order workarounds in `SimpleMusicPlayer.kt` must stay: upstream issue #325 (shuffle order in the Player interface) is still open as of 2026.
- [ ] Kotlin/KSP `1.9.10` → 2.x (keep KSP version paired).
- [ ] AGP/Gradle upgrade (JDK 21 is available). Expect manifest/DSL cleanups.
- [ ] Enable Gradle configuration cache / build cache if the plugins cooperate; measure F-Droid build time before and after.
- [ ] Keep Jetifier until Simple-Commons is replaced or updated; it exists for third-party dependencies such as the pinned 2023 Commons build, so removing it blindly will break the build.

Do **not** couple this phase with UI changes. The notification/lock-screen presentation comes from Media3, so check it explicitly.

## Phase 6 — Optional functional gaps (owner decision needed)

- [ ] Gapless playback: either implement it properly (ExoPlayer does gapless for supported formats by default; stop wiring the setting to skip-silence) or remove the setting. Current state is misleading.
- [ ] Android Auto: uncomment the `automotive_app_desc` meta-data in `AndroidManifest.xml` and verify browsing/playback. No in-app UI change.
- [ ] Simple-Commons migration: long-term only. The pinned 2023 commit is the main maintenance risk and blocks Material 3, but migrating is a project of its own.

## Explicitly out of scope

- Visual redesign, Material You/dynamic colors, new UI features.
- targetSdk upgrades and Play Store compliance.
- Changing distribution away from F-Droid.
- Rewriting playback on a new architecture; keep Media3.

## Suggested order

1. Phase 1 (indices) — one migration, immediate read speedup.
2. Phase 2 (queue writes) — biggest during-playback win.
3. Phase 3 (media tree) — biggest startup-with-big-library win.
4. Phase 4 (small cleanups) — cheap.
5. Phase 5 (deps) — do when there is time to smoke-test.
6. Phase 6 — only if wanted.

## Verification without a test suite

There are no tests or CI. For each phase: compile `:app:compileFdroidDebugKotlin`, install a debug build on the SDK 33 device, and exercise the affected flow. For startup work, capture a few cold starts with `adb shell am start -W` before and after; for query work, confirm via `EXPLAIN QUERY PLAN` on a pulled debug database if needed. Do not run `:app:assembleFdroidRelease` for verification unless asked.
