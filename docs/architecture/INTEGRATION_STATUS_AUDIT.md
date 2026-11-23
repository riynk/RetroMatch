# RetroMatch Integration Status Audit

Audit date: 2026-11-24
Branch: `master`

## Scope and evidence

Reviewed the repository tree, README, integration architecture, library-model audit, lyrics audit, Android manifest, application dependency wiring, player/drive-mode implementation, lyrics provider and persistence, and metadata tag writer.

This is a source-level audit. A successful CI build and a user-confirmed normal install/startup do not prove runtime behavior for every integration.

## Status by capability

| Capability | Repository evidence | Status |
| --- | --- | --- |
| Single Android application and playback owner | One `:app` module, `MusicService`, Room/Koin wiring | Implemented foundation |
| Local library, queue, playlists and standard player UI | Retained Retro Music model, repositories, fragments and service | Implemented foundation |
| Android Auto media browsing and controls | Automotive manifest metadata, MediaBrowser service and `AutoMusicProvider` | Existing media integration; device/head-unit validation remains |
| Drive mode | `DriveModeActivity` exposes playback controls, seek, shuffle, repeat and favorite | Existing basic drive controls; it is not turn-by-turn map navigation |
| LRCLIB lookup | Native Kotlin provider, exact lookup plus search/ranking fallback, DI binding and preview UI | Implemented in source; live network/device cases remain to validate |
| Synced LRC persistence | Per-audio-path private file key, app-specific storage, legacy/sidecar fallback and seek-synced display | Implemented in source; device persistence/seek cases remain to validate |
| Plain lyrics | Existing tag editor flow writes the lyrics field with Android R+ MediaStore consent path | Implemented in source; device permission/cancel/write cases remain |
| General metadata/tag editing | Existing Jaudiotagger-based tag editor and `TagWriter` | Existing foundation capability; no separate Metadator-derived domain/repository or complete lookup/batch/undo integration identified |
| LocalPlayer UX contribution | No dedicated imported feature package/module or documented completed UX port found in the current tree | Planned / not evidenced as integrated |
| Canonical track boundary | Existing `Song` uses MediaStore ID and file path; no explicit content-URI property | Known architecture gap |
| RetroMatch-specific visual identity | Current shell and screen hierarchy remain Retro Music-derived | Not yet delivered as a distinct UX milestone |
| Normal and F-Droid variants | CI assembles both debug flavors | Build coverage only; F-Droid runtime/policy review remains |

## Fixes made during this audit

1. `TagWriter.writeTagsToFilesR` previously created temporary files using only the source audio basename. Two files from different folders with the same basename could overwrite the same cache file. It now creates unique temporary files while preserving the audio extension for format detection.
2. Two lyrics-edit paths previously indexed the returned temporary-file list with `[0]`. If tag preparation returned an empty list, this could throw an index exception. They now use `firstOrNull()` and show the existing write-failure message instead.

## Remaining engineering priorities

1. Add a non-breaking canonical track/URI adapter shared by library, lyrics, and metadata operations.
2. Separate metadata domain and repository logic from the legacy editor UI; define backup/undo and batch-edit semantics before expanding write capabilities.
3. Review and selectively port useful LocalPlayer interaction patterns into the existing navigation and player screens; do not introduce another player service or library.
4. Decide whether RetroMatch's product scope includes only Android Auto media browsing/drive controls or also a distinct map/navigation surface. Current source evidence supports the former, not turn-by-turn navigation.
5. Add a testable boundary for the empty tag-preparation UI path; unique metadata temp-file naming is now covered by JVM unit tests.
6. Run CI on the audit-fix commit, then perform device validation for playback, media controls, LRCLIB network outcomes, LRC save/reopen/seek, metadata consent cancellation, and duplicate-basename batch edits.

## Verification boundary

The source changes above are committed, but they are not considered verified until the CI workflow completes on the resulting commit. No claim of physical-device runtime validation is made by this audit.
