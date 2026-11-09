# pvorg-android

Native Android tablet implementation of PVORG using Kotlin, Jetpack Compose, Media3, Coil, and the Storage Access Framework.

## Project status

The app source is implemented, but this machine did not have the Android SDK, command-line tools, or a Gradle wrapper available during implementation. One local setup pass is still required before the first build.

## One-time setup on macOS

1. Install Android Studio on macOS.
2. Open Android Studio once, then install:
   - Android SDK Platform 35
   - Android SDK Build-Tools
   - Android SDK Command-line Tools
3. Install Gradle with Homebrew:

```bash
brew install gradle
```

4. Generate the Gradle wrapper:

```bash
cd pvorg-android
gradle wrapper --gradle-version 8.10.2
```

5. Open the project in Android Studio and let it sync.
6. Build a debug APK:

```bash
cd pvorg-android
./gradlew assembleDebug
```

## Run

- Launch on an Android tablet or emulator.
- On startup, pick the root folder containing:
  - `pvorg.json`
  - `VID`
  - `VID/img`
  - `GAL`

## Notes

- Android ignores `videosPath` and `galleriesPath` in `pvorg.json`.
- Only videos with a matching `VID/img/<filename>.jpg` are included.
- Mapping changes are written back into the selected folder’s `pvorg.json`.
- The app is locked to landscape because this version is tablet-first.
