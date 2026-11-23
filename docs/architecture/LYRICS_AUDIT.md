# RetroMatch Lyrics Audit

Audit date: 2026-11-24
Branch: `master`

## Existing implementation

RetroMusic foundation already contains the lyrics feature:

- `lyrics/LrcUtils.java`: local LRC parsing, multiple timestamps per line, and bilingual file parsing.
- `model/lyrics/SynchronizedLyricsLRC.java`: synchronized LRC parser with offset metadata support.
- `model/lyrics/Lyrics.java` and `AbsSynchronizedLyrics.java`: lyrics model abstraction.
- `fragments/lyrics/LyricsFragment.kt` and player lyric fragments/views: lyrics UI and synchronization presentation.
- `db/LyricsEntity.kt` / `LyricsDao.kt`: legacy Room types; they are not registered in the active database and are not used by the LRCLIB flow.
- `network/LrcLibLyricsProvider.kt`: native Kotlin exact lookup, search fallback, candidate ranking, and lyric payload mapping.

## Findings and fixes

1. **Scoped storage:** downloaded/edited synced lyrics are written to app-specific external storage (or internal files storage fallback), not directly into a shared music directory with raw `FileWriter`.
2. **Track identity at consent callback:** MediaStore callbacks retain the originally requested song URI and temporary metadata file instead of reading the mutable current `song` property after playback changes.
3. **LRCLIB candidate quality:** search results without lyrics are removed before ranking so a metadata-perfect empty result cannot hide a usable candidate.
4. **Unicode matching:** metadata normalization preserves Unicode letters/digits and uses `Locale.ROOT`, allowing fallback matching for non-Latin artist/title metadata.
5. **Long-track LRC timestamps:** timestamp detection accepts minute fields longer than three digits and is shared by provider preview and UI preview classification.
6. **Regression tests:** `LyricsTextTest` covers non-Latin normalization, locale-independent case conversion, featured-artist extraction, long timestamps, malformed timestamps, and exact-vs-partial metadata scoring. Android CI runs `:app:testNormalDebugUnitTest`.

## Persistence and migration decision

Fetched synchronized lyrics use the existing LRC-file playback path. Plain lyrics are shown in the existing metadata editor before being written to the audio tag. The active Room database is version 24 and does not register `LyricsEntity`; enabling the unused legacy entity would require an unnecessary schema migration for this integration, so no Room schema change is included.

## Remaining validation

- Confirm the new unit-test task and both APK build variants pass in CI.
- Test actual LRCLIB exact lookup, search fallback, ambiguous-result rejection, no-result, and network-error behavior on a device/emulator.
- Test synced-LRC save/reopen and playback seeking, including Android 12+ scoped storage.
- Test plain-lyrics metadata save and MediaStore consent cancellation on Android 11+.
- Test duplicate tracks with the same title/artist from different albums before changing the managed LRC filename scheme.
- Verify the F-Droid variant at runtime; successful assembly alone does not prove dependency or store-policy compliance.
