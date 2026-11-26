package app.glassworks.patches.tidal.media

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall

private const val MEDIA_SESSION = "Landroid/media/session/MediaSession;"

/**
 * The method that passes TIDAL's track metadata to the Android media session
 * (`MediaSessionCompat.MediaSessionImplApi21.setMetadata`). Matched by its call to the framework,
 * which is never obfuscated.
 */
internal object MediaSessionSetMetadataFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        methodCall(
            definingClass = MEDIA_SESSION,
            name = "setMetadata",
            parameters = listOf("Landroid/media/MediaMetadata;"),
            returnType = "V",
        ),
    ),
)

/**
 * The method that passes TIDAL's playback state to the Android media session
 * (`MediaSessionCompat.MediaSessionImplApi21.setPlaybackState`).
 */
internal object MediaSessionSetPlaybackStateFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        methodCall(
            definingClass = MEDIA_SESSION,
            name = "setPlaybackState",
            parameters = listOf("Landroid/media/session/PlaybackState;"),
            returnType = "V",
        ),
    ),
)
