# RetroMatch Music Video Architecture

## Product decision

Music Video is a per-track feature with both in-app and external playback. The user chooses the preferred behavior:

- `IN_APP`: use an in-app adapter only when the provider permits playback and exposes a supported playback surface.
- `EXTERNAL`: open the selected HTTPS URL or local content URI with an Android intent.
- `ASK_EVERY_TIME`: ask for the playback destination for each selection.

If an in-app adapter is unavailable, blocked, or cannot play the selected source, offer external playback. Never silently scrape a provider, bypass DRM, or treat an ordinary webpage URL as a playable media stream.

## Data ownership

Room is the canonical source of video associations. A track may have multiple entries: official music video, lyric video, live performance, visualizer, or other. Each entry stores a canonical `trackKey`, optional MediaStore song ID, URI, source type, video type, provider, official flag, language tag, lyric/video sync offset, and creation time.

The MediaStore ID and file path are not canonical identities. `TrackVideoIdentity` currently derives a SHA-256 metadata fingerprint from normalized title, artist, album, and duration. It is independent of MediaStore IDs and file paths, but it is not immutable: editing any of those metadata fields changes the key. Duplicate recordings with identical metadata may still share a key. Before shipping association management, the next identity phase must add a durable recording ID/alias strategy (prefer a trusted recording identifier when available, with explicit user-confirmed migration when metadata changes) so edits do not orphan video links.

Remote references are HTTPS URLs. Local references are persisted `content://` URIs; the UI must request and retain persistable URI permission where the document provider supports it. Raw filesystem paths are not accepted.

## Layers

1. **Persistence** — Room entity and DAO; migrations are additive and preserve existing playlist/history data.
2. **Repository** — owns association CRUD, URI validation, ordering, and track-key lookup.
3. **Provider adapters** — declare whether a source supports in-app playback, external intents, or neither. Provider policy and terms take precedence over a generic embedded web view.
4. **Playback coordinator** — routes to an allowed in-app adapter or external Android intent. It does not create another audio service or replace the existing audio playback service.
5. **Presentation** — Video tab on the track/Now Playing surface, video manager in track details, and a user preference for playback destination. Preserve the existing theme engine and animated icon language.
6. **Lyrics synchronization** — apply `syncOffsetMs` only when a provider exposes a reliable playback position. This is not part of the initial persistence milestone.

## Metadata tags

The database remains authoritative. A future tag adapter may import/export `RETROMATCH_VIDEO_URL` where the audio format and jaudiotagger mapping support a safe custom field. Tag import must be best-effort, preserve unrelated metadata, and never make a database association depend on a successful tag write. Multiple videos require a versioned encoding or sidecar; a single custom tag must not overwrite the full list.

## Delivery sequence

- Phase 1: Room schema, migration, track identity, URI validation, and DAO (this change).
- Phase 2: repository/use cases, preferences, provider capability contract, and unit tests.
- Phase 3: track details/Now Playing UI and external playback.
- Phase 4: permitted in-app adapters, fallback behavior, and device tests.
- Phase 5: optional tag import/export and lyric/video synchronization.

## Acceptance criteria

- Existing version-24 databases migrate without losing existing data.
- Multiple videos can be associated with one track; duplicate URI entries for the same track are rejected.
- Invalid schemes and local file paths are rejected.
- MediaStore ID changes do not change the metadata fingerprint when track metadata is unchanged.
- Metadata edits must not silently orphan video links; the durable identity/alias phase is required before user-facing release.
- Provider restrictions are enforced and external fallback is explicit.
- No second audio playback service is introduced.
