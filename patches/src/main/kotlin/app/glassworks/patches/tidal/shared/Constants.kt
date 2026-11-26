package app.glassworks.patches.tidal.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_TIDAL = Compatibility(
        name = "TIDAL",
        packageName = "com.aspiro.tidal",
        // TIDAL ships from Google Play as split APKs, which APKMirror distributes as .apkm bundles.
        apkFileType = ApkFileType.APKM,
        // TIDAL's icon is black.
        appIconColor = 0x000000,
        targets = listOf(
            // Version the patches were developed and tested against.
            AppTarget(version = "2.221.0"),
        ),
    )
}
