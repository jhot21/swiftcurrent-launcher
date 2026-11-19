# Translating

Translations live in `app/src/main/res/values-<code>/strings.xml`, alongside the
English originals in `app/src/main/res/values/strings.xml`. The app follows the device's
language; there is no in-app language picker.

## By pull request

Copy `values/strings.xml` to `values-<code>/strings.xml`, translate the text
between the tags, and open a pull request. Things to watch:

- Keep every `name="..."` exactly as it is. That is what the code looks the
  string up by; the text between the tags is the only part to change.
- Keep the format markers — `%1$d`, `%1$s`, `%d`. They are filled in at runtime with a
  number or a name. Numbered markers may move to wherever your sentence needs them, but keep the
  same numbers.
- `<plurals>` entries need one `<item>` for each plural form your language uses (`one`, `few`,
  `many`, `other`, ...; see the
  [Android list](https://developer.android.com/guide/topics/resources/string-resource#Plurals)),
  not just the English `one` and `other`.
- Escape an apostrophe as `\'` and write `&amp;` rather than a bare `&`, or the file is not valid
  and nothing will build. Escapes such as `\u00B7` are fine to keep.

A missing string falls back to English, and lint only warns about it, so a partial translation is
fine and a translation that has fallen behind a release is not a problem.

## Checking it

Switch the device or emulator to your language and run `./gradlew assembleDebug`, or run
`./gradlew test lint` to confirm the file is valid.
