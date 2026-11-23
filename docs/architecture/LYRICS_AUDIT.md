# RetroMatch Lyrics Audit

Audit date: 2026-11-24
Branch: `master`

## Existing implementation

RetroMusic foundation already contains the lyrics feature:

- `lyrics/LrcUtils.java`: local LRC parsing and bilingual file parsing.
- `model/lyrics/SynchronizedLyricsLRC.java`: synchronized LRC parser with offset metadata support.
- `fragments/lyrics/LyricsFragment.kt` and player lyric fragments/views: lyrics UI and synchronization presentation.
- `db/LyricsEntity.kt` / `LyricsDao.kt`: legacy Room types; they are not registered in the active database and are not used by the LRCLIB flow.
- `network/LrcLibLyricsProvider.kt`: native Kotlin exact lookup, search fallback, candidate ranking, and lyric payload mapping.

## Findings and fixes

1. **Scoped storage:** downloaded/edited synced lyrics are written to app-specific external storage (or internal files storage fallback), not directly into shared music directories with raw file writers.
2. **Track identity at consent callback:** MediaStore callbacks retain the originally requested song URI and temporary metadata file instead of reading the mutable current `song` property after playback changes.
3. **UI refresh after writes:** lyric views refresh after MediaStore consent and actual file/tag writes complete, rather than relying only on the earlier dialog button callback.
4. **LRCLIB candidate quality:** search results without lyrics are removed before ranking so a metadata-perfect empty result cannot hide a usable candidate.
5. **Unicode matching:** metadata normalization preserves Unicode letters/digits and uses `Locale.ROOT`, allowing fallback matching for non-Latin artist/title metadata.
6. **LRC timestamps:** shared timestamp detection accepts long minute fields, accepts timestamps without fractions, and rejects seconds outside 00–59. The `LrcView` parser was also updated for long minutes, optional one-to-three digit fractional seconds, multiple timestamps, and blank instrumental lines.
7. **Per-track private LRC identity:** app-managed LRC files now use a deterministic SHA-256 key derived from the audio file path. This avoids collisions between separate files/releases with identical title and artist metadata, and does not rely on transient MediaStore IDs. Legacy title/artist and colocated sidecar files remain readable as fallbacks.
8. **Regression tests:** `LyricsTextTest` covers Unicode normalization, locale-independent case conversion, featured-artist extraction, long timestamps, invalid timestamps, and scoring. `LyricsFileKeyTest` checks stable keys, path separation, and filesystem-safe names. Android CI runs `:app:testNormalDebugUnitTest`.

## Persistence and migration decision

Fetched synchronized lyrics use the existing LRC-file playback path. New private lyrics are stored per audio path. Plain lyrics are shown in the existing metadata editor before being written to the audio tag. The active Room database is version 24 and does not register `LyricsEntity`; enabling the unused legacy entity would require an unnecessary schema migration for this integration, so no Room schema change is included.

Older app-managed LRC files named by title and artist are still used as fallback so existing users do not lose access to them. New writes use the per-track key. The old metadata-based deletion API remains for compatibility; a new per-song deletion overload removes the per-track private file and that song's colocated sidecar without deleting another duplicate track's file.

## Remaining validation

- Confirm the latest Android CI run passes lint, `:app:testNormalDebugUnitTest`, and both APK variants.
- Test actual LRCLIB exact lookup, search fallback, ambiguous-result rejection, no-result, and network-error behavior on a device/emulator.
- Test synced-LRC save/reopen and playback seeking, including Android 12+ scoped storage.
- Test plain-lyrics metadata save and MediaStore consent cancellation on Android 11+.
- Test duplicate tracks with identical title/artist from different albums and ensure separate private LRC files are loaded.
- Verify the F-Droid variant at runtime; successful assembly alone does not prove dependency or store-policy compliance.
