# Releasing

Pushing a tag builds, signs and publishes the APKs through `.github/workflows/release.yml`. The
tag is the only input, so there is no version-bump commit. Three forms are accepted and nothing
else:

| Tag | Kind | Source commit |
| --- | --- | --- |
| `vX.Y.Z` | final release | must be on `main` |
| `vX.Y.Z-rc.N` | pre-release, N 1 to 48 | any branch |
| `vX.Y.Z-beta.N` | pre-release, N 1 to 39 | any branch |

The version name is the tag without the `v`. The version code is
`((X*100 + Y)*100 + Z)*100 + stage`, with stage 99 for a final, `50 + N` for `rc.N` and `10 + N`
for `beta.N`, so a beta ranks below an rc, which ranks below the final. The rules live in
`scripts/release-version.sh` and are tested by `task test:scripts`.

Run `task test` first. Then push the tag by itself:

```bash
git tag vX.Y.Z && git push origin vX.Y.Z
```

Push the one tag, never `git push --tags`. A local build outside the workflow keeps the default
version (`0.1.0`, code 1) from `app/build.gradle.kts`.

The workflow runs lint, unit tests and the screenshot check, builds both variants with the derived
version, and refuses to publish unless each APK carries the expected certificate, version name
and version code. The code must also be higher than the previous release's. A release carries:

- `swiftcurrent-<version>.apk`, the release build, signed with the release key.
- `swiftcurrent-<version>-debug.apk`, the debug build, signed with the maintainer's own debug key,
  so it installs over a debug build made on that machine without an uninstall.
- `SHA256SUMS`.

The two variants have different signatures, so moving a device from one to the other means an
uninstall, which wipes local data and unbinds the accessibility service.

## Signing keys

The release keystore (`swiftcurrent-release-key.jks`) stays out of git. **Back it up**: losing it
means installed releases can only be updated after an uninstall. Its password is kept age-encrypted
in the gitignored `mise.local.toml` as `KEYSTORE_PASSWORD`; the key password is the same. To build
a signed release locally, write a `keystore.properties` (also gitignored):

```properties
storeFile=swiftcurrent-release-key.jks
storePassword=…
keyAlias=swiftcurrent
keyPassword=…
```

The expected certificate fingerprints are pinned in `release.yml`. If either key is ever replaced,
update them there and note that installed copies can only update after an uninstall.

## GitHub setup

The signing secrets live in a `release` environment, which only deploys from `v*` tags, so branch
pushes and pull requests cannot read them. A ruleset also limits `v*` tags to the repository
admin. Neither lives in the repo.

| Secret (environment `release`) | Value |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | `base64 < swiftcurrent-release-key.jks` |
| `RELEASE_KEYSTORE_PASSWORD` | the store password |
| `RELEASE_KEY_ALIAS` | `swiftcurrent` |
| `RELEASE_KEY_PASSWORD` | the key password |
| `DEBUG_KEYSTORE_BASE64` | `base64 < ~/.android/debug.keystore` |

Set each with `gh secret set <NAME> --env release`, feeding the value on standard input.
