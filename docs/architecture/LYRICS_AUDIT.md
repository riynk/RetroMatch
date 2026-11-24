# RetroMatch Lyrics Audit

Audit date: 2026-11-24
Branch: `feature/localplayer-ttml-core`

## Existing implementation

RetroMusic foundation already contains the lyrics feature:

- `lyrics/LrcUtils.java`: local LRC parsing and bilingual file parsing.
- `model/lyrics/SynchronizedLyricsLRC.java`: synchronized LRC parser with offset metadata support.
- `fragments/lyrics/LyricsFragment.kt` and player lyric fragments/views: lyrics UI and synchronization presentation.
- `db/LyricsEntity.kt` / `LyricsDao.kt`: legacy Room types; they are not registered in the active database and are not used by the LRCLIB flow.
- `network/LrcLibLyricsProvider.kt`: native Kotlin LRCLIB exact lookup, search fallback, candidate ranking, and LRC payload mapping.

## Integration architecture

- **RetroMusic** remains the app shell, library, playback service, and current LRC presentation path.
- **LocalPlayer** supplies the TTML model/parser foundation and is the visual reference for the future word-level karaoke surface.
- **TimeLyr/LRCLIB** currently provides plain and synchronized LRC. Its current API payload does not establish a TTML source; a separate verified TTML acquisition adapter is still needed.
- **Metadator** remains a separate metadata/tag domain and is not coupled to lyric persistence.

TTML and LRC are deliberately distinct formats. LRC remains the line-timed fallback; TTML retains word/syllable timing and must not be flattened into LRC during storage.

## Findings and fixes

1. **Scoped storage:** downloaded/edited synced lyrics are written to app-specific external storage (or internal files storage fallback), not directly into shared music directories with raw file writers.
2. **Track identity at consent callback:** MediaStore callbacks retain the originally requested song URI and temporary metadata file instead of reading the mutable current `song` property after playback changes.
3. **UI refresh after writes:** lyric views refresh after MediaStore consent and actual file/tag writes complete, rather than relying only on the earlier dialog button callback.
4. **LRCLIB candidate quality:** search results without lyrics are removed before ranking so a metadata-perfect empty result cannot hide a usable candidate.
5. **Unicode matching:** metadata normalization preserves Unicode letters/digits and uses `Locale.ROOT`, allowing fallback matching for non-Latin artist/title metadata.
6. **LRC timestamps:** shared timestamp detection accepts long minute fields, accepts timestamps without fractions, and rejects seconds outside 00–59. The `LrcView` parser was also updated for long minutes, optional one-to-three digit fractional seconds, multiple timestamps, and blank instrumental lines.
7. **Per-track private LRC identity:** app-managed LRC files use a deterministic SHA-256 key derived from the audio file path. Legacy title/artist and colocated sidecar files remain readable as fallbacks.
8. **TTML parser core:** LocalPlayer's TTML model/parser has been adapted to the RetroMatch namespace. The parser uses KXML for JVM test compatibility, removes the source hash-only cache, and handles millisecond duration suffixes.
9. **Per-track TTML sidecars:** `TtmlLyricsStore` stores validated raw TTML in the existing app-private lyrics directory with a per-audio-path `.ttml` key. It reads back structured TTML, rejects empty/unparseable payloads, and deletes only the requested track's file. It does not create a second database or mutate the LRC store.
10. **Regression tests:** existing LRC utility tests plus TTML parser and sidecar-store tests cover timestamps, duration parsing, per-track isolation, invalid payload rejection, and track-specific deletion.

## Persistence and migration decision

The active Room database is version 24 and does not register the legacy `LyricsEntity`; enabling it would require an unnecessary schema migration. LRC continues to use the existing file path. TTML is stored separately as a private sidecar because word-level timing is not representable in the current LRC playback model. Plain lyrics continue through the existing metadata editor.

## Remaining work and validation

- Connect a verified TTML acquisition source to the lyrics flow. LRCLIB's current plain/synced LRC fields are not a TTML source.
- Add TTML-aware UI selection and playback synchronization without replacing the existing LRC fallback.
- Port LocalPlayer's karaoke typography/motion into the existing Android ViewBinding UI; do not add a second Compose app shell.
- Run and inspect CI for lint, `:app:testNormalDebugUnitTest`, `:app:assembleNormalDebug`, and `:app:assembleFdroidDebug`.
- Validate actual fetch, save/reopen, seeking, track changes, and Android 12+ storage behavior on a device/emulator.
- Verify the F-Droid variant at runtime; successful assembly alone does not prove dependency or store-policy compliance.
