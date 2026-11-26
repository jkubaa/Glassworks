package app.glassworks.patches.tidal.lastfm

import app.glassworks.patches.shared.redirectStaticCall
import app.glassworks.patches.shared.returnEarly
import app.glassworks.patches.tidal.settings.settingsPatch
import app.glassworks.patches.tidal.shared.Constants.COMPATIBILITY_TIDAL
import app.morphe.patcher.patch.bytecodePatch

private const val SCROBBLE_HOOK = "Lapp/glassworks/extension/tidal/lastfm/ScrobbleHook;"

@Suppress("unused")
val lastFmScrobblingPatch = bytecodePatch(
    name = "Last.fm scrobbling",
    description = "Scrobbles the tracks you play to Last.fm and shows what is playing on your profile. " +
        "Log in with your own Last.fm API key in Settings > Glassworks > Last.fm.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_TIDAL)

    dependsOn(settingsPatch)

    extendWith("extensions/tidal.mpe")

    execute {
        LastFmFeatureFingerprint.method.returnEarly(true)

        // Route TIDAL's media session updates through the scrobbler. The hooks take the same
        // arguments (the session, then the value) and call the original framework method first.
        MediaSessionSetMetadataFingerprint.let {
            it.method.redirectStaticCall(
                it.instructionMatches.first().index,
                "$SCROBBLE_HOOK->setMetadata(Landroid/media/session/MediaSession;Landroid/media/MediaMetadata;)V",
            )
        }
        MediaSessionSetPlaybackStateFingerprint.let {
            it.method.redirectStaticCall(
                it.instructionMatches.first().index,
                "$SCROBBLE_HOOK->setPlaybackState(Landroid/media/session/MediaSession;" +
                    "Landroid/media/session/PlaybackState;)V",
            )
        }
    }
}
