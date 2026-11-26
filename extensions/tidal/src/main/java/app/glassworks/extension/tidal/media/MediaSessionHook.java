package app.glassworks.extension.tidal.media;

import android.app.Application;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.util.Log;

import app.glassworks.extension.tidal.lastfm.ScrobbleHook;
import app.glassworks.extension.tidal.pill.ArtworkTint;
import app.glassworks.extension.tidal.settings.Features;
import app.glassworks.extension.tidal.settings.GlassworksSettings;

/**
 * Entry points called from bytecode injected into TIDAL's media session code
 * ({@code MediaSessionCompat}), which replaces its calls to the Android framework's
 * {@link MediaSession#setMetadata} and {@link MediaSession#setPlaybackState}.
 * <p>
 * Each hook first does exactly what TIDAL's call did, then passes the update to the features
 * that were patched in. Using the framework types keeps this independent of TIDAL's obfuscated
 * player classes.
 */
@SuppressWarnings("unused")
public final class MediaSessionHook {

    private static final String TAG = "Glassworks";

    private MediaSessionHook() {
    }

    public static void setMetadata(MediaSession session, MediaMetadata metadata) {
        session.setMetadata(metadata);
        if (!initSettings()) {
            return;
        }
        try {
            if (Features.lastFm()) {
                ScrobbleHook.onMetadata(session, metadata);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Scrobbler metadata update failed", t);
        }
        try {
            if (Features.navigationBar()) {
                ArtworkTint.onMetadata(metadata);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Artwork tint update failed", t);
        }
    }

    public static void setPlaybackState(MediaSession session, PlaybackState state) {
        session.setPlaybackState(state);
        if (!initSettings()) {
            return;
        }
        try {
            if (Features.lastFm()) {
                ScrobbleHook.onPlaybackState(session, state);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Scrobbler playback update failed", t);
        }
    }

    /** Initializes the settings if TIDAL started without its UI, such as from a media button. */
    private static boolean initSettings() {
        if (GlassworksSettings.getPreferences() != null) {
            return true;
        }
        try {
            Application application = (Application) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            if (application == null) {
                return false;
            }
            GlassworksSettings.init(application);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
