# Swiftcurrent

A minimal, list-based Android home screen. Your favorites, the next thing on your
calendar, and nothing else; everything beyond that is a swipe away.

## Install

Prebuilt APKs are attached to [Releases](../../releases); there is no store listing.
Or build it from source (see [Build](#build)), then install the APK and pick
Swiftcurrent under **Settings → Apps → Default apps → Home app**. Being the
default home app is what lets Android show the launcher its app shortcuts.

Requires Android 14 (API 34) or newer.

## Getting started

The home screen holds a clock, the date, the next thing on your calendar, and a
short list of favorites — nothing you did not put there.

- **Swipe up** to search apps, app shortcuts and contacts.
- **Swipe up from the right edge** to open the A–Z app list, then slide along the
  letters to jump to one. The star at the top of the strip goes to favorites and
  the ring at the bottom opens Settings.
- **Swipe right on an item** for its pop-up: an app's own shortcuts, a folder's
  contents, or the actions you can take on it.
- **Long-press an item** to rename it, pick its shortcuts, add it to favorites or
  hide it.
- **Swipe down** to open the notification shade.
- **Double-tap the alphabet** to lock the screen. That needs the accessibility
  service, which Android may hide behind "allow restricted settings" in App info
  for anything installed outside a store.

Favorites can be apps, app shortcuts or folders, and the list has its own editor
for order and contents. Settings covers Home spacing, hidden apps, pop-up
folders, which shortcuts search shows, gesture and haptic options, and backup and
restore of your setup.

## Context favorites

A favorite can be shown only in some situations. Open its edit sheet (long-press
it on Home, or the pencil in the favorites editor) and add conditions under
**Show when…**:

- **Bluetooth**: a particular paired device, or any device, is connected or not
  connected (for example, show a podcast app while `My Earbuds` is connected).
- **Meld mode**: a [Meld](https://github.com/jhot21/meld) mode, such as `Work`,
  is active or inactive. Needs Meld 1.3.0 or newer.

All of a favorite's conditions must hold for it to appear. The rows below it move
up while it is hidden; Home never scrolls, so when everything does not fit, the
bottom gap shrinks first and then rows drop off the bottom.

Swiftcurrent asks for `BLUETOOTH_CONNECT` the first time you add a Bluetooth
condition, and for Meld's `me.jhot.meld.permission.READ_MODES` the first time you
add a Meld condition; nothing is requested before that. When the state cannot be
read (permission denied, Meld not installed or not responding), the last known
state is kept, and if there is none yet the favorite stays hidden.

Install Meld before Swiftcurrent asks for `READ_MODES`: Android only grants a
permission an installed app defines. If Swiftcurrent was installed first, relaunch
or reinstall it after installing Meld.

A condition on a device that is no longer paired, or on a Meld mode that no
longer exists, never matches; the editor outlines it in red.

## Privacy

No ads, no tracking, and no network permission at all. Contacts and calendar data
are read on the device, only when a permission is granted, and are never sent
anywhere. The backup file is the only way your setup leaves the app, and Android's
own backup deliberately skips it.

## Build

You need JDK 17 and an Android SDK with platform 35.

```sh
./gradlew assembleDebug
```

See [CONTRIBUTING.md](docs/CONTRIBUTING.md) for the full build and contribution
notes, [ARCHITECTURE.md](docs/ARCHITECTURE.md) for how the code fits together, and
[TRANSLATING.md](docs/TRANSLATING.md) to help with translations.

## Credits

Swiftcurrent started as a fork of
[Victoria Launcher](https://github.com/adelmonte/victoria-launcher) by adelmonte and
has since been rebuilt. Its design owes a lot to
[Niagara Launcher](https://niagaralauncher.app), which set the bar for a list-based
home screen. Parts of search were inspired by
[Sesame Shortcuts](https://play.google.com/store/apps/details?id=ninja.sesame.app.edge). None of these projects is affiliated with
this one.

## License

GPL-3.0-or-later — see [LICENSE](LICENSE).
