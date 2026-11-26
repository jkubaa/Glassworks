package app.glassworks.extension.tidal.settings;

/**
 * Which Glassworks patches were applied. Each method returns false here, and the patch that
 * provides the feature replaces its body with {@code return true}, so the settings only show
 * categories for patches that are actually in the app.
 */
@SuppressWarnings("unused")
public final class Features {

    private Features() {
    }

    /** Replaced by the "Pill navigation bar" patch. */
    public static boolean navigationBar() {
        return false;
    }

    /** Replaced by the "Last.fm scrobbling" patch. */
    public static boolean lastFm() {
        return false;
    }
}
