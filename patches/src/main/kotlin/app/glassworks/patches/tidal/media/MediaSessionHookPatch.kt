package app.glassworks.patches.tidal.media

import app.glassworks.patches.shared.redirectStaticCall
import app.morphe.patcher.patch.bytecodePatch

private const val MEDIA_SESSION_HOOK = "Lapp/glassworks/extension/tidal/media/MediaSessionHook;"

/**
 * Routes TIDAL's media session updates (track metadata and playback state) through the extension,
 * which passes them to the features that use them: Last.fm scrobbling and the album art tint.
 * Not shown in patch lists; patches that need the current track depend on it.
 */
internal val mediaSessionHookPatch = bytecodePatch {
    extendWith("extensions/tidal.mpe")

    execute {
        // The hooks take the same arguments (the session, then the value) and call the original
        // framework method first.
        MediaSessionSetMetadataFingerprint.let {
            it.method.redirectStaticCall(
                it.instructionMatches.first().index,
                "$MEDIA_SESSION_HOOK->setMetadata(Landroid/media/session/MediaSession;Landroid/media/MediaMetadata;)V",
            )
        }
        MediaSessionSetPlaybackStateFingerprint.let {
            it.method.redirectStaticCall(
                it.instructionMatches.first().index,
                "$MEDIA_SESSION_HOOK->setPlaybackState(Landroid/media/session/MediaSession;" +
                    "Landroid/media/session/PlaybackState;)V",
            )
        }
    }
}
