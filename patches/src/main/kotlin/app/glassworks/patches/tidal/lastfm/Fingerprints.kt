package app.glassworks.patches.tidal.lastfm

import app.morphe.patcher.Fingerprint

/** Marks the Last.fm feature as present for the settings page. */
internal object LastFmFeatureFingerprint : Fingerprint(
    definingClass = "Lapp/glassworks/extension/tidal/settings/Features;",
    name = "lastFm",
    returnType = "Z",
    parameters = listOf(),
)
