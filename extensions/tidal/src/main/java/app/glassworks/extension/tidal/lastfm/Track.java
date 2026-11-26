package app.glassworks.extension.tidal.lastfm;

import org.json.JSONException;
import org.json.JSONObject;

/** The track metadata sent to Last.fm. */
final class Track {

    final String artist;
    final String title;
    final String album;
    /** Duration in milliseconds, or 0 if unknown. */
    final long durationMs;

    Track(String artist, String title, String album, long durationMs) {
        this.artist = artist;
        this.title = title;
        this.album = album == null ? "" : album;
        this.durationMs = Math.max(0, durationMs);
    }

    boolean sameAs(Track other) {
        return other != null && artist.equals(other.artist) && title.equals(other.title)
                && album.equals(other.album) && durationMs == other.durationMs;
    }

    JSONObject toJson(long timestampSeconds) throws JSONException {
        return new JSONObject()
                .put("artist", artist)
                .put("title", title)
                .put("album", album)
                .put("duration", durationMs)
                .put("timestamp", timestampSeconds);
    }

    static Track fromJson(JSONObject json) {
        return new Track(json.optString("artist"), json.optString("title"),
                json.optString("album"), json.optLong("duration"));
    }

    @Override
    public String toString() {
        return artist + " - " + title;
    }
}
