package app.glassworks.patches.tidal.lastfm

import app.glassworks.patches.shared.returnEarly
import app.glassworks.patches.tidal.media.mediaSessionHookPatch
import app.glassworks.patches.tidal.settings.settingsPatch
import app.glassworks.patches.tidal.shared.Constants.COMPATIBILITY_TIDAL
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val lastFmScrobblingPatch = bytecodePatch(
    name = "Last.fm scrobbling",
    description = "Scrobbles the tracks you play to Last.fm and shows what is playing on your profile. " +
        "Log in with your Last.fm account in Settings > Glassworks > Last.fm.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_TIDAL)

    dependsOn(settingsPatch, mediaSessionHookPatch)

    extendWith("extensions/tidal.mpe")

    execute {
        // Turns on scrobbling in the media session hook and shows the Last.fm settings page.
        LastFmFeatureFingerprint.method.returnEarly(true)
    }
}
