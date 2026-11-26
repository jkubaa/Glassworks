package app.glassworks.extension.tidal.pill;

import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.compose.ui.Modifier;
import androidx.compose.ui.geometry.Rect;
import androidx.compose.ui.graphics.AndroidCanvas_androidKt;
import androidx.compose.ui.graphics.drawscope.ContentDrawScope;
import androidx.compose.ui.layout.LayoutCoordinates;
import androidx.compose.ui.layout.LayoutCoordinatesKt;
import androidx.compose.ui.node.DelegatableNodeKt;
import androidx.compose.ui.node.DelegatableNode_androidKt;
import androidx.compose.ui.node.DrawModifierNode;
import androidx.compose.ui.node.DrawModifierNodeKt;
import androidx.compose.ui.node.GlobalPositionAwareModifierNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import app.glassworks.extension.tidal.settings.GlassworksSettings;

/**
 * Draws a rounded glass pill behind the content it modifies (see {@link GlassPainter}).
 * <p>
 * A tab bar pill with a separate tab is drawn as two shapes: the remaining tabs on the left
 * and a round pill on the right, sized to the bar height (see {@link TabBarHook}).
 * <p>
 * Only Compose APIs without Kotlin lambda or Unit types in their JVM signature are used,
 * because TIDAL's R8 renamed those Kotlin classes.
 */
final class FrostedPillNode extends Modifier.Node
        implements DrawModifierNode, GlobalPositionAwareModifierNode {

    /** Attached nodes, so they can be redrawn when the tint or settings change. */
    private static final Set<FrostedPillNode> NODES =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final int kind;
    private final int[] hostLocation = new int[2];
    private final int[] sourceLocation = new int[2];
    private final GlassPainter.Shape[] shapes = {new GlassPainter.Shape(), new GlassPainter.Shape()};
    private final Runnable redraw = this::redraw;

    private View host;
    private Backdrop backdrop;
    private GlassPainter painter;
    private float density = 1f;

    FrostedPillNode(int kind) {
        this.kind = kind;
    }

    /** Redraws every pill, such as after a settings change or a new artwork color. */
    static void invalidateAll() {
        MAIN.post(() -> {
            List<FrostedPillNode> nodes;
            synchronized (NODES) {
                nodes = new ArrayList<>(NODES);
            }
            for (FrostedPillNode node : nodes) {
                node.redraw();
            }
        });
    }

    @Override
    public void onAttach() {
        host = DelegatableNode_androidKt.requireView(this);
        backdrop = Backdrop.acquire(host);
        density = host.getResources().getDisplayMetrics().density;
        painter = new GlassPainter(density);
        NODES.add(this);
    }

    @Override
    public void onDetach() {
        NODES.remove(this);
        if (backdrop != null) {
            backdrop.release();
            backdrop = null;
        }
        if (host != null) {
            host.removeCallbacks(redraw);
        }
        host = null;
    }

    private void redraw() {
        if (isAttached()) {
            DrawModifierNodeKt.invalidateDraw(this);
        }
    }

    @Override
    public void onGloballyPositioned(LayoutCoordinates coordinates) {
        // The blur offset depends on where the pill is in the window, so redraw when it moves.
        DrawModifierNodeKt.invalidateDraw(this);
    }

    @Override
    public void draw(ContentDrawScope scope) {
        Canvas canvas = AndroidCanvas_androidKt.getNativeCanvas(scope.getDrawContext().getCanvas());

        // Unclipped bounds of this pill, relative to the ComposeView.
        LayoutCoordinates coordinates = DelegatableNodeKt.requireLayoutCoordinates(this);
        LayoutCoordinates root = LayoutCoordinatesKt.findRootCoordinates(coordinates);
        Rect bounds = root.localBoundingBoxOf(coordinates, false);
        float width = bounds.getWidth();
        float height = bounds.getHeight();

        if (width <= 0f || height <= 0f || painter == null) {
            scope.drawContent();
            return;
        }

        int count = buildShapes(width, height);

        float dx = 0f;
        float dy = 0f;
        boolean hasBackdrop = host != null && backdrop != null && backdrop.isReady();
        if (hasBackdrop) {
            host.getLocationInWindow(hostLocation);
            backdrop.getSource().getLocationInWindow(sourceLocation);
            // Backdrop origin in the window, minus the pill's origin in the window.
            dx = sourceLocation[0] - (hostLocation[0] + bounds.getLeft());
            dy = sourceLocation[1] - (hostLocation[1] + bounds.getTop());
        }

        boolean albumArt = GlassworksSettings.getTintColorIndex() == GlassworksSettings.TINT_ALBUM_ART;
        int tint = albumArt ? ArtworkTint.currentRgb() : GlassworksSettings.getTintRgb();
        painter.draw(canvas, shapes, count, hasBackdrop ? backdrop.getNode() : null, dx, dy, tint);

        scope.drawContent();

        if (albumArt && ArtworkTint.isFading() && host != null) {
            host.removeCallbacks(redraw);
            host.postOnAnimation(redraw);
        }
    }

    /** @return The number of shapes: 2 for a split tab bar, otherwise 1. */
    private int buildShapes(float width, float height) {
        float roundness = GlassworksSettings.getRoundnessPercent() / 100f;
        float radius = kind == FrostedPillElement.KIND_CARD
                ? FrostedPillPatch.getCardRadiusDp() * density
                : roundness * Math.min(width, height) / 2f;

        if (kind == FrostedPillElement.KIND_TAB_BAR && TabBarHook.isSplitActive() && width > height * 2) {
            // Left pill for the other tabs, round pill on the right for the separate tab.
            float gap = TabBarHook.getSplitGapDp() * density;
            float circleLeft = width - height;
            shapes[0].set(0f, 0f, circleLeft - gap, height, radius);
            shapes[1].set(circleLeft, 0f, width, height, roundness * height / 2f);
            return 2;
        }
        shapes[0].set(0f, 0f, width, height, radius);
        return 1;
    }
}
