package app.glassworks.extension.tidal.lastfm;

import android.app.Application;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.util.Log;

import app.glassworks.extension.tidal.settings.GlassworksSettings;

/**
 * Entry points called from bytecode injected into TIDAL's media session code
 * ({@code MediaSessionCompat}), which replaces its calls to the Android framework's
 * {@link MediaSession#setMetadata} and {@link MediaSession#setPlaybackState}.
 * <p>
 * Each hook first does exactly what TIDAL's call did, then tells the {@link Scrobbler}.
 * Using the framework types keeps this independent of TIDAL's obfuscated player classes.
 */
@SuppressWarnings("unused")
public final class ScrobbleHook {

    private static final String TAG = "Glassworks";

    private ScrobbleHook() {
    }

    public static void setMetadata(MediaSession session, MediaMetadata metadata) {
        session.setMetadata(metadata);
        try {
            if (!ready()) {
                return;
            }
            Scrobbler.get().onMetadata(session, toTrack(metadata));
        } catch (Throwable t) {
            Log.e(TAG, "Scrobbler metadata update failed", t);
        }
    }

    public static void setPlaybackState(MediaSession session, PlaybackState state) {
        session.setPlaybackState(state);
        try {
            if (!ready()) {
                return;
            }
            boolean playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
            long position = state == null ? 0 : state.getPosition();
            Scrobbler.get().onPlaybackState(session, playing, position);
        } catch (Throwable t) {
            Log.e(TAG, "Scrobbler playback update failed", t);
        }
    }

    /** Whether scrobbling is set up. Initializes the settings if TIDAL started without its UI. */
    private static boolean ready() {
        if (GlassworksSettings.getPreferences() == null) {
            Application application = currentApplication();
            if (application == null) {
                return false;
            }
            GlassworksSettings.init(application);
        }
        return LastFmSettings.isEnabled() && LastFmSettings.isLoggedIn();
    }

    /** The track in TIDAL's metadata, or null if it has no title or artist (such as while loading). */
    private static Track toTrack(MediaMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        String title = trim(metadata.getString(MediaMetadata.METADATA_KEY_TITLE));
        String artist = trim(metadata.getString(MediaMetadata.METADATA_KEY_ARTIST));
        if (title.isEmpty() || artist.isEmpty()) {
            return null;
        }
        if (LastFmSettings.isMainArtistOnly()) {
            artist = mainArtist(artist);
        }
        String album = trim(metadata.getString(MediaMetadata.METADATA_KEY_ALBUM));
        long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        return new Track(artist, title, album, duration);
    }

    /**
     * TIDAL joins a track's artists with ", ", main artist first. Keeps only the first one,
     * unless the next part starts with "The " (as in "Tyler, The Creator").
     */
    static String mainArtist(String artists) {
        int index = 0;
        while ((index = artists.indexOf(", ", index)) > 0) {
            String rest = artists.substring(index + 2);
            if (!rest.regionMatches(true, 0, "The ", 0, 4)) {
                return artists.substring(0, index);
            }
            index += 2;
        }
        return artists;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static Application currentApplication() {
        try {
            return (Application) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }
}
