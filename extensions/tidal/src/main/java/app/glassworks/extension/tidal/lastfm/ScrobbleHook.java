package app.glassworks.extension.tidal.lastfm;

import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;

/**
 * Passes TIDAL's media session updates to the {@link Scrobbler}. Called by
 * {@link app.glassworks.extension.tidal.media.MediaSessionHook}.
 */
public final class ScrobbleHook {

    private ScrobbleHook() {
    }

    public static void onMetadata(MediaSession session, MediaMetadata metadata) {
        if (isActive()) {
            Scrobbler.get().onMetadata(session, toTrack(metadata));
        }
    }

    public static void onPlaybackState(MediaSession session, PlaybackState state) {
        if (!isActive()) {
            return;
        }
        boolean playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
        long position = state == null ? 0 : state.getPosition();
        Scrobbler.get().onPlaybackState(session, playing, position);
    }

    private static boolean isActive() {
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
}
