package app.glassworks.extension.tidal.pill;

import androidx.compose.ui.Modifier;
import androidx.compose.ui.draw.ClipKt;
import androidx.compose.ui.graphics.Brush;
import androidx.compose.ui.graphics.Shape;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import app.glassworks.extension.tidal.settings.GlassworksSettings;

/**
 * Entry points called from bytecode injected into TIDAL's AppScaffold and TabBar composables.
 * <p>
 * TIDAL draws the mini player and the tab bar on one shared rounded card. Depending on the bar
 * style setting:
 * <ul>
 * <li>Two pills: the card's background, clip and black gradient are skipped, and the mini player
 * and the tab bar each get their own frosted pill.</li>
 * <li>One pill: the card's solid background is replaced with a frosted one.</li>
 * <li>Original: every call does exactly what TIDAL's original code did.</li>
 * </ul>
 */
@SuppressWarnings("unused")
public final class FrostedPillPatch {

    /**
     * TIDAL's mini player row has 12dp padding above and 8dp below, which looked even on the
     * shared card. In its own pill that sits 2dp low, so the content is moved up by half the
     * difference.
     */
    private static final float MINI_PLAYER_CENTERING_DP = -2f;

    /** Corner radius of the one-pill card at 100% roundness, in dp (half the tab bar height). */
    private static final float CARD_MAX_RADIUS_DP = 28f;

    /**
     * Modifiers this class already returned. TabBar's restart lambda passes its (already wrapped)
     * modifier back into the same function on recomposition, so wrapping must be idempotent.
     */
    private static final Set<Modifier> WRAPPED =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private FrostedPillPatch() {
    }

    /**
     * Injected at the start of the TabBar row composable, wrapping its {@code modifier} parameter.
     */
    public static Modifier tabBarModifier(Modifier modifier) {
        if (!GlassworksSettings.isTwoPills() || modifier == null || WRAPPED.contains(modifier)) {
            return modifier;
        }
        int side = GlassworksSettings.getSideMarginDp();
        Modifier withMargins = ComposeCompat.padding(modifier, side, 0f, side, GlassworksSettings.getLiftDp());
        return remember(withMargins.then(FrostedPillElement.TAB_BAR));
    }

    /**
     * Injected before AppScaffold's mini player {@code AnimatedVisibility} call, wrapping its
     * modifier. The gap is padding below the pill, so it only exists while the mini player shows.
     */
    public static Modifier miniPlayerModifier(Modifier modifier) {
        if (!GlassworksSettings.isTwoPills() || modifier == null || WRAPPED.contains(modifier)) {
            return modifier;
        }
        int side = GlassworksSettings.getSideMarginDp();
        Modifier withGap = ComposeCompat.padding(modifier, side, 0f, side, GlassworksSettings.getPillGapDp());
        Modifier pill = withGap.then(FrostedPillElement.MINI_PLAYER);
        return remember(ComposeCompat.offset(pill, 0f, MINI_PLAYER_CENTERING_DP));
    }

    /**
     * Replaces {@code Modifier.background(brush)} on the full-width box behind the card,
     * which draws a transparent-to-black gradient.
     */
    public static Modifier cardBrushBackground(Modifier modifier, Brush brush, Shape shape,
                                               float alpha, int defaultsMask, Object unused) {
        if (GlassworksSettings.isPillBarEnabled()) {
            return modifier;
        }
        return ComposeCompat.brushBackground(modifier, brush, shape, alpha, defaultsMask, unused);
    }

    /**
     * Replaces {@code Modifier.background(color, shape)} of the shared card.
     */
    public static Modifier cardBackground(Modifier modifier, long color, Shape shape) {
        switch (GlassworksSettings.getBarStyle()) {
            case GlassworksSettings.STYLE_TWO_PILLS:
                return modifier;
            case GlassworksSettings.STYLE_ONE_PILL:
                int side = GlassworksSettings.getSideMarginDp();
                return ComposeCompat.padding(modifier, side, 0f, side, GlassworksSettings.getLiftDp())
                        .then(FrostedPillElement.CARD);
            default:
                return ComposeCompat.background(modifier, color, shape);
        }
    }

    /**
     * Replaces {@code Modifier.clip(shape)} of the shared card. With two pills it is skipped so it
     * does not cut the pills' edges; with one pill it matches the pill's corner radius.
     */
    public static Modifier cardClip(Modifier modifier, Shape shape) {
        switch (GlassworksSettings.getBarStyle()) {
            case GlassworksSettings.STYLE_TWO_PILLS:
                return modifier;
            case GlassworksSettings.STYLE_ONE_PILL:
                Shape rounded = ComposeCompat.roundedCornerShape(getCardRadiusDp());
                return ClipKt.clip(modifier, rounded != null ? rounded : shape);
            default:
                return ClipKt.clip(modifier, shape);
        }
    }

    /**
     * Called when settings that only change how the glass is drawn (tint, blur, edge, shadow)
     * were changed, so the pills redraw without recreating the activity.
     */
    public static void onSettingsChanged() {
        ArtworkTint.update();
        FrostedPillNode.invalidateAll();
    }

    /** Corner radius of the one-pill card, in dp. */
    static float getCardRadiusDp() {
        return CARD_MAX_RADIUS_DP * GlassworksSettings.getRoundnessPercent() / 100f;
    }

    private static Modifier remember(Modifier wrapped) {
        WRAPPED.add(wrapped);
        return wrapped;
    }
}
