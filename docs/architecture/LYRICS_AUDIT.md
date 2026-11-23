# RetroMatch Lyrics Audit

Audit date: 2026-11-23
Branch: `master`

## Existing implementation

Retro Music foundation already contains a lyrics feature:

- `lyrics/LrcUtils.java`: local LRC parsing, multiple timestamps per line, and bilingual file parsing.
- `model/lyrics/SynchronizedLyricsLRC.java`: synchronized LRC parser with offset metadata support.
- `model/lyrics/Lyrics.java` and `AbsSynchronizedLyrics.java`: lyrics model abstraction.
- `fragments/lyrics/LyricsFragment.kt` and player lyric fragments/views: existing UI and synchronization presentation.
- `db/LyricsEntity.kt` / `LyricsDao.kt`: existing Room lyrics persistence.
- `network/LyricsService.kt`: existing Retrofit interface for a legacy `/lyrics` endpoint.

## Integration conclusion

Do not import TimeLyr's Flutter application, screen, or separate lyrics state. RetroMatch already has a native synchronized-lyrics parser, UI, and persistence.

The meaningful TimeLyr contribution is its LRCLIB integration approach:

1. Exact lookup using artist, title, album, and duration.
2. Search fallback when exact lookup returns no result.
3. Candidate matching based on normalized title/artist/album and duration.
4. Return synchronized lyrics preferentially, with plain lyrics as fallback.
5. Distinguish no-result responses from temporary network failures.

## Recommended next work

Implement an LRCLIB provider in Kotlin that adapts its response to the existing RetroMatch lyrics model and repository/UI flow. Add focused tests for matching and LRC edge cases before connecting UI. Do not change Room schema or playback service in this milestone.

## Porting notes

- TimeLyr source is Flutter/Dart; port behavior, not the runtime or UI.
- Preserve TimeLyr MIT attribution/license if code is directly adapted.
- Avoid overly permissive fuzzy matching: reject ambiguous candidates and require a confidence threshold.
- Do not automatically overwrite user-saved lyrics; fetched lyrics should be previewed or follow existing app behavior.
