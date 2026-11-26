package app.glassworks.extension.tidal.settings;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.Log;

/**
 * Colors, fonts and icons of TIDAL's own settings screens, so Glassworks pages look like them.
 * Fonts and icons are TIDAL's resources, looked up by name; if one is missing, a system
 * equivalent is used.
 */
final class TidalStyle {

    static final int BACKGROUND = 0xFF000000;
    static final int PRIMARY_TEXT = 0xFFFFFFFF;
    /** Subtitles and values (sampled from TIDAL's settings rows). */
    static final int SECONDARY_TEXT = 0xFFAFAFB6;
    /** Switch track when off (sampled from TIDAL's offline mode switch). */
    static final int SWITCH_TRACK_OFF = 0xFF788893;
    /** Switch track when on, and TIDAL's accent (sampled from Audio & Playback). */
    static final int SWITCH_TRACK_ON = 0xFF33FFEE;
    static final int SWITCH_THUMB = 0xFFFFFFFF;
    static final int SLIDER_TRACK = 0xFF3A3A40;
    static final int RIPPLE = 0x33FFFFFF;
    static final int DIALOG_BACKGROUND = 0xFF1C1C1F;
    static final int ERROR_TEXT = 0xFFFF6E6E;

    /** Sizes measured from TIDAL's settings at font scale 1.0. */
    static final float TITLE_SP = 17f;
    static final float SUBTITLE_SP = 15f;
    static final float PAGE_TITLE_SP = 17f;
    /** Section headers, such as "Quality" in Audio & Playback. */
    static final float SECTION_SP = 26f;
    static final float NOTE_SP = 14f;
    /** TIDAL's switch: a 56x28dp track with a 20dp thumb. */
    static final int SWITCH_WIDTH_DP = 56;
    static final int SWITCH_HEIGHT_DP = 28;
    static final int SWITCH_THUMB_INSET_DP = 4;

    private static final String TAG = "Glassworks";

    private static Typeface bold;
    private static Typeface demiBold;
    private static Typeface medium;

    private TidalStyle() {
    }

    /** TIDAL's Nationale Bold, used for section headers. */
    static Typeface bold(Context context) {
        if (bold == null) {
            bold = font(context, "nationale_bold", Typeface.DEFAULT_BOLD);
        }
        return bold;
    }

    /** TIDAL's Nationale DemiBold, used for settings titles and subtitles. */
    static Typeface demiBold(Context context) {
        if (demiBold == null) {
            demiBold = font(context, "nationale_demi_bold", Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return demiBold;
    }

    /** TIDAL's Nationale Medium, used for longer explanations. */
    static Typeface medium(Context context) {
        if (medium == null) {
            medium = font(context, "nationale_medium", Typeface.DEFAULT);
        }
        return medium;
    }

    /** TIDAL's back arrow icon, or null if it is missing. */
    static Drawable backIcon(Context context) {
        return drawable(context, "ic_back");
    }

    static Drawable drawable(Context context, String name) {
        try {
            int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
            return id == 0 ? null : context.getDrawable(id);
        } catch (Exception e) {
            Log.w(TAG, "Drawable not found: " + name, e);
            return null;
        }
    }

    private static Typeface font(Context context, String name, Typeface fallback) {
        try {
            int id = context.getResources().getIdentifier(name, "font", context.getPackageName());
            if (id != 0) {
                Typeface typeface = context.getResources().getFont(id);
                if (typeface != null) {
                    return typeface;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Font not found: " + name, e);
        }
        return fallback;
    }
}
