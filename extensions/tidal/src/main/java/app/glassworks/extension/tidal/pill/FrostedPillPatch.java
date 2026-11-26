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

    /** Artwork and play button size of the compact mini player (TIDAL: 44 and 40), in dp. */
    private static final float COMPACT_ITEM_DP = 36f;
    /**
     * Padding above and below the compact mini player row (TIDAL: 12 and 8), in dp. TIDAL's
     * play and next buttons are 44dp tall, so the compact row is 52dp.
     */
    private static final float COMPACT_ROW_PADDING_DP = 4f;

    /** TIDAL's padding of the mini player's title and artist column, in dp. */
    private static final float TEXT_START_DP = 12f;
    private static final float TEXT_END_DP = 16f;
    /** End padding of that column with a frosted style, in dp. */
    private static final float COMPACT_TEXT_END_DP = 4f;

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
        if (isCompactMiniPlayer()) {
            // The compact row has even padding, so it needs no centering.
            return remember(pill);
        }
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

    /**
     * Replaces the mini player artwork's corner shape (a {@code RoundedCornerShape}) with the
     * one chosen in settings, while a frosted style is used.
     */
    public static Object artworkShape(Object original) {
        if (!GlassworksSettings.isPillBarEnabled()) {
            return original;
        }
        Shape shape;
        switch (GlassworksSettings.getArtworkShape()) {
            case GlassworksSettings.ARTWORK_ROUNDED:
                shape = ComposeCompat.roundedCornerShape(getArtworkRadiusDp());
                break;
            case GlassworksSettings.ARTWORK_CIRCLE:
                shape = ComposeCompat.roundedCornerShapePercent(50);
                break;
            default:
                return original;
        }
        return shape != null ? shape : original;
    }

    /** Corner radius of the "Rounded" artwork shape: up to 12dp, following the pill's roundness. */
    static float getArtworkRadiusDp() {
        return 4f + 8f * GlassworksSettings.getRoundnessPercent() / 100f;
    }

    /**
     * Replaces {@code padding(start, top, end, bottom)} of the mini player row
     * (16, 12, 18, 8 in TIDAL 2.221.0). The compact size uses even, smaller vertical padding.
     */
    public static Modifier miniPlayerRowPadding(Modifier modifier, float start, float top, float end, float bottom) {
        if (isCompactMiniPlayer()) {
            top = COMPACT_ROW_PADDING_DP;
            bottom = COMPACT_ROW_PADDING_DP;
        }
        return ComposeCompat.paddingExact(modifier, start, top, end, bottom);
    }

    /**
     * Replaces {@code size(dp)} calls in the mini player row: the 44dp artwork and the 40dp
     * play button get smaller for the compact size; other sizes are unchanged.
     */
    public static Modifier miniPlayerSize(Modifier modifier, float sizeDp) {
        if (isCompactMiniPlayer() && sizeDp > COMPACT_ITEM_DP) {
            sizeDp = COMPACT_ITEM_DP;
        }
        return ComposeCompat.size(modifier, sizeDp);
    }

    /**
     * Replaces the play/pause button's icon button rank. TIDAL uses PRIMARY, a filled white
     * circle. While a frosted style is on, and unless the circle is turned on in settings,
     * TERTIARY is used instead: a plain icon with no background, like the next button.
     * (SECONDARY still draws a faint grey circle.)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object playButtonRank(Object rank) {
        if (!(rank instanceof Enum) || !GlassworksSettings.isPillBarEnabled()
                || GlassworksSettings.isPlayButtonCircle()) {
            return rank;
        }
        try {
            return Enum.valueOf(((Enum) rank).getDeclaringClass(), "TERTIARY");
        } catch (IllegalArgumentException e) {
            return rank;
        }
    }

    /**
     * Replaces {@code Modifier.background(color, shape)} in the mini player row: the artwork's
     * background, and the white circle behind the loading spinner, which is dropped with the
     * play button circle.
     */
    public static Modifier miniPlayerBackground(Modifier modifier, long color, Shape shape) {
        if (hidePlayButtonCircle() && shape != null && shape == ComposeCompat.circleShape()) {
            return modifier;
        }
        return ComposeCompat.background(modifier, color, shape);
    }

    /**
     * Replaces the mini player's loading spinner ({@code CircularProgressIndicator}). TIDAL draws
     * it dark on the white circle; without the circle it is white on a faint white track, like
     * the play button.
     */
    public static void miniPlayerSpinner(Modifier modifier, long color, float strokeWidth, long trackColor,
                                         int strokeCap, Object composer, int changed, int defaults) {
        if (hidePlayButtonCircle()) {
            color = ComposeCompat.color(0xFFFFFFFF);
            trackColor = ComposeCompat.color(0x33FFFFFF);
        }
        ComposeCompat.circularProgressIndicator(modifier, color, strokeWidth, trackColor, strokeCap,
                composer, changed, defaults);
    }

    /**
     * Replaces {@code padding$default(start, top, end, bottom)} calls in the mini player row.
     * The title and artist column has 12dp at the start and 16dp at the end. With a frosted style
     * the end is 4dp, so the text runs closer to the play button, and the start moves left by the
     * left fade's width; the text is padded back inside its marquee (see {@link MarqueeFade}).
     */
    public static Modifier miniPlayerPaddingDefault(Modifier modifier, float start, float top, float end,
                                                    float bottom, int defaults, Object unused) {
        if (GlassworksSettings.isPillBarEnabled() && start == TEXT_START_DP && end == TEXT_END_DP) {
            start = TEXT_START_DP - MarqueeFade.START_INSET_DP;
            end = COMPACT_TEXT_END_DP;
        }
        // Unset arguments are 0 in TIDAL's calls, so passing all four gives the same result.
        return ComposeCompat.paddingExact(modifier, start, top, end, bottom);
    }

    /** Replaces the mini player title's {@code basicMarquee()}, adding the edge fades. */
    public static Modifier miniPlayerTitleMarquee(Modifier modifier, int iterations, int animationMode,
                                                  int repeatDelay, int initialDelay, Object spacing,
                                                  float velocity, int defaults, Object unused) {
        return marquee(MarqueeFade.TITLE, modifier, iterations, animationMode, repeatDelay, initialDelay,
                spacing, velocity, defaults, unused);
    }

    /** Replaces the mini player artist's {@code basicMarquee()}, adding the edge fades. */
    public static Modifier miniPlayerSubtitleMarquee(Modifier modifier, int iterations, int animationMode,
                                                     int repeatDelay, int initialDelay, Object spacing,
                                                     float velocity, int defaults, Object unused) {
        return marquee(MarqueeFade.SUBTITLE, modifier, iterations, animationMode, repeatDelay, initialDelay,
                spacing, velocity, defaults, unused);
    }

    private static Modifier marquee(int line, Modifier modifier, int iterations, int animationMode,
                                    int repeatDelay, int initialDelay, Object spacing, float velocity,
                                    int defaults, Object unused) {
        Modifier marquee = ComposeCompat.basicMarquee(modifier, iterations, animationMode, repeatDelay,
                initialDelay, spacing, velocity, defaults, unused);
        if (!GlassworksSettings.isPillBarEnabled() || marquee == modifier) {
            return marquee;
        }
        return MarqueeFade.wrap(line, marquee);
    }

    private static boolean hidePlayButtonCircle() {
        return GlassworksSettings.isPillBarEnabled() && !GlassworksSettings.isPlayButtonCircle();
    }

    private static boolean isCompactMiniPlayer() {
        return GlassworksSettings.isPillBarEnabled() && GlassworksSettings.isCompactMiniPlayer();
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
