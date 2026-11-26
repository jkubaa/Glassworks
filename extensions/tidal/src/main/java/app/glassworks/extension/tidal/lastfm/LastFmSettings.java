package app.glassworks.extension.tidal.lastfm;

import android.content.SharedPreferences;

import app.glassworks.extension.tidal.BuildConfig;
import app.glassworks.extension.tidal.settings.GlassworksSettings;

/**
 * Last.fm settings, stored with the other Glassworks settings.
 * <p>
 * Requests use the built-in API account unless the user switches to their own key (or the build
 * has none). Logging in through the browser never shows the password to Glassworks; the
 * password login sends it once to Last.fm to get a session key and never stores it.
 */
public final class LastFmSettings {

    public static final boolean DEFAULT_ENABLED = true;
    public static final boolean DEFAULT_NOW_PLAYING = true;
    public static final boolean DEFAULT_MAIN_ARTIST_ONLY = true;
    public static final int DEFAULT_THRESHOLD_PERCENT = 50;
    /** Last.fm's rule: a track counts once half of it played (or 4 minutes), never earlier. */
    public static final int MIN_THRESHOLD_PERCENT = 50;
    public static final int MAX_THRESHOLD_PERCENT = 95;

    public static final String KEY_ENABLED = "lastfm_enabled";
    public static final String KEY_NOW_PLAYING = "lastfm_now_playing";
    public static final String KEY_MAIN_ARTIST_ONLY = "lastfm_main_artist_only";
    public static final String KEY_THRESHOLD_PERCENT = "lastfm_threshold_percent";
    public static final String KEY_API_KEY = "lastfm_api_key";
    public static final String KEY_API_SECRET = "lastfm_api_secret";
    static final String KEY_USE_OWN_API_KEY = "lastfm_use_own_api_key";
    static final String KEY_SESSION_KEY = "lastfm_session_key";
    static final String KEY_USERNAME = "lastfm_username";
    static final String KEY_QUEUE = "lastfm_queue";
    static final String KEY_SCROBBLE_COUNT = "lastfm_scrobble_count";
    static final String KEY_LAST_ERROR = "lastfm_last_error";

    private LastFmSettings() {
    }

    public static boolean isEnabled() {
        return GlassworksSettings.getBoolean(KEY_ENABLED, DEFAULT_ENABLED);
    }

    public static boolean isNowPlayingEnabled() {
        return GlassworksSettings.getBoolean(KEY_NOW_PLAYING, DEFAULT_NOW_PLAYING);
    }

    public static boolean isMainArtistOnly() {
        return GlassworksSettings.getBoolean(KEY_MAIN_ARTIST_ONLY, DEFAULT_MAIN_ARTIST_ONLY);
    }

    /** How much of a track must be played before it is scrobbled (Last.fm also caps it at 4 minutes). */
    public static int getThresholdPercent() {
        int value = GlassworksSettings.getInt(KEY_THRESHOLD_PERCENT, DEFAULT_THRESHOLD_PERCENT);
        return Math.max(MIN_THRESHOLD_PERCENT, Math.min(MAX_THRESHOLD_PERCENT, value));
    }

    /** Whether this build has Glassworks' own Last.fm API account (release builds from CI do). */
    public static boolean hasBuiltInApiAccount() {
        return !BuildConfig.LASTFM_API_KEY.isEmpty() && !BuildConfig.LASTFM_API_SECRET.isEmpty();
    }

    /**
     * Whether requests use the API key the user entered instead of the built-in one. Users who
     * entered a key before the built-in one existed keep using theirs, so their session stays valid.
     */
    public static boolean isUsingOwnApiKey() {
        return !hasBuiltInApiAccount()
                || GlassworksSettings.getBoolean(KEY_USE_OWN_API_KEY, !getOwnApiKey().isEmpty());
    }

    /** Switches between the built-in and the user's API key. A session only works with its key, so this logs out. */
    public static void setUsingOwnApiKey(boolean useOwn) {
        if (useOwn != isUsingOwnApiKey()) {
            logOut();
        }
        SharedPreferences prefs = GlassworksSettings.getPreferences();
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_USE_OWN_API_KEY, useOwn).apply();
        }
    }

    /** The API key requests use. */
    static String getApiKey() {
        return isUsingOwnApiKey() ? getOwnApiKey() : BuildConfig.LASTFM_API_KEY;
    }

    static String getApiSecret() {
        return isUsingOwnApiKey() ? getOwnApiSecret() : BuildConfig.LASTFM_API_SECRET;
    }

    /** The API key the user entered, or "". */
    public static String getOwnApiKey() {
        return GlassworksSettings.getString(KEY_API_KEY, "").trim();
    }

    public static String getOwnApiSecret() {
        return GlassworksSettings.getString(KEY_API_SECRET, "").trim();
    }

    static String getSessionKey() {
        return GlassworksSettings.getString(KEY_SESSION_KEY, "");
    }

    /** The logged in Last.fm username, or "" when logged out. */
    public static String getUsername() {
        return getSessionKey().isEmpty() ? "" : GlassworksSettings.getString(KEY_USERNAME, "");
    }

    public static boolean hasApiAccount() {
        return !getApiKey().isEmpty() && !getApiSecret().isEmpty();
    }

    public static boolean isLoggedIn() {
        return hasApiAccount() && !getSessionKey().isEmpty();
    }

    /** Scrobbles sent since logging in. */
    public static int getScrobbleCount() {
        return GlassworksSettings.getInt(KEY_SCROBBLE_COUNT, 0);
    }

    /** The last error, or "" if the last request succeeded. */
    public static String getLastError() {
        return GlassworksSettings.getString(KEY_LAST_ERROR, "");
    }

    static void setSession(String sessionKey, String username) {
        SharedPreferences prefs = GlassworksSettings.getPreferences();
        if (prefs != null) {
            prefs.edit()
                    .putString(KEY_SESSION_KEY, sessionKey)
                    .putString(KEY_USERNAME, username)
                    .putInt(KEY_SCROBBLE_COUNT, 0)
                    .remove(KEY_LAST_ERROR)
                    .apply();
        }
    }

    /** Logs out and drops scrobbles that were not sent yet. */
    public static void logOut() {
        SharedPreferences prefs = GlassworksSettings.getPreferences();
        if (prefs != null) {
            prefs.edit()
                    .remove(KEY_SESSION_KEY)
                    .remove(KEY_USERNAME)
                    .remove(KEY_QUEUE)
                    .remove(KEY_SCROBBLE_COUNT)
                    .remove(KEY_LAST_ERROR)
                    .apply();
        }
    }

    static void addScrobbleCount(int count) {
        SharedPreferences prefs = GlassworksSettings.getPreferences();
        if (prefs != null && count > 0) {
            prefs.edit().putInt(KEY_SCROBBLE_COUNT, getScrobbleCount() + count).apply();
        }
    }

    static void setLastError(String error) {
        GlassworksSettings.putString(KEY_LAST_ERROR, error);
    }
}
