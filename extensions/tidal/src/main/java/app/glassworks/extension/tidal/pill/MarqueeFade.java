package app.glassworks.extension.tidal.pill;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;

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
import androidx.compose.ui.node.ModifierNodeElement;

import java.lang.ref.WeakReference;

/**
 * Fades both edges of the mini player's scrolling title and artist, like Apple Music.
 * <p>
 * TIDAL scrolls each text with {@code Modifier.basicMarquee()}, which measures the text at its
 * full width and shows it through a window of the available width. Around that, a
 * {@link FadeNode} outside the marquee knows the window's width, and a {@link ProbeNode} inside it
 * knows the text's full width. The fade is only drawn while the text is wider than the window,
 * so short titles are not faded.
 * <p>
 * The left fade only shows while the text has scrolled away from its start: when the marquee
 * pauses at the start between loops, the first letters are not faded. The scroll position is
 * read from the marquee node next to the fade node.
 * <p>
 * One fade and probe pair is kept per text line ({@link #TITLE}, {@link #SUBTITLE}).
 */
final class MarqueeFade {

    static final int TITLE = 0;
    static final int SUBTITLE = 1;

    /** Width of each fade, in dp. */
    private static final float FADE_DP = 14f;
    private static final String MARQUEE_NODE = "androidx.compose.foundation.MarqueeModifierNode";

    private static java.lang.reflect.Field childField;
    private static java.lang.reflect.Field offsetField;
    private static java.lang.reflect.Method animatableValue;
    private static boolean reflectionFailed;

    private static final float[] windowWidth = new float[2];
    private static final float[] textWidth = new float[2];
    @SuppressWarnings("unchecked")
    private static final WeakReference<FadeNode>[] fades = new WeakReference[]{
            new WeakReference<>(null), new WeakReference<>(null)};

    private MarqueeFade() {
    }

    /** Wraps a marquee modifier of the given line: fade outside it, width probe inside it. */
    static Modifier wrap(int line, Modifier marquee) {
        return new FadeElement(line).then(marquee).then(new ProbeElement(line));
    }

    private static boolean overflows(int line) {
        return textWidth[line] > windowWidth[line] + 1f;
    }

    /** Unclipped bounds of the node's layout, relative to the root. */
    private static Rect bounds(Modifier.Node node) {
        LayoutCoordinates coordinates = DelegatableNodeKt.requireLayoutCoordinates(node);
        LayoutCoordinates root = LayoutCoordinatesKt.findRootCoordinates(coordinates);
        return root.localBoundingBoxOf(coordinates, false);
    }

    static final class FadeNode extends Modifier.Node implements DrawModifierNode {
        private final int line;
        private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float shaderWidth = -1f;
        private int shaderLeftAlpha = -1;

        FadeNode(int line) {
            this.line = line;
            maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        }

        @Override
        public void onAttach() {
            fades[line] = new WeakReference<>(this);
        }

        @Override
        public void draw(ContentDrawScope scope) {
            Rect bounds = bounds(this);
            float width = bounds.getWidth();
            float height = bounds.getHeight();
            windowWidth[line] = width;
            if (!overflows(line) || width <= 0f || height <= 0f) {
                scope.drawContent();
                return;
            }

            Canvas canvas = AndroidCanvas_androidKt.getNativeCanvas(scope.getDrawContext().getCanvas());
            // Some room above and below for glyphs that reach outside the line.
            float top = -height / 2f;
            float bottom = height * 1.5f;
            int save = canvas.saveLayer(0f, top, width, bottom, null);
            try {
                scope.drawContent();
                float fadePx = Math.min(FADE_DP * density(), width / 4f);
                // Fully opaque at the left edge while the text is at its start, fading in as it
                // scrolls the first fadePx pixels.
                float scrolled = Math.min(1f, Math.abs(scrollOffset()) / fadePx);
                int leftAlpha = Math.round(255 * (1f - scrolled));
                if (shaderWidth != width || shaderLeftAlpha != leftAlpha) {
                    shaderWidth = width;
                    shaderLeftAlpha = leftAlpha;
                    float fade = fadePx / width;
                    maskPaint.setShader(new LinearGradient(0f, 0f, width, 0f,
                            new int[]{leftAlpha << 24, 0xFF000000, 0xFF000000, 0x00000000},
                            new float[]{0f, fade, 1f - fade, 1f}, Shader.TileMode.CLAMP));
                }
                canvas.drawRect(0f, top, width, bottom, maskPaint);
            } finally {
                canvas.restoreToCount(save);
            }
        }

        /** The marquee's scroll offset in pixels (0 at the start), or 0 if it cannot be read. */
        private float scrollOffset() {
            if (reflectionFailed) {
                return 0f;
            }
            try {
                if (childField == null) {
                    childField = Modifier.Node.class.getDeclaredField("child");
                    childField.setAccessible(true);
                }
                Object node = childField.get(this);
                for (int i = 0; node != null && i < 8; i++) {
                    if (node.getClass().getName().equals(MARQUEE_NODE)) {
                        if (offsetField == null) {
                            offsetField = node.getClass().getDeclaredField("offset");
                            offsetField.setAccessible(true);
                        }
                        Object animatable = offsetField.get(node);
                        if (animatableValue == null) {
                            animatableValue = animatable.getClass().getMethod("getValue");
                        }
                        Object value = animatableValue.invoke(animatable);
                        return value instanceof Float ? (Float) value : 0f;
                    }
                    node = childField.get(node);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                android.util.Log.w("Glassworks", "Cannot read the marquee position", e);
                reflectionFailed = true;
            }
            return 0f;
        }

        private float density() {
            return DelegatableNode_androidKt.requireView(this).getResources().getDisplayMetrics().density;
        }

        void redraw() {
            if (isAttached()) {
                DrawModifierNodeKt.invalidateDraw(this);
            }
        }
    }

    static final class ProbeNode extends Modifier.Node implements GlobalPositionAwareModifierNode {
        private final int line;

        ProbeNode(int line) {
            this.line = line;
        }

        @Override
        public void onGloballyPositioned(LayoutCoordinates coordinates) {
            boolean before = overflows(line);
            textWidth[line] = bounds(this).getWidth();
            if (before != overflows(line)) {
                FadeNode fade = fades[line].get();
                if (fade != null) {
                    fade.redraw();
                }
            }
        }
    }

    static final class FadeElement extends ModifierNodeElement<FadeNode> {
        private final int line;

        FadeElement(int line) {
            this.line = line;
        }

        @Override
        public FadeNode create() {
            return new FadeNode(line);
        }

        @Override
        public void update(FadeNode node) {
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof FadeElement && ((FadeElement) other).line == line;
        }

        @Override
        public int hashCode() {
            return 0x6A0E + line;
        }
    }

    static final class ProbeElement extends ModifierNodeElement<ProbeNode> {
        private final int line;

        ProbeElement(int line) {
            this.line = line;
        }

        @Override
        public ProbeNode create() {
            return new ProbeNode(line);
        }

        @Override
        public void update(ProbeNode node) {
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ProbeElement && ((ProbeElement) other).line == line;
        }

        @Override
        public int hashCode() {
            return 0x7B0E + line;
        }
    }
}
