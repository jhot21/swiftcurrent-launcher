# Contributing

## Building

You need JDK 17 and an Android SDK with platform 35.

```sh
export ANDROID_HOME=/path/to/android-sdk   # or create local.properties with sdk.dir=...
./gradlew assembleDebug                    # app/build/outputs/apk/debug/
./gradlew test                             # unit tests
./gradlew lint                             # Android lint
```

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

`kotlin.code.style=official`. Match the surrounding code rather than reformatting
files you are only partly touching.

Comments here explain *why*, not *what* — several parts of this codebase look
wrong until you know the constraint that shaped them (see `docs/ARCHITECTURE.md`).
If you change one of those, update the comment with it.

## Translations

All user-facing text lives in `app/src/main/res/values/strings.xml`. To add a
language, copy that file to `values-<lang>/strings.xml` and translate the values,
leaving the `name` attributes alone. Nothing else needs to change.

## Tests

`app/src/test/` covers the framework-free logic and runs with `./gradlew test`.
`app/src/androidTest/` drives the launcher on a device or emulator with UiAutomator
(`./gradlew connectedDebugAndroidTest`); these tests make the app under test the home app, so
run them on an emulator. Anything touching `PackageManager`, `ComponentName` or Compose that
neither covers is untested — a PR adding Robolectric is welcome.

## License

By contributing you agree your work is licensed under GPL-3.0-or-later. New
source files should carry the SPDX header the existing ones do.
