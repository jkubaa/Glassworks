# 🧊🧩 Tideglass Patches

Unofficial Morphe patches for the TIDAL Android app.

## ❓ About

Tideglass patches change how TIDAL looks and feels. The first patch replaces the bottom
navigation bar with a floating, pill-shaped bar on a frosted-glass background.

Tideglass is not affiliated with TIDAL or with the Morphe project. These patches do not
unlock paid features or bypass DRM.

### How to use these patches

Click the button below, or manually add this repo url to Morphe Manager: https://github.com/jkubaa/tideglass-patches

<a href="https://morphe.software/add-source?github=jkubaa/tideglass-patches">
  <img src=".github/assets/add-to-morphe.svg" alt="Add to Morphe" height="48">
</a>

## 🩹 Patches list

<!-- PATCHES_START -->
> **[v1.0.0-dev.1](https://github.com/jkubaa/tideglass-patches/releases/tag/v1.0.0-dev.1)**&nbsp;&nbsp;&middot;&nbsp;&nbsp;`dev`&nbsp;&nbsp;&middot;&nbsp;&nbsp;**1 patch** across **1 app**&nbsp;&nbsp;&middot;&nbsp;&nbsp;[Full details](PATCHES.md)

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

## 📜 License

UserXYZ Patches are licensed under the [GNU General Public License v3.0](LICENSE)
