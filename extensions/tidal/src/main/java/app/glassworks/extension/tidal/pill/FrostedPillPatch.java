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
 * TIDAL draws the mini player and the tab bar on one shared rounded card. With the pill bar
 * enabled, the card's background, clip and black gradient are skipped, and the mini player and
 * the tab bar each get their own frosted pill. With it disabled, every call does exactly what
 * TIDAL's original code did.
 */
@SuppressWarnings("unused")
public final class FrostedPillPatch {

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
        if (!GlassworksSettings.isPillBarEnabled() || modifier == null || WRAPPED.contains(modifier)) {
            return modifier;
        }
        return remember(modifier.then(FrostedPillElement.INSTANCE));
    }

    /**
     * Injected before AppScaffold's mini player {@code AnimatedVisibility} call, wrapping its
     * modifier. The gap is padding below the pill, so it only exists while the mini player shows.
     */
    public static Modifier miniPlayerModifier(Modifier modifier) {
        if (!GlassworksSettings.isPillBarEnabled() || modifier == null || WRAPPED.contains(modifier)) {
            return modifier;
        }
        Modifier withGap = ComposeCompat.paddingBottom(modifier, GlassworksSettings.getPillGapDp());
        return remember(withGap.then(FrostedPillElement.INSTANCE));
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
        if (GlassworksSettings.isPillBarEnabled()) {
            return modifier;
        }
        return ComposeCompat.background(modifier, color, shape);
    }

    /**
     * Replaces {@code Modifier.clip(shape)} of the shared card, so it does not cut the pills' edges.
     */
    public static Modifier cardClip(Modifier modifier, Shape shape) {
        if (GlassworksSettings.isPillBarEnabled()) {
            return modifier;
        }
        return ClipKt.clip(modifier, shape);
    }

    private static Modifier remember(Modifier wrapped) {
        WRAPPED.add(wrapped);
        return wrapped;
    }
}
