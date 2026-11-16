# Architecture

Swiftcurrent is a single-module Android home-screen launcher: Kotlin, Jetpack Compose,
`minSdk 34`, `compileSdk`/`targetSdk 35`, package `me.jhot.swiftcurrent`. No network
permission, no analytics, no widgets.

## Principles

- Single module (`:app`). Versions live in `gradle/libs.versions.toml`.
- Pure logic sits behind small interfaces (stores, search sources, clocks), so it is
  tested on the JVM without a device. Android types stay at the edges.
- Design system first: `ui/design/` owns tokens, type and page scaffolds, and screens
  compose those instead of styling ad hoc.
- Tests mirror main. Robolectric and Roborazzi cover UI on the JVM; `androidTest` is for
  system behavior and runs on the emulator only.

## Package layout (`app/src/main/java/me/jhot/swiftcurrent/`)

```
core/        items, favorites state, backup, LauncherStore
catalog/     installed-app catalog, sections, usage
search/      SearchSource, engine, ranking, sources/ (apps, shortcuts, contacts)
shortcuts/   shortcut index, user shortcuts, creator discovery, pin requests
agenda/      calendar repository and formatting
lock/        lock service and controller, screen-reset receiver
ui/design/   tokens, type, settings/editor pages, sheet scaffold
ui/home/     home screen, favorites capacity, gestures, popups, action sheet
ui/applist/  app list and scrubber
ui/search/   search screen
ui/editor/   item editor and its state
ui/agenda/   agenda sheet
ui/settings/ settings pages and setup
HomeActivity.kt, SwiftcurrentApp.kt
```
