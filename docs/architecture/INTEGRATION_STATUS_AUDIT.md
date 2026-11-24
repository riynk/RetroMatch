# RetroMatch Integration Status Audit

Audit date: 2026-11-24
Branch: `master`

## Scope and evidence

Reviewed the repository tree, README, integration architecture, library-model audit, lyrics audit, Android manifest, application dependency wiring, player/drive-mode implementation, lyrics provider and persistence, metadata tag writer, and the `integration/localplayer` UI/theme/lyrics sources.

This is a source-level audit. A successful CI build and a user-confirmed normal install/startup do not prove runtime behavior for every integration.

## Product integration model

RetroMatch uses a single Android application and a single playback owner. The four sources have distinct responsibilities:

| Source | Intended responsibility in RetroMatch |
| --- | --- |
| Retro Music Player | Application foundation: navigation, library structure, settings, playback service, persistence and existing Android integrations. |
| LocalPlayer | Primary reference for the player experience: Now Playing composition, visual hierarchy, typography treatment, motion/animation patterns and advanced TTML karaoke presentation. |
| TimeLyr | Lyrics acquisition and persistence: LRCLIB lookup, synced/plain LRC handling and complementary TTML source/fallback flow. |
| Metadator | Metadata domain: tag editing, metadata lookup, artwork and eventual batch/backup/undo workflows. |

Do not import a second playback service, library database or complete application shell from LocalPlayer. Adapt its UI and domain capabilities to the existing RetroMatch boundaries.

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
| LocalPlayer TTML model/parser/cache | LocalPlayer branch contains `TtmlLyrics`, `TtmlParser`, `TtmlCache` and TTML-aware `LyricsViewModel`; equivalent integration not evidenced in master | Not yet integrated into master |
| LocalPlayer word/syllable karaoke | `WordByWordLyrics.kt`, `LyricsLine.kt`, and `LyricsScreen.kt` implement animated syllable reveal and TTML-first display; equivalent UI not evidenced in master | Not yet integrated into master |
| LocalPlayer player motion and composition | `PlayerScreen.kt` uses Compose animation primitives, drag gestures, queue interactions and a lyrics surface; master player remains Retro Music-derived | Selective UI port required |
| LocalPlayer typography/font assets | LocalPlayer theme supplies Material typography and explicit text size/weight hierarchy; no standalone TTF/OTF font assets found in its branch tree | Port typographic system and hierarchy; do not claim a custom font file is available |
| RetroMatch visual identity | Master includes Manrope font resources and existing theme infrastructure; no completed unified LocalPlayer-led player design milestone | Design-system integration still required |
| General metadata/tag editing | Existing Jaudiotagger-based tag editor and `TagWriter` | Existing foundation capability; no separate Metadator-derived domain/repository or complete lookup/batch/undo integration identified |
| Canonical track boundary | Existing `Song` uses MediaStore ID and file path; no explicit content-URI property | Known architecture gap |
| Normal and F-Droid variants | CI assembles both debug flavors | Build coverage only; F-Droid runtime/policy review remains |

## Visual integration direction

- LocalPlayer is the visual reference for Now Playing, lyric typography, animated transitions, playback interactions and TTML karaoke.
- Retro Music remains the application shell reference for navigation, library organization, settings and established Android integrations.
- Reuse RetroMatch's existing theme and Manrope resources where appropriate; map LocalPlayer's typography hierarchy, spacing, color roles and motion into the existing View/XML architecture instead of introducing a parallel Compose app.
- First visual migration target: Now Playing and Lyrics. Library/settings should not be wholesale replaced unless a later screen-by-screen review demonstrates a clear benefit.
- Preserve accessibility: motion should respect reduced-animation/system animator settings where practical, and lyric highlighting must retain readable contrast.

## Fixes made during the earlier audit

1. `TagWriter.writeTagsToFilesR` previously created temporary files using only the source audio basename. Two files from different folders with the same basename could overwrite the same cache file. It now creates unique temporary files while preserving the audio extension for format detection.
2. Two lyrics-edit paths previously indexed the returned temporary-file list with `[0]`. If tag preparation returned an empty list, this could throw an index exception. They now use `firstOrNull()` and show the existing write-failure message instead.

## Remaining engineering priorities

1. Define a non-breaking canonical track/URI adapter shared by playback, lyrics and metadata operations.
2. Port LocalPlayer's TTML model/parser/cache into the existing `:app`; add parser and cache tests before wiring UI.
3. Adapt LocalPlayer's animated syllable/word karaoke presentation to the existing player/lyrics screen and playback-position callback. Keep LRC fallback fully functional.
4. Port LocalPlayer's Now Playing composition, typography hierarchy and motion patterns selectively. Reuse existing RetroMatch theme infrastructure and Manrope resources; no LocalPlayer custom font file was found.
5. Connect TimeLyr's LRCLIB acquisition with the richer TTML path using explicit source priority and format fallback; do not conflate LRC and TTML.
6. Separate metadata domain and repository logic from the legacy editor UI; define backup/undo and batch-edit semantics before expanding write capabilities.
7. Decide whether RetroMatch's product scope includes only Android Auto media browsing/drive controls or also a distinct map/navigation surface. Current source evidence supports the former, not turn-by-turn navigation.
8. Validate playback, media controls, LRCLIB network outcomes, TTML/LRC load and fallback, lyric seek/highlight timing, cache invalidation, metadata consent cancellation and duplicate-basename batch edits on device.

## Verification boundary

The previous CI run #54 on commit `1a7a1677be0c1943da1fc75fad699a203c5892b2` completed successfully, including lint and both Normal/F-Droid debug assemble tasks. This verifies that commit only. No claim of physical-device runtime validation is made by this audit.
