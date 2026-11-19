# Architecture

Swiftcurrent is a single-module Android home-screen launcher: Kotlin, Jetpack Compose,
`minSdk 34`, `compileSdk`/`targetSdk 35`, package `me.jhot.swiftcurrent`. No network
permission, no analytics, no widgets.

## Principles

- One app module (`:app`), plus `:benchmark` for macrobenchmarks. Versions live in
  `gradle/libs.versions.toml`.
- Pure logic sits behind small interfaces (stores, search sources, clocks), so it is
  tested on the JVM without a device. Android types stay at the edges.
- Design system first: `ui/design/` owns tokens, type and page scaffolds, and screens
  compose those instead of styling ad hoc.
- Tests mirror main. Robolectric and Roborazzi cover UI on the JVM, with screenshot baselines in
  `app/src/test/snapshots`; `androidTest` is for system behavior and runs on the emulator only.

## Package layout (`app/src/main/java/me/jhot/swiftcurrent/`)

```
core/        items, favorites state, backup, LauncherStore; db/ is the Room usage database
catalog/     installed-app catalog, sections, usage
context/     device context for conditional favorites: Bluetooth devices, Meld modes, ContextMonitor
search/      SearchSource, engine, ranking, learning; sources/ (apps, shortcuts, contacts, events)
shortcuts/   shortcut index, user shortcuts, creator discovery, pin requests
agenda/      calendar repository, event occurrences and formatting
lock/        lock service and controller, double-tap detection, screen-reset receiver
shell/       LauncherShell (the root composable), ShellState, the dependencies it is given
             (LauncherDependencies, DefaultDependencies) and the hosts for settings, setup and shortcuts
ui/design/   tokens, type, settings/editor pages, sheet scaffold and gestures
ui/home/     home screen, favorites capacity, gestures, popups, action sheet
ui/applist/  app list and scrubber
ui/search/   search screen
ui/editor/   item editor and its state
ui/agenda/   agenda sheet
ui/edge/     edge strip
ui/settings/ settings pages and setup
HomeActivity.kt, SwiftcurrentApp.kt
```

`shell/` takes everything it touches (package manager, calendar, clock, intents) through
`LauncherDependencies`, so the whole shell runs on the JVM against fakes in tests.
