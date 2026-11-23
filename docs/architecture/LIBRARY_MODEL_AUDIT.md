# RetroMatch Library Model Audit

Audit date: 2026-11-23
Branch: `master`

## Existing model

The foundation already has a shared library model and repository facade:

- `model/Song.kt` is the parcelable library/playback model.
- Track identity currently uses `Song.id` (MediaStore ID), with `Song.data` holding the file path.
- Metadata fields include title, track number, year, duration, modified date, album ID/name, artist ID/name, composer, and album artist.
- `Repository` / `RealRepository` expose library queries and search; `SongRepository` is the source for MediaStore song queries.
- `SongEntity` is a Room playlist/history/favorite-related entity, not a replacement for the library model. It duplicates song metadata and has a separate generated `songPrimaryKey` plus playlist creator ID.
- `RetroDatabase` is at schema version 24. Any future schema change requires an explicit migration and must not be bundled casually with feature integration.

## Integration decisions

1. Keep `Song` as the compatibility model for existing playback, queue, fragments, and adapters during the first integration phase.
2. Do not use Room's `SongEntity.songPrimaryKey` as global track identity. Use the existing MediaStore ID and content URI/path boundary.
3. Lyrics and metadata features must consume the same track identity. They must not maintain a second library/catalog.
4. Before writing tags, resolve the track to a writable content URI and handle Android scoped-storage authorization. A raw path is not sufficient as a write contract on modern Android.
5. Keep metadata lookup results separate from local metadata until the user explicitly previews/applies them.
6. Avoid a database migration for the initial lyrics integration unless persistent lyrics cache requirements are proven. Prefer existing lyric persistence/caching mechanisms first.

## Known gaps to address incrementally

- The current `Song` model has no explicit content URI field; `data` is a path. A URI adapter is needed for scoped-storage-safe edits.
- No canonical metadata-editor or lyrics-specific domain boundary exists yet.
- Album artwork, disc number, genre, and lyric-source identity are not represented directly in this model.
- `SongEntity` duplicates metadata and must not become a new canonical track type.

## Next implementation milestone

Create a small, non-breaking track adapter/identity boundary, then integrate the LRC parser and unit tests without changing the playback service, Room schema, or navigation. Metadata writing should follow only after writable-URI handling and backup/undo behavior are designed.
