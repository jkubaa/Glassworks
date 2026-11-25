package app.tideglass.extension.settings;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Tideglass preferences, stored in TIDAL's own data folder ({@code shared_prefs/tideglass.xml}).
 * <p>
 * Every getter falls back to its default before {@link #init(Context)} is called, so patched
 * code never crashes if it runs before MainActivity.onCreate.
 */
public final class TideglassSettings {

    public static final boolean DEFAULT_PILL_BAR = true;
    public static final int DEFAULT_PILL_GAP_DP = 10;
    public static final int DEFAULT_TINT_PERCENT = 55;
    public static final int DEFAULT_BLUR_DP = 24;
    public static final int DEFAULT_SATURATION_PERCENT = 160;
    public static final boolean DEFAULT_EDGE_HIGHLIGHT = true;

    public static final int MAX_PILL_GAP_DP = 32;
    public static final int MAX_BLUR_DP = 60;
    public static final int MIN_SATURATION_PERCENT = 50;
    public static final int MAX_SATURATION_PERCENT = 250;

    private static final String PREFERENCES_NAME = "tideglass";

    static final String KEY_PILL_BAR = "pill_bar";
    static final String KEY_PILL_GAP_DP = "pill_gap_dp";
    static final String KEY_TINT_PERCENT = "tint_percent";
    static final String KEY_BLUR_DP = "blur_dp";
    static final String KEY_SATURATION_PERCENT = "saturation_percent";
    static final String KEY_EDGE_HIGHLIGHT = "edge_highlight";

    private static volatile SharedPreferences preferences;

    private TideglassSettings() {
    }

    public static void init(Context context) {
        if (preferences == null) {
            preferences = context.getApplicationContext()
                    .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        }
    }

    /** Whether the frosted pill bar is used instead of TIDAL's original bar. */
    public static boolean isPillBarEnabled() {
        return getBoolean(KEY_PILL_BAR, DEFAULT_PILL_BAR);
    }

    /** Space between the mini player pill and the tab bar pill, in dp. */
    public static int getPillGapDp() {
        return clamp(getInt(KEY_PILL_GAP_DP, DEFAULT_PILL_GAP_DP), 0, MAX_PILL_GAP_DP);
    }

    /** Opacity of the dark tint over the blur, in percent. */
    public static int getTintPercent() {
        return clamp(getInt(KEY_TINT_PERCENT, DEFAULT_TINT_PERCENT), 0, 100);
    }

    /** Blur radius of the content behind the pills, in dp. 0 disables the blur. */
    public static int getBlurDp() {
        return clamp(getInt(KEY_BLUR_DP, DEFAULT_BLUR_DP), 0, MAX_BLUR_DP);
    }

    /** Color saturation of the blurred content, in percent (100 is unchanged). */
    public static int getSaturationPercent() {
        return clamp(getInt(KEY_SATURATION_PERCENT, DEFAULT_SATURATION_PERCENT),
                MIN_SATURATION_PERCENT, MAX_SATURATION_PERCENT);
    }

    /** Whether the pills have a thin light edge. */
    public static boolean isEdgeHighlightEnabled() {
        return getBoolean(KEY_EDGE_HIGHLIGHT, DEFAULT_EDGE_HIGHLIGHT);
    }

    static void putBoolean(String key, boolean value) {
        SharedPreferences prefs = preferences;
        if (prefs != null) {
            prefs.edit().putBoolean(key, value).apply();
        }
    }

    static void putInt(String key, int value) {
        SharedPreferences prefs = preferences;
        if (prefs != null) {
            prefs.edit().putInt(key, value).apply();
        }
    }

    /** Resets only the navigation bar settings, leaving other categories untouched. */
    static void resetNavigationBar() {
        SharedPreferences prefs = preferences;
        if (prefs != null) {
            prefs.edit()
                    .remove(KEY_PILL_BAR)
                    .remove(KEY_PILL_GAP_DP)
                    .remove(KEY_TINT_PERCENT)
                    .remove(KEY_BLUR_DP)
                    .remove(KEY_SATURATION_PERCENT)
                    .remove(KEY_EDGE_HIGHLIGHT)
                    .apply();
        }
    }

    private static boolean getBoolean(String key, boolean defaultValue) {
        SharedPreferences prefs = preferences;
        return prefs == null ? defaultValue : prefs.getBoolean(key, defaultValue);
    }

    private static int getInt(String key, int defaultValue) {
        SharedPreferences prefs = preferences;
        return prefs == null ? defaultValue : prefs.getInt(key, defaultValue);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
