# pvorg-android

The Android tablet app for PVORG: pair videos with image galleries, browse the
pairings in a viewer, and curate which galleries and tags belong to each video.

Behavior, data rules and design are specified in [docs/SPEC.md](docs/SPEC.md),
which is shared byte-for-byte with the macOS (`pvorg-native`) and web
(`pvorg-web`) apps. This README covers building, running and the developer
tooling only.

## Stack

Kotlin 2.0, Jetpack Compose with Material 3, Media3 (ExoPlayer), Coil 3,
kotlinx-serialization and the Storage Access Framework. Minimum SDK 29,
target SDK 35, landscape, tablet-first.

## Requirements

- JDK 17
- Android SDK with platform 35 and build-tools (Android Studio installs both;
  so does `sdkmanager "platforms;android-35" "build-tools;35.0.0"`)
- `local.properties` pointing at the SDK (`sdk.dir=/path/to/Android/sdk`);
  Android Studio writes it on first sync

The Gradle wrapper is checked in, so no separate Gradle install is needed.

## Build and test

```bash
./gradlew testDebugUnitTest   # JVM unit tests: data rules, compatibility sequence
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # minified with R8; unsigned
./gradlew installDebug        # onto a running emulator or connected device
```

## Run

On first launch, choose the library folder. It must contain `pvorg.json`,
`VID` (videos, with contact sheets in `VID/img`) and `GAL` (one folder per
gallery). Access is remembered; **Settings › Change Folder…** picks another.

Android doesn't generate previews. Make contact sheets with the macOS or web
app first. Videos without one still appear and can be played.

## Project layout

```
app/src/main/java/com/pvorg/android/
  domain/   Pure Kotlin model and rules: pvorg.json codec, mapping saves,
            Exclude Pairing, combinations, display names, search, sorting
  data/     LibraryStorage (SAF and java.io.File) and LibraryRepository
  state/    AppViewModel, UiState, mapping sessions, playback, preferences
  ui/       Compose theme, components, navigation shell and one file per page
app/src/debug/   QA hook and screenshot tour (debug builds only)
app/src/release/ No-op stand-in for the QA hook
app/src/test/    JVM unit tests, including the shared compatibility test
scripts/qa-tour.sh
```

The domain and data layers have no Android dependencies, so every Part I rule
in the spec is covered by plain JVM tests. `CompatibilityTest` replays the
shared sequence from spec §1.2 and compares the result with
`app/src/test/resources/compat/expected.json`.

## Screenshot and keyboard tour

Debug builds accept two launch extras. Release builds don't contain them.

- `--es pvorg.qa.library <device path>` opens a library from device storage
  without the folder picker.
- `--ez pvorg.qa.tour true` walks every page and state in Dark and then Light
  appearance. It also checks the hardware-keyboard shortcuts with real
  injected key events.

`scripts/qa-tour.sh` drives the tour on a running emulator or device:

```bash
scripts/qa-tour.sh [fixture-dir] [output-dir]
# defaults: /tmp/pvorg-android-fixture → /tmp/pvorg-android-shots
```

The script does the following:

1. Installs the debug build.
2. Copies a fresh fixture into the app's private files folder (via `run-as`).
3. Takes each screenshot the app asks for, with `adb exec-out screencap`.
4. Injects the requested keys.
5. Reports the keyboard checks and any crashes or ANRs from logcat.

A fixture is any library folder. `pvorg-native/Scripts/make_ui_fixture.sh`
generates a suitable one. Include a video without a contact sheet, an empty
gallery and a misnamed file to cover those states.
