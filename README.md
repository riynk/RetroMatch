# RetroMatch

RetroMatch is an Android local-music player project that brings selected capabilities from several open-source music projects into one application.

The goal is a single APK with one music library, one playback service, and a consistent user experience—not a bundle of separate music-player apps.

## Current foundation

The `:app` module is based on **Retro Music Player v7.0.3**. Its existing Kotlin, XML layouts, Fragments, ViewBinding, Room database, Koin dependency injection, library screens, and playback service are retained as the initial foundation.

The application is being rebranded as RetroMatch. During the transition, the Android application ID is `app.retromatch.player`; the existing Kotlin namespace remains `code.name.monkey.retromusic` to avoid a risky package-wide rename before the architecture is ready.

## Planned source integrations

| Source | Intended contribution | Integration approach |
| --- | --- | --- |
| Retro Music Player v7.0.3 | Main application, local library, playback, playlists, widgets, existing player experience | Keep as the application foundation |
| Metadator v2.1.0 | Audio-tag editing, metadata lookup, cover editing, batch operations | Integrate selected metadata/domain logic and editor capabilities |
| LocalPlayer vV1.0.1 | Player and library UI ideas | Reuse selected UX patterns; do not add a second player engine |
| TimeLyr v1.1.0 | LRC parsing, synchronized lyrics, LRCLIB workflows | Port the useful logic into the native Kotlin application |

The imported source branches are kept separate so each upstream project can be audited and integrated selectively.

## Architecture principles

- One installable Android application.
- One canonical music-library model and repository boundary.
- One playback engine, foreground service, and media-session integration.
- Lyrics and metadata are separate domain capabilities that use the same track identity.
- Keep source-specific UI and duplicate playback/data layers out of the final app.
- Preserve upstream copyright notices and comply with the applicable licenses.

See [the integration architecture](docs/architecture/INTEGRATION_ARCHITECTURE.md) for the implementation sequence and boundaries.

## Build requirements

- JDK 21
- Android SDK with API 36
- Gradle Wrapper (use the checked-in `./gradlew`)

Build the application variants with:

```bash
./gradlew :app:assembleNormalDebug
./gradlew :app:assembleFdroidDebug
```

Run lint with:

```bash
./gradlew lint
```

Build verification must be recorded against the exact commit being tested. A successful source import is not the same as a successful APK build.

## License

The current application foundation is licensed under **GNU General Public License v3.0**. The project must retain the required upstream notices and source obligations for the GPL-derived application. Any MIT-licensed components integrated from other sources must retain their copyright and license notices.

See [LICENSE.md](LICENSE.md) and the upstream source repositories for the full license texts and attribution requirements.
