package app.glassworks.extension.tidal.settings;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Glassworks preferences, stored in TIDAL's own data folder ({@code shared_prefs/tideglass.xml}).
 * <p>
 * Every getter falls back to its default before {@link #init(Context)} is called, so patched
 * code never crashes if it runs before MainActivity.onCreate.
 */
public final class GlassworksSettings {

    // region Navigation bar

    /** Bar styles. Stored as two booleans so settings from earlier versions keep working. */
    public static final int STYLE_ORIGINAL = 0;
    public static final int STYLE_TWO_PILLS = 1;
    public static final int STYLE_ONE_PILL = 2;

    public static final boolean DEFAULT_PILL_BAR = true;
    public static final boolean DEFAULT_SINGLE_PILL = false;
    public static final int DEFAULT_PILL_GAP_DP = 10;
    public static final int DEFAULT_ROUNDNESS_PERCENT = 100;
    public static final int DEFAULT_SIDE_MARGIN_DP = 6;
    public static final boolean DEFAULT_COMPACT_MINI_PLAYER = true;
    public static final int DEFAULT_LIFT_DP = 0;
    public static final int DEFAULT_TINT_COLOR = 0;
    public static final int DEFAULT_TINT_PERCENT = 55;
    public static final int DEFAULT_BLUR_DP = 24;
    public static final int DEFAULT_SATURATION_PERCENT = 160;
    public static final boolean DEFAULT_EDGE_HIGHLIGHT = true;
    public static final String DEFAULT_SEPARATE_TAB = "";
    public static final int DEFAULT_GLASS_STYLE = 0;
    public static final int DEFAULT_SHADOW_PERCENT = 25;

    public static final int MAX_PILL_GAP_DP = 32;
    public static final int MAX_SIDE_MARGIN_DP = 48;
    public static final int MAX_LIFT_DP = 32;
    public static final int MAX_BLUR_DP = 60;
    public static final int MIN_SATURATION_PERCENT = 50;
    public static final int MAX_SATURATION_PERCENT = 250;

    /** Tint color presets: names shown in settings and their RGB values. */
    public static final String[] TINT_COLOR_NAMES = {
            "Graphite", "Album art", "Black", "Midnight", "Ocean", "Plum", "Wine", "Forest",
    };
    /** -1 marks "Album art", whose color comes from the current track's artwork. */
    public static final int[] TINT_COLORS = {
            0x1C1C1E, -1, 0x000000, 0x0B1A33, 0x053841, 0x2A1236, 0x3A0D18, 0x0F2A1A,
    };
    public static final int TINT_ALBUM_ART = 1;

    /** Mini player artwork shapes. */
    public static final int ARTWORK_TIDAL = 0;
    public static final int ARTWORK_ROUNDED = 1;
    public static final int ARTWORK_CIRCLE = 2;
    public static final int DEFAULT_ARTWORK_SHAPE = ARTWORK_ROUNDED;

    /** Glass styles. */
    public static final int GLASS_FROSTED = 0;
    public static final int GLASS_LIQUID = 1;

    /** TIDAL's tab names ({@code TabBarContract$TabType}) and the labels shown in settings. */
    public static final String[] TAB_NAMES = {"HOME", "FEED", "SEARCH", "COLLECTION", "UPLOADS"};
    public static final String[] TAB_LABELS = {"Home", "Activity", "Search", "My collection", "Upload"};

    // endregion

    // Keeps the name from before the project was renamed to Glassworks,
    // so settings saved by earlier versions are not lost.
    private static final String PREFERENCES_NAME = "tideglass";

    static final String KEY_PILL_BAR = "pill_bar";
    static final String KEY_SINGLE_PILL = "single_pill";
    static final String KEY_PILL_GAP_DP = "pill_gap_dp";
    static final String KEY_ROUNDNESS_PERCENT = "roundness_percent";
    static final String KEY_SIDE_MARGIN_DP = "side_margin_dp";
    static final String KEY_LIFT_DP = "lift_dp";
    static final String KEY_TINT_COLOR = "tint_color";
    static final String KEY_TINT_PERCENT = "tint_percent";
    static final String KEY_BLUR_DP = "blur_dp";
    static final String KEY_SATURATION_PERCENT = "saturation_percent";
    static final String KEY_EDGE_HIGHLIGHT = "edge_highlight";
    static final String KEY_SEPARATE_TAB = "separate_tab";
    static final String KEY_HIDDEN_TAB_PREFIX = "hide_tab_";
    static final String KEY_TAB_ORDER = "tab_order";
    static final String KEY_GLASS_STYLE = "glass_style";
    static final String KEY_SHADOW_PERCENT = "shadow_percent";
    static final String KEY_ARTWORK_SHAPE = "artwork_shape";
    static final String KEY_COMPACT_MINI_PLAYER = "compact_mini_player";

    private static final String[] NAVIGATION_BAR_KEYS = {
            KEY_PILL_BAR, KEY_SINGLE_PILL, KEY_PILL_GAP_DP, KEY_ROUNDNESS_PERCENT, KEY_SIDE_MARGIN_DP,
            KEY_LIFT_DP, KEY_TINT_COLOR, KEY_TINT_PERCENT, KEY_BLUR_DP, KEY_SATURATION_PERCENT,
            KEY_EDGE_HIGHLIGHT, KEY_SEPARATE_TAB, KEY_TAB_ORDER, KEY_GLASS_STYLE, KEY_SHADOW_PERCENT,
            KEY_ARTWORK_SHAPE, KEY_COMPACT_MINI_PLAYER,
    };

    private static volatile SharedPreferences preferences;
    /** Increased on every change, so cached drawing objects know to update. */
    private static volatile int version;

    private GlassworksSettings() {
    }

    public static void init(Context context) {
        if (preferences == null) {
            preferences = context.getApplicationContext()
                    .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        }
    }

    // region Navigation bar getters

    /** One of {@link #STYLE_ORIGINAL}, {@link #STYLE_TWO_PILLS} or {@link #STYLE_ONE_PILL}. */
    public static int getBarStyle() {
        if (!getBoolean(KEY_PILL_BAR, DEFAULT_PILL_BAR)) {
            return STYLE_ORIGINAL;
        }
        return getBoolean(KEY_SINGLE_PILL, DEFAULT_SINGLE_PILL) ? STYLE_ONE_PILL : STYLE_TWO_PILLS;
    }

    /** Whether a frosted style (one or two pills) is used instead of TIDAL's original bar. */
    public static boolean isPillBarEnabled() {
        return getBarStyle() != STYLE_ORIGINAL;
    }

    public static boolean isTwoPills() {
        return getBarStyle() == STYLE_TWO_PILLS;
    }

    public static boolean isOnePill() {
        return getBarStyle() == STYLE_ONE_PILL;
    }

    /** Space between the mini player pill and the tab bar pill, in dp. */
    public static int getPillGapDp() {
        return clamp(getInt(KEY_PILL_GAP_DP, DEFAULT_PILL_GAP_DP), 0, MAX_PILL_GAP_DP);
    }

    /** Corner roundness in percent: 100 is a full pill, 0 a rectangle. */
    public static int getRoundnessPercent() {
        return clamp(getInt(KEY_ROUNDNESS_PERCENT, DEFAULT_ROUNDNESS_PERCENT), 0, 100);
    }

    /**
     * Extra space at the left and right of the bar, in dp, on top of TIDAL's 16dp screen margin.
     * The default insets the bar from the content, so it reads as floating above it.
     */
    public static int getSideMarginDp() {
        return clamp(getInt(KEY_SIDE_MARGIN_DP, DEFAULT_SIDE_MARGIN_DP), 0, MAX_SIDE_MARGIN_DP);
    }

    /** Extra space below the bar, in dp. */
    public static int getLiftDp() {
        return clamp(getInt(KEY_LIFT_DP, DEFAULT_LIFT_DP), 0, MAX_LIFT_DP);
    }

    /** Index into {@link #TINT_COLORS}. */
    public static int getTintColorIndex() {
        return clamp(getInt(KEY_TINT_COLOR, DEFAULT_TINT_COLOR), 0, TINT_COLORS.length - 1);
    }

    /** Tint color as RGB, without alpha. For "Album art", the fallback (Graphite). */
    public static int getTintRgb() {
        int rgb = TINT_COLORS[getTintColorIndex()];
        return rgb < 0 ? TINT_COLORS[0] : rgb;
    }

    /** Whether the mini player uses smaller artwork and padding, about 52dp tall instead of 64dp. */
    public static boolean isCompactMiniPlayer() {
        return getBoolean(KEY_COMPACT_MINI_PLAYER, DEFAULT_COMPACT_MINI_PLAYER);
    }

    /** {@link #ARTWORK_TIDAL}, {@link #ARTWORK_ROUNDED} or {@link #ARTWORK_CIRCLE}. */
    public static int getArtworkShape() {
        return clamp(getInt(KEY_ARTWORK_SHAPE, DEFAULT_ARTWORK_SHAPE), ARTWORK_TIDAL, ARTWORK_CIRCLE);
    }

    /** {@link #GLASS_FROSTED} or {@link #GLASS_LIQUID}. */
    public static int getGlassStyle() {
        return clamp(getInt(KEY_GLASS_STYLE, DEFAULT_GLASS_STYLE), GLASS_FROSTED, GLASS_LIQUID);
    }

    /** Strength of the shadow below the pills, in percent. 0 disables it. */
    public static int getShadowPercent() {
        return clamp(getInt(KEY_SHADOW_PERCENT, DEFAULT_SHADOW_PERCENT), 0, 100);
    }

    /** Opacity of the tint over the blur, in percent. */
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

    /** Name of the tab shown in its own pill at the right of the tab bar, or "" for none. */
    public static String getSeparateTab() {
        return getString(KEY_SEPARATE_TAB, DEFAULT_SEPARATE_TAB);
    }

    /** Whether the tab with this {@code TabBarContract$TabType} name is hidden. */
    public static boolean isTabHidden(String tabName) {
        return getBoolean(KEY_HIDDEN_TAB_PREFIX + tabName, false);
    }

    /**
     * Tab names in the order chosen in settings. Tabs missing from the saved order (such as ones
     * TIDAL adds later) keep TIDAL's place after the ordered ones. Empty if no order was chosen.
     */
    public static String[] getTabOrder() {
        String order = getString(KEY_TAB_ORDER, "");
        return order.isEmpty() ? new String[0] : order.split(",");
    }

    /** The tab order for the settings page: the saved order, completed with the other tabs. */
    static String[] getFullTabOrder() {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (String name : getTabOrder()) {
            if (java.util.Arrays.asList(TAB_NAMES).contains(name) && !result.contains(name)) {
                result.add(name);
            }
        }
        for (String name : TAB_NAMES) {
            if (!result.contains(name)) {
                result.add(name);
            }
        }
        return result.toArray(new String[0]);
    }

    static String tabLabel(String name) {
        for (int i = 0; i < TAB_NAMES.length; i++) {
            if (TAB_NAMES[i].equals(name)) {
                return TAB_LABELS[i];
            }
        }
        return name;
    }

    /** Changes every time a setting changes. */
    public static int getVersion() {
        return version;
    }

    // endregion

    // region Storage

    static void putBoolean(String key, boolean value) {
        SharedPreferences prefs = preferences;
        version++;
        if (prefs != null) {
            prefs.edit().putBoolean(key, value).apply();
        }
    }

    static void putInt(String key, int value) {
        SharedPreferences prefs = preferences;
        version++;
        if (prefs != null) {
            prefs.edit().putInt(key, value).apply();
        }
    }

    public static void putString(String key, String value) {
        SharedPreferences prefs = preferences;
        version++;
        if (prefs != null) {
            if (value == null) {
                prefs.edit().remove(key).apply();
            } else {
                prefs.edit().putString(key, value).apply();
            }
        }
    }

    static void putBarStyle(int style) {
        SharedPreferences prefs = preferences;
        version++;
        if (prefs != null) {
            prefs.edit()
                    .putBoolean(KEY_PILL_BAR, style != STYLE_ORIGINAL)
                    .putBoolean(KEY_SINGLE_PILL, style == STYLE_ONE_PILL)
                    .apply();
        }
    }

    /** Resets only the navigation bar settings, leaving other categories untouched. */
    static void resetNavigationBar() {
        SharedPreferences prefs = preferences;
        version++;
        if (prefs != null) {
            SharedPreferences.Editor editor = prefs.edit();
            for (String key : NAVIGATION_BAR_KEYS) {
                editor.remove(key);
            }
            for (String tab : TAB_NAMES) {
                editor.remove(KEY_HIDDEN_TAB_PREFIX + tab);
            }
            editor.apply();
        }
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        SharedPreferences prefs = preferences;
        return prefs == null ? defaultValue : prefs.getBoolean(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        SharedPreferences prefs = preferences;
        return prefs == null ? defaultValue : prefs.getInt(key, defaultValue);
    }

    public static String getString(String key, String defaultValue) {
        SharedPreferences prefs = preferences;
        return prefs == null ? defaultValue : prefs.getString(key, defaultValue);
    }

    /** The preferences, or null before {@link #init(Context)}. */
    public static SharedPreferences getPreferences() {
        return preferences;
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // endregion
}
