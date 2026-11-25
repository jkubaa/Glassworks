extension {
    name = "extensions/extension.mpe"
}

android {
    namespace = "app.tideglass.extension"

    defaultConfig {
        // TIDAL 2.221.0 requires API 32, and the frosted backdrop uses RenderEffect (API 31).
        minSdk = 32
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
