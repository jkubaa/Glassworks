package app.glassworks.extension.tidal.pill;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
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

import app.glassworks.extension.tidal.settings.GlassworksSettings;

/**
 * Draws a rounded, frosted-glass pill behind the content it modifies:
 * the blurred screen content behind it, a translucent tint and a hairline edge.
 * <p>
 * A tab bar pill with a separate tab is drawn as two shapes: the remaining tabs on the left
 * and a round pill on the right, sized to the bar height (see {@link TabBarHook}).
 * <p>
 * Only Compose APIs without Kotlin lambda or Unit types in their JVM signature are used,
 * because TIDAL's R8 renamed those Kotlin classes.
 */
final class FrostedPillNode extends Modifier.Node
        implements DrawModifierNode, GlobalPositionAwareModifierNode {

    /** Minimum tint opacity when no blur can be drawn, such as on a software canvas. */
    private static final int MIN_ALPHA_WITHOUT_BLUR = 0xF2;
    /** Hairline edge highlight (white at 14%). */
    private static final int EDGE_COLOR = 0x24FFFFFF;

    private final Path path = new Path();
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] hostLocation = new int[2];
    private final int[] sourceLocation = new int[2];

    private final int kind;

    private View host;
    private Backdrop backdrop;
    private float density = 1f;

    FrostedPillNode(int kind) {
        this.kind = kind;
        tintPaint.setStyle(Paint.Style.FILL);
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setColor(EDGE_COLOR);
    }

    @Override
    public void onAttach() {
        host = DelegatableNode_androidKt.requireView(this);
        backdrop = Backdrop.acquire(host);
        density = host.getResources().getDisplayMetrics().density;
        edgePaint.setStrokeWidth(density);
    }

    @Override
    public void onDetach() {
        if (backdrop != null) {
            backdrop.release();
            backdrop = null;
        }
        host = null;
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

        if (width <= 0f || height <= 0f) {
            scope.drawContent();
            return;
        }

        float roundness = GlassworksSettings.getRoundnessPercent() / 100f;
        float radius = kind == FrostedPillElement.KIND_CARD
                ? Math.min(FrostedPillPatch.getCardRadiusDp() * density, Math.min(width, height) / 2f)
                : roundness * Math.min(width, height) / 2f;
        path.reset();
        if (kind == FrostedPillElement.KIND_TAB_BAR && TabBarHook.isSplitActive() && width > height * 2) {
            // Left pill for the other tabs, round pill on the right for the separate tab.
            float gap = TabBarHook.getSplitGapDp() * density;
            float circleLeft = width - height;
            float circleRadius = roundness * height / 2f;
            path.addRoundRect(0f, 0f, circleLeft - gap, height, radius, radius, Path.Direction.CW);
            path.addRoundRect(circleLeft, 0f, width, height, circleRadius, circleRadius, Path.Direction.CW);
        } else {
            path.addRoundRect(0f, 0f, width, height, radius, radius, Path.Direction.CW);
        }

        boolean blurred = drawBackdrop(canvas, bounds);

        int alpha = Math.round(GlassworksSettings.getTintPercent() * 2.55f);
        if (!blurred) {
            alpha = Math.max(alpha, MIN_ALPHA_WITHOUT_BLUR);
        }
        tintPaint.setColor((alpha << 24) | GlassworksSettings.getTintRgb());
        canvas.drawPath(path, tintPaint);

        if (GlassworksSettings.isEdgeHighlightEnabled()) {
            canvas.drawPath(path, edgePaint);
        }

        scope.drawContent();
    }

    /**
     * Draws the blurred content behind the pill, clipped to the pill shape.
     *
     * @return whether the blur was drawn.
     */
    private boolean drawBackdrop(Canvas canvas, Rect bounds) {
        if (host == null || backdrop == null || !backdrop.isReady() || !canvas.isHardwareAccelerated()) {
            return false;
        }

        host.getLocationInWindow(hostLocation);
        backdrop.getSource().getLocationInWindow(sourceLocation);

        // Pill origin in the window, minus the backdrop's origin in the window.
        float dx = sourceLocation[0] - (hostLocation[0] + bounds.getLeft());
        float dy = sourceLocation[1] - (hostLocation[1] + bounds.getTop());

        int save = canvas.save();
        try {
            canvas.clipPath(path);
            canvas.translate(dx, dy);
            canvas.drawRenderNode(backdrop.getNode());
        } finally {
            canvas.restoreToCount(save);
        }
        return true;
    }
}
