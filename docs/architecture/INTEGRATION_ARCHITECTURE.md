# RetroMatch Integration Architecture

## 1. Product target

Deliver one native Android application that combines local music playback, metadata tools, and synchronized lyrics. The user should experience one application—not four separate applications embedded together.

The initial foundation is the Retro Music Player v7.0.3 Android project. Keep its working application shell and playback behavior while adding capabilities in small, verifiable increments.

## 2. Architecture decisions

### One application and one playback owner

- Keep a single Android application module during the first integration milestones.
- Keep one authoritative playback service and one player instance.
- Keep one media-session and notification path for lock-screen, Bluetooth, headset, and Android Auto controls.
- Do not import LocalPlayer or Metadator as a second runnable application.
- Do not create a parallel Flutter runtime for TimeLyr.

### Preserve the existing codebase before modularizing

The current application uses Kotlin, XML layouts, Fragments, ViewBinding, Room, Koin, and an existing media playback service. Continue these established patterns for initial integration work.

Do not migrate the whole UI to Compose as a prerequisite. Compose can be evaluated for a self-contained feature only when it materially improves that feature and can coexist cleanly with the current navigation and theme system.

The Kotlin namespace remains `code.name.monkey.retromusic` during the transition. The distributable application ID is now `app.retromatch.player`. A package rename should be a separately planned migration after app-level dependencies and generated references are mapped.

## 3. Source responsibilities

| Source | Adopt | Avoid |
| --- | --- | --- |
| Retro Music Player 7.0.3 | Application shell, local library, playback service, queue, playlists, themes, widgets, existing database and navigation | Treating upstream branding as final product identity |
| Metadator 2.1.0 | TagLib-backed tag read/write flow, metadata lookup concepts, artwork editing, batch-edit domain logic, safe backup/undo patterns | Importing a second app shell, DI graph, library, and playback feature |
| LocalPlayer vV1.0.1 | Selected player-screen and library interaction patterns after UX review | Bringing in another player service or a second complete catalog |
| TimeLyr 1.1.0 | LRC parser behavior, timestamp handling, LRCLIB client concepts, lyric matching and caching | Embedding the Flutter app or duplicating lyric state in multiple repositories |

## 4. Target dependency direction

The first stage can stay within the existing `:app` module. Extract modules only after code boundaries are stable.

```text
UI / Navigation
    |
    +-- Library feature --------> Music repository ----> MediaStore / Room
    |
    +-- Player feature ---------> Playback controller --> Single playback service
    |
    +-- Lyrics feature ---------> Lyrics repository ----> LRCLIB / local LRC cache
    |
    +-- Metadata editor --------> Metadata repository --> TagLib / lookup providers
    |
    +-- Settings / Theme
```

Longer-term module candidates:

```text
:app
:core:model
:core:common
:core:database
:core:design
:feature:library
:feature:player
:feature:lyrics
:feature:metadata
:feature:playlists
:feature:settings
:data:music
:data:lyrics
:data:metadata
```

Do not create empty Gradle modules just to match this diagram. Extract a module only when it has a real implementation and a stable API.

## 5. Canonical track boundary

The app needs a single track identity shared by library, playback, lyrics, and metadata features. Introduce an adapter around the existing track/song model first; do not replace all existing call sites in one change.

The canonical representation should cover:

- Stable MediaStore or app-library ID and content URI
- Title, artist, album, and album artist
- Duration, track number, disc number, year, and genre
- Artwork reference and audio format
- Favorite state and optional lyrics reference

Repositories should own mapping from MediaStore, Room, and imported metadata models. UI components should not independently query MediaStore or parse tags.

## 6. Integration sequence

1. **Foundation:** application identity, build configuration, source attribution, and architecture boundaries.
2. **Library boundary:** document the existing Song model and MediaStore/Room mapping; establish one repository contract without changing the UI yet.
3. **Playback boundary:** map existing playback APIs and MediaSession lifecycle; preserve one service and test background playback before any engine changes.
4. **Lyrics:** port and test LRC parsing first, then add LRCLIB lookup, matching, caching, and synchronized display.
5. **Metadata editor:** integrate safe tag reading/writing, artwork updates, metadata lookup, and batch editing with backup/undo.
6. **UX unification:** apply selected LocalPlayer interaction patterns to existing RetroMatch screens without adding a competing navigation or player stack.
7. **Hardening:** test normal and F-Droid flavors, Android 12+ permissions, background playback, media controls, large libraries, lyrics synchronization, and metadata writes.

Each milestone should be a separate, reviewable commit or pull request. Preserve the imported source branches as immutable reference points.

## 7. Build and verification

Use JDK 21 and the repository's Gradle Wrapper. At minimum, verify:

```bash
./gradlew lint
./gradlew :app:assembleNormalDebug
./gradlew :app:assembleFdroidDebug
```

A commit is not build-verified until these commands have been run successfully against that commit. Also perform device/emulator checks for playback, notification controls, media permission flows, and file/tag writes before calling an integration complete.

## 8. Licensing and attribution

The base application is GPL-3.0. Preserve its license and copyright notices and comply with GPL-3.0 distribution/source requirements for derivative builds.

Metadator is GPL-3.0; its integrated portions remain subject to the applicable GPL requirements. LocalPlayer and TimeLyr are MIT-licensed; preserve their copyright and license notices for any code adopted from them.

Keep a third-party attribution inventory as code is actually imported. Do not copy upstream source files without their associated notices, and do not assume that a repository-wide license file overrides notices attached to individual files.
