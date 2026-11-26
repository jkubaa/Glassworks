package app.glassworks.patches.tidal.dev

import app.glassworks.patches.tidal.shared.Constants.COMPATIBILITY_TIDAL
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import org.w3c.dom.Element

/*
 * Dev-only: compiled only with -Pglassworks.dev, so it never ships in the published bundle.
 * It gives a test build its own package name, label and icon color, so it installs next to the TIDAL app
 * patched by Morphe Manager instead of replacing it.
 */

private const val ORIGINAL_PACKAGE = "com.aspiro.tidal"

// Must not contain "internal" or "test": TIDAL turns on internal debug features for
// application IDs that do.
private const val DEV_PACKAGE = "com.aspiro.tidal.glassworks.dev"
private const val DEV_LABEL = "TIDAL Dev"

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

/** Launcher icon background: violet instead of TIDAL's black, with a lighter glassy corner. */
private const val DEV_ICON_COLOR = "#ff5b2bd6"
private const val DEV_ICON_HIGHLIGHT_COLOR = "#ff7c55ec"

/**
 * `TidalApp.onCreate`, which sets TIDAL's application ID constant. TIDAL builds FileProvider
 * authorities from it (such as `<id>.screenshot` and `<id>.logs`), so it must match the new name.
 */
internal object TidalAppOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/tidal/android/application/TidalApp;",
    name = "onCreate",
    returnType = "V",
    filters = listOf(string(ORIGINAL_PACKAGE)),
)

private val devManifestPatch = resourcePatch {
    execute {
        // The adaptive icon's background layer: a single black 108x108 square path.
        document("res/drawable/icon_background.xml").use { dom ->
            val paths = dom.getElementsByTagName("path")
            for (i in 0 until paths.length) {
                (paths.item(i) as Element).setAttributeNS(ANDROID_NS, "android:fillColor", DEV_ICON_COLOR)
            }
            val highlight = dom.createElement("path")
            highlight.setAttributeNS(ANDROID_NS, "android:fillColor", DEV_ICON_HIGHLIGHT_COLOR)
            highlight.setAttributeNS(ANDROID_NS, "android:pathData", "M108,30L108,108L30,108Z")
            dom.documentElement.appendChild(highlight)
        }

        document("AndroidManifest.xml").use { dom ->
            val manifest = dom.documentElement
            manifest.setAttribute("package", DEV_PACKAGE)

            fun renamed(value: String) =
                if (value.startsWith("$ORIGINAL_PACKAGE.")) DEV_PACKAGE + value.removePrefix(ORIGINAL_PACKAGE) else value

            // Provider authorities and permissions TIDAL defines must be unique per device.
            val providers = dom.getElementsByTagName("provider")
            for (i in 0 until providers.length) {
                val provider = providers.item(i) as Element
                val authorities = provider.getAttributeNS(ANDROID_NS, "authorities")
                if (authorities.isNotEmpty()) {
                    provider.setAttributeNS(
                        ANDROID_NS,
                        "android:authorities",
                        authorities.split(';').joinToString(";") { renamed(it) },
                    )
                }
            }
            for (tag in listOf("permission", "uses-permission")) {
                val elements = dom.getElementsByTagName(tag)
                for (i in 0 until elements.length) {
                    val element = elements.item(i) as Element
                    val name = element.getAttributeNS(ANDROID_NS, "name")
                    if (name.startsWith("$ORIGINAL_PACKAGE.") && name.endsWith("_PERMISSION")) {
                        element.setAttributeNS(ANDROID_NS, "android:name", renamed(name))
                    }
                }
            }

            // A different name in the launcher, so the two apps can be told apart.
            val application = dom.getElementsByTagName("application").item(0) as Element
            application.setAttributeNS(ANDROID_NS, "android:label", DEV_LABEL)
            val activities = dom.getElementsByTagName("activity")
            for (i in 0 until activities.length) {
                val activity = activities.item(i) as Element
                if (activity.hasAttributeNS(ANDROID_NS, "label") &&
                    activity.getElementsByTagName("category").let { categories ->
                        (0 until categories.length).any {
                            (categories.item(it) as Element).getAttributeNS(ANDROID_NS, "name") ==
                                "android.intent.category.LAUNCHER"
                        }
                    }
                ) {
                    activity.setAttributeNS(ANDROID_NS, "android:label", DEV_LABEL)
                }
            }
        }
    }
}

@Suppress("unused")
val devBuildPatch = bytecodePatch(
    name = "Dev build",
    description = "Dev only. Installs as \"$DEV_LABEL\" ($DEV_PACKAGE) with a violet icon, " +
        "next to the regular patched TIDAL.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_TIDAL)

    dependsOn(devManifestPatch)

    execute {
        TidalAppOnCreateFingerprint.let {
            val index = it.instructionMatches.first().index
            val register = it.method.getInstruction<OneRegisterInstruction>(index).registerA
            it.method.replaceInstruction(index, "const-string v$register, \"$DEV_PACKAGE\"")
        }
    }
}
