# Contributing

## Building

You need JDK 17 and an Android SDK with platform 35. If you use [mise](https://mise.jdx.dev),
`mise.toml` pins JDK 17 and ktlint.

```sh
export ANDROID_HOME=/path/to/android-sdk   # or create local.properties with sdk.dir=...
./gradlew assembleDebug                    # app/build/outputs/apk/debug/
./gradlew test verifyRoborazziDebug        # unit tests and screenshot check
./gradlew lint                             # Android lint
```

With [Task](https://taskfile.dev), `task test` runs ktlint, the tests, the screenshot check and
lint in one go. That is the gate to pass before opening a pull request.

`./gradlew assembleRelease` produces an unsigned APK unless you create a
`keystore.properties` in the project root:

```properties
storeFile=release.keystore
storePassword=…
keyAlias=…
keyPassword=…
```

That file and any keystore are gitignored — never commit them. Published releases are
built from tags; see [releasing.md](releasing.md).

To use the launcher, install the APK and pick Swiftcurrent under
Settings → Apps → Default apps → Home app.

## Code style

ktlint enforces the style, configured in `.editorconfig` (`task ktlint` to check, `task format` to
fix what it can). Match the surrounding code rather than reformatting files you are only partly
touching.

Comments here explain *why*, not *what* — several parts of this codebase look
wrong until you know the constraint that shaped them (see `docs/ARCHITECTURE.md`).
If you change one of those, update the comment with it.

## Tests

- `app/src/test/` runs on the JVM with `./gradlew test`: plain logic tests, plus Robolectric and
  Compose tests for the UI.
- Screenshot tests compare against the PNGs in `app/src/test/snapshots`, which were recorded on
  macOS; Linux renders text slightly differently, so CI checks them on a macOS runner. After an
  intended design change, run `./gradlew :app:recordRoborazziDebug` and commit only the PNGs that
  changed on purpose (recording rewrites every file).
- `app/src/androidTest/` drives the launcher on a device or emulator with UiAutomator
  (`task test-device`, or `./gradlew connectedDebugAndroidTest`). These tests make the app under test
  the home app and are guarded to run on an emulator only, so don't point them at a phone.
- `benchmark/` holds macrobenchmarks (`task bench`); the numbers from an emulator show direction, not feel.

Pull requests run the unit tests, the screenshot check, lint and a debug build
(`.github/workflows/build.yml`).

## License

By contributing you agree your work is licensed under GPL-3.0-or-later, the project's
[LICENSE](../LICENSE). Source files carry no per-file header.
