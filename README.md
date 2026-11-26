# 🧊🧩 Glassworks Patches

Unofficial Morphe patches for Android apps.

## ❓ About

Glassworks patches add features to apps and change how they look and feel. For TIDAL, they
replace the bottom navigation bar with floating, frosted-glass pills you can restyle, and
scrobble what you play to Last.fm with your own API key. Both are set up in TIDAL's
Settings > Glassworks.

Glassworks is not affiliated with the developers of any app it patches or with the Morphe
project. These patches do not unlock paid features or bypass DRM.

### How to use these patches

Click the button below, or manually add this repo url to Morphe Manager: https://github.com/jkubaa/Glassworks

<a href="https://morphe.software/add-source?github=jkubaa/Glassworks">
  <img src=".github/assets/add-to-morphe.svg" alt="Add to Morphe" height="48">
</a>

## 🩹 Patches list

<!-- PATCHES_START -->
> **[v1.0.0-dev.3](https://github.com/jkubaa/Glassworks/releases/tag/v1.0.0-dev.3)**&nbsp;&nbsp;&middot;&nbsp;&nbsp;`dev`&nbsp;&nbsp;&middot;&nbsp;&nbsp;**1 patch** across **1 app**&nbsp;&nbsp;&middot;&nbsp;&nbsp;[Full details](PATCHES.md)

| # | App | Patches | Version | Package |
|---|---|---|---|---|
| 1 | [**TIDAL**](PATCHES.md#tidal-comaspirotidal) | 1 | `2.221.0` | [`com.aspiro.tidal`](https://play.google.com/store/apps/details?id=com.aspiro.tidal) |
<!-- PATCHES_END -->

### 🛠️ Building locally

- Run `./gradlew buildAndroid`
- The built patches .mpp file is found in `patches/build/libs/patches-*.mpp`
- Patch the mpp file using [Morphe-Desktop](https://github.com/MorpheApp/morphe-desktop)
  like any other patch bundle.

See the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation) for more information.

### 🗂️ Project layout

All patches ship in one bundle, with each app's code kept in its own folder:

| Path | Contents |
|---|---|
| `patches/src/main/kotlin/app/glassworks/patches/<app>/` | Patches for one app, plus its `shared/Constants.kt` with the app's compatibility info |
| `patches/src/main/kotlin/app/glassworks/patches/shared/` | Helpers that work with any app |
| `extensions/<app>/` | The app's extension (Java code the patches add to the app), built as `extensions/<app>.mpe` |

To add an app, create `patches/.../patches/<app>/` and, if its patches need extension code,
an `extensions/<app>/` module. Copy `extensions/tidal/build.gradle.kts` as a starting point and
change its `name`, `namespace` and `minSdk`.

## 📜 License

Glassworks Patches are licensed under the [GNU General Public License v3.0](LICENSE)
