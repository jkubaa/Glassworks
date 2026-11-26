import java.util.Properties

extension {
    name = "extensions/tidal.mpe"
}

// The built-in Last.fm API account. CI passes it from the LASTFM_API_KEY and LASTFM_API_SECRET
// repository secrets; local builds can set lastfm.apiKey and lastfm.apiSecret in local.properties.
// Without them the build still works, and the app asks users for their own key.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun lastFmValue(environmentVariable: String, localProperty: String): String {
    val value = providers.environmentVariable(environmentVariable).orNull?.trim().orEmpty()
        .ifEmpty { localProperties.getProperty(localProperty, "").trim() }
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

android {
    namespace = "app.glassworks.extension.tidal"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // TIDAL 2.221.0 requires API 32, and the frosted backdrop uses RenderEffect (API 31).
        minSdk = 32

        buildConfigField("String", "LASTFM_API_KEY", lastFmValue("LASTFM_API_KEY", "lastfm.apiKey"))
        buildConfigField("String", "LASTFM_API_SECRET", lastFmValue("LASTFM_API_SECRET", "lastfm.apiSecret"))
    }
}

dependencies {
    // Compile against the same Compose UI version TIDAL ships (META-INF/androidx.compose.ui_ui.version).
    // compileOnly: the app already contains Compose, so nothing is bundled into the extension.
    //
    // TIDAL's R8 renamed Kotlin's Function1/Unit, so extension code must only call Compose APIs whose
    // JVM signatures contain no Kotlin lambda or Unit types. Everything used here was checked against
    // the decompiled 2.221.0 APK.
    compileOnly("androidx.compose.ui:ui-android:1.9.2")
}
