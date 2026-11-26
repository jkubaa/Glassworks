package app.glassworks.extension.tidal.pill;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;

import app.glassworks.extension.tidal.settings.GlassworksSettings;

/**
 * Draws glass shapes: a soft shadow, the blurred content behind them, a tint, and an edge.
 * Used by the pills in TIDAL's bar and by the preview on the settings page.
 * <p>
 * The "Liquid" glass style adds a sheen at the top, a rim that is brighter at the top and bottom,
 * and on Android 13+ bends the blurred content near the edges like a lens.
 */
final class GlassPainter {

    /** One rounded rectangle, in the canvas's coordinates. */
    static final class Shape {
        float left;
        float top;
        float right;
        float bottom;
        float radius;

        void set(float left, float top, float right, float bottom, float radius) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.radius = Math.min(radius, Math.min(right - left, bottom - top) / 2f);
        }

        float width() {
            return right - left;
        }

        float height() {
            return bottom - top;
        }
    }

    /** Minimum tint opacity when no blur can be drawn, such as on a software canvas. */
    private static final int MIN_ALPHA_WITHOUT_BLUR = 0xF2;
    /** Hairline edge of the frosted style (white at 14%). */
    private static final int FROSTED_EDGE_COLOR = 0x24FFFFFF;

    /**
     * Bends the content within {@code band} pixels of the shape's edge outward, so content just
     * outside the glass shows near its rim, as through a thick lens.
     */
    private static final String LENS_SHADER =
            "uniform shader content;\n"
                    + "uniform float2 size;\n"
                    + "uniform float margin;\n"
                    + "uniform float radius;\n"
                    + "uniform float band;\n"
                    + "uniform float strength;\n"
                    + "float sdf(float2 p) {\n"
                    + "  float2 c = size * 0.5;\n"
                    + "  float2 q = abs(p - c) - (c - radius);\n"
                    + "  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;\n"
                    + "}\n"
                    + "half4 main(float2 coord) {\n"
                    + "  float2 p = coord - margin;\n"
                    + "  float d = sdf(p);\n"
                    + "  if (d > 0.0 || d < -band) { return content.eval(coord); }\n"
                    + "  float2 e = float2(1.0, 0.0);\n"
                    + "  float2 n = float2(sdf(p + e.xy) - sdf(p - e.xy), sdf(p + e.yx) - sdf(p - e.yx));\n"
                    + "  n = n / max(length(n), 0.0001);\n"
                    + "  float t = 1.0 + d / band;\n"
                    + "  return content.eval(coord + n * strength * t * t * t);\n"
                    + "}\n";

    private final float density;
    private final Path path = new Path();
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RenderNode[] lensNodes = new RenderNode[2];
    private RuntimeShader lensShader;

    private float gradientTop = Float.NaN;
    private float gradientBottom = Float.NaN;

    GlassPainter(float density) {
        this.density = density;
        tintPaint.setStyle(Paint.Style.FILL);
        sheenPaint.setStyle(Paint.Style.FILL);
        edgePaint.setStyle(Paint.Style.STROKE);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(0xFF000000);
    }

    /**
     * @param backdrop The blurred content, or null if there is none.
     * @param dx       X of the backdrop's origin in this canvas.
     * @param dy       Y of the backdrop's origin in this canvas.
     * @param tintRgb  Tint color without alpha.
     */
    void draw(Canvas canvas, Shape[] shapes, int count, RenderNode backdrop, float dx, float dy, int tintRgb) {
        path.reset();
        for (int i = 0; i < count; i++) {
            Shape s = shapes[i];
            path.addRoundRect(s.left, s.top, s.right, s.bottom, s.radius, s.radius, Path.Direction.CW);
        }
        boolean liquid = GlassworksSettings.getGlassStyle() == GlassworksSettings.GLASS_LIQUID;

        drawShadow(canvas, shapes, count);

        boolean blurred = backdrop != null && canvas.isHardwareAccelerated()
                && drawBackdrop(canvas, shapes, count, backdrop, dx, dy, liquid);

        int alpha = Math.round(GlassworksSettings.getTintPercent() * 2.55f);
        if (!blurred) {
            alpha = Math.max(alpha, MIN_ALPHA_WITHOUT_BLUR);
        }
        tintPaint.setColor((alpha << 24) | (tintRgb & 0xFFFFFF));
        canvas.drawPath(path, tintPaint);

        if (liquid) {
            updateGradients(shapes[0].top, shapes[0].bottom);
            canvas.drawPath(path, sheenPaint);
        }

        if (GlassworksSettings.isEdgeHighlightEnabled()) {
            if (liquid) {
                updateGradients(shapes[0].top, shapes[0].bottom);
                edgePaint.setStrokeWidth(1.2f * density);
            } else {
                edgePaint.setShader(null);
                edgePaint.setColor(FROSTED_EDGE_COLOR);
                edgePaint.setStrokeWidth(density);
                gradientTop = Float.NaN;
            }
            canvas.drawPath(path, edgePaint);
        }
    }

    /** A soft shadow around the shapes, clipped so it never darkens the glass itself. */
    private void drawShadow(Canvas canvas, Shape[] shapes, int count) {
        int percent = GlassworksSettings.getShadowPercent();
        if (percent <= 0) {
            return;
        }
        int alpha = Math.round(percent * 1.6f);
        shadowPaint.setShadowLayer(14f * density, 0f, 4f * density, alpha << 24);
        int save = canvas.save();
        try {
            canvas.clipOutPath(path);
            for (int i = 0; i < count; i++) {
                Shape s = shapes[i];
                canvas.drawRoundRect(s.left, s.top, s.right, s.bottom, s.radius, s.radius, shadowPaint);
            }
        } finally {
            canvas.restoreToCount(save);
        }
    }

    private boolean drawBackdrop(Canvas canvas, Shape[] shapes, int count, RenderNode backdrop,
                                 float dx, float dy, boolean liquid) {
        if (liquid && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            for (int i = 0; i < count; i++) {
                drawLens(canvas, i, shapes[i], backdrop, dx, dy);
            }
            return true;
        }

        int save = canvas.save();
        try {
            canvas.clipPath(path);
            canvas.translate(dx, dy);
            canvas.drawRenderNode(backdrop);
        } finally {
            canvas.restoreToCount(save);
        }
        return true;
    }

    /** Draws the backdrop behind one shape through the lens shader. */
    private void drawLens(Canvas canvas, int index, Shape shape, RenderNode backdrop, float dx, float dy) {
        if (lensShader == null) {
            lensShader = new RuntimeShader(LENS_SHADER);
        }
        if (lensNodes[index] == null) {
            lensNodes[index] = new RenderNode("GlassworksLens" + index);
        }
        RenderNode lens = lensNodes[index];

        float width = shape.width();
        float height = shape.height();
        float strength = Math.min(10f * density, height * 0.2f);
        float band = Math.min(height * 0.4f, 22f * density);
        float margin = (float) Math.ceil(strength + 2f);

        int nodeWidth = (int) Math.ceil(width + 2 * margin);
        int nodeHeight = (int) Math.ceil(height + 2 * margin);
        lens.setPosition(0, 0, nodeWidth, nodeHeight);
        RecordingCanvas recording = lens.beginRecording(nodeWidth, nodeHeight);
        try {
            recording.translate(dx - shape.left + margin, dy - shape.top + margin);
            recording.drawRenderNode(backdrop);
        } finally {
            lens.endRecording();
        }

        lensShader.setFloatUniform("size", width, height);
        lensShader.setFloatUniform("margin", margin);
        lensShader.setFloatUniform("radius", shape.radius);
        lensShader.setFloatUniform("band", band);
        lensShader.setFloatUniform("strength", strength);
        lens.setRenderEffect(RenderEffect.createRuntimeShaderEffect(lensShader, "content"));

        int save = canvas.save();
        try {
            canvas.clipPath(path);
            canvas.translate(shape.left - margin, shape.top - margin);
            canvas.drawRenderNode(lens);
        } finally {
            canvas.restoreToCount(save);
        }
    }

    /** Sheen and rim gradients of the liquid style, for shapes spanning top to bottom. */
    private void updateGradients(float top, float bottom) {
        if (top == gradientTop && bottom == gradientBottom && edgePaint.getShader() != null) {
            return;
        }
        gradientTop = top;
        gradientBottom = bottom;
        float height = bottom - top;
        sheenPaint.setShader(new LinearGradient(0f, top, 0f, top + height * 0.55f,
                0x24FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        edgePaint.setColor(0xFFFFFFFF);
        edgePaint.setShader(new LinearGradient(0f, top, 0f, bottom,
                new int[]{0x99FFFFFF, 0x1AFFFFFF, 0x14FFFFFF, 0x4DFFFFFF},
                new float[]{0f, 0.35f, 0.65f, 1f}, Shader.TileMode.CLAMP));
    }
}
