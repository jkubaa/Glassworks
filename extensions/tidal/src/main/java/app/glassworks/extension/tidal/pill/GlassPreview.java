package app.glassworks.extension.tidal.pill;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.view.View;

import app.glassworks.extension.tidal.settings.GlassworksSettings;

/**
 * A small mock of TIDAL's screen with the navigation bar drawn over it, for the settings page.
 * It uses the same {@link GlassPainter} as the real bar and reads the settings on every draw, so
 * it shows changes immediately. Call {@link #update()} after a change.
 */
public final class GlassPreview extends View {

    private static final int HEIGHT_DP = 220;
    private static final int[] TILE_COLORS = {
            0xFFE85D75, 0xFFF4A259, 0xFF33FFEE, 0xFF4F6BED, 0xFFF7D046, 0xFF9B5DE5,
            0xFF2EC4B6, 0xFFFF6B6B, 0xFF5E60CE,
    };

    private final float density;
    private final GlassPainter painter;
    private final RenderNode content = new RenderNode("GlassworksPreview");
    private final GlassPainter.Shape[] shapes = {new GlassPainter.Shape(), new GlassPainter.Shape()};
    private final GlassPainter.Shape[] miniShape = {new GlassPainter.Shape()};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private int effectVersion = -1;

    public GlassPreview(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        painter = new GlassPainter(density);
    }

    /** Redraws with the current settings. */
    public void update() {
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Math.round(HEIGHT_DP * density));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        recordContent(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        int version = GlassworksSettings.getVersion();
        if (version != effectVersion) {
            effectVersion = version;
            content.setRenderEffect(Backdrop.createEffect(density));
        }

        float corner = 16f * density;
        clip.reset();
        clip.addRoundRect(0f, 0f, width, height, corner, corner, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(clip);

        // The sharp content, then the bar on top.
        drawTiles(canvas, width, height);
        drawBar(canvas, width, height);

        canvas.restoreToCount(save);

        if (GlassworksSettings.getTintColorIndex() == GlassworksSettings.TINT_ALBUM_ART && ArtworkTint.isFading()) {
            postInvalidateOnAnimation();
        }
    }

    private void drawBar(Canvas canvas, int width, int height) {
        int style = GlassworksSettings.getBarStyle();
        float side = (12f + GlassworksSettings.getSideMarginDp()) * density;
        float bottom = height - (10f + GlassworksSettings.getLiftDp()) * density;
        float tabHeight = 50f * density;
        float miniHeight = (GlassworksSettings.isCompactMiniPlayer() ? 46f : 56f) * density;
        float roundness = GlassworksSettings.getRoundnessPercent() / 100f;
        boolean albumArt = GlassworksSettings.getTintColorIndex() == GlassworksSettings.TINT_ALBUM_ART;
        int tint = albumArt ? ArtworkTint.currentRgb() : GlassworksSettings.getTintRgb();

        float tabTop = bottom - tabHeight;
        float left = side;
        float right = width - side;

        int tabs = visibleTabCount();
        String separate = GlassworksSettings.getSeparateTab();
        boolean split = style == GlassworksSettings.STYLE_TWO_PILLS && !separate.isEmpty()
                && !GlassworksSettings.isTabHidden(separate) && tabs > 1;

        if (style == GlassworksSettings.STYLE_TWO_PILLS) {
            float gap = GlassworksSettings.getPillGapDp() * density;
            float miniTop = tabTop - gap - miniHeight;
            miniShape[0].set(left, miniTop, right, miniTop + miniHeight, roundness * miniHeight / 2f);
            painter.draw(canvas, miniShape, 1, content, 0f, 0f, tint);
            drawMiniPlayer(canvas, left, miniTop, right, miniHeight);

            int count;
            if (split) {
                float circleLeft = right - tabHeight;
                shapes[0].set(left, tabTop, circleLeft - gap, bottom, roundness * tabHeight / 2f);
                shapes[1].set(circleLeft, tabTop, right, bottom, roundness * tabHeight / 2f);
                count = 2;
            } else {
                shapes[0].set(left, tabTop, right, bottom, roundness * tabHeight / 2f);
                count = 1;
            }
            painter.draw(canvas, shapes, count, content, 0f, 0f, tint);
            if (split) {
                drawTabs(canvas, left, shapes[0].right, tabTop, tabHeight, tabs - 1, true);
                drawTabs(canvas, shapes[1].left, right, tabTop, tabHeight, 1, false);
            } else {
                drawTabs(canvas, left, right, tabTop, tabHeight, tabs, true);
            }
        } else {
            float cardTop = tabTop - miniHeight;
            if (style == GlassworksSettings.STYLE_ONE_PILL) {
                shapes[0].set(left, cardTop, right, bottom, FrostedPillPatch.getCardRadiusDp() * density);
                painter.draw(canvas, shapes, 1, content, 0f, 0f, tint);
            } else {
                // TIDAL's original solid card.
                paint.setShader(null);
                paint.setColor(0xFF1C1C1E);
                float radius = 16f * density;
                canvas.drawRoundRect(12f * density, cardTop, width - 12f * density, height - 10f * density,
                        radius, radius, paint);
                left = 12f * density;
                right = width - 12f * density;
            }
            drawMiniPlayer(canvas, left, cardTop, right, miniHeight);
            drawTabs(canvas, left, right, tabTop, tabHeight, tabs, true);
        }
    }

    private void drawMiniPlayer(Canvas canvas, float left, float top, float right, float height) {
        float pad = 10f * density;
        float art = height - 2 * pad;
        paint.setColor(0xFFE85D75);
        float artRadius;
        int artShape = GlassworksSettings.isPillBarEnabled()
                ? GlassworksSettings.getArtworkShape() : GlassworksSettings.ARTWORK_TIDAL;
        if (artShape == GlassworksSettings.ARTWORK_CIRCLE) {
            artRadius = art / 2f;
        } else if (artShape == GlassworksSettings.ARTWORK_ROUNDED) {
            // Scaled from the real 44dp artwork to the preview's size.
            artRadius = FrostedPillPatch.getArtworkRadiusDp() * density * art / (44f * density);
        } else {
            artRadius = 4 * density;
        }
        canvas.drawRoundRect(left + pad + 4 * density, top + pad, left + pad + 4 * density + art, top + pad + art,
                artRadius, artRadius, paint);
        float textLeft = left + pad + art + 18f * density;
        paint.setColor(0xFFFFFFFF);
        canvas.drawRoundRect(textLeft, top + height * 0.32f, textLeft + 90f * density, top + height * 0.32f + 7f * density,
                4 * density, 4 * density, paint);
        paint.setColor(0x99FFFFFF);
        canvas.drawRoundRect(textLeft, top + height * 0.56f, textLeft + 60f * density, top + height * 0.56f + 6f * density,
                3 * density, 3 * density, paint);
        float playX = right - pad - 40f * density;
        float centerY = top + height / 2f;
        boolean circle = !GlassworksSettings.isPillBarEnabled() || GlassworksSettings.isPlayButtonCircle();
        if (circle) {
            paint.setColor(0xFFFFFFFF);
            canvas.drawCircle(playX, centerY, 15f * density, paint);
        }
        // A play triangle, black on the circle or white without it.
        Path play = new Path();
        float s = 7f * density;
        play.moveTo(playX - s * 0.6f, centerY - s);
        play.lineTo(playX + s, centerY);
        play.lineTo(playX - s * 0.6f, centerY + s);
        play.close();
        paint.setColor(circle ? 0xFF000000 : 0xFFFFFFFF);
        canvas.drawPath(play, paint);
    }

    private void drawTabs(Canvas canvas, float left, float right, float top, float height, int count,
                          boolean firstSelected) {
        if (count <= 0) {
            return;
        }
        float slot = (right - left) / count;
        for (int i = 0; i < count; i++) {
            float cx = left + slot * (i + 0.5f);
            float cy = top + height / 2f;
            if (i == 0 && firstSelected) {
                paint.setColor(0x26FFFFFF);
                float half = Math.min(slot / 2f - 4f * density, height / 2f - 4f * density);
                canvas.drawRoundRect(cx - half * 1.4f, cy - half, cx + half * 1.4f, cy + half, half, half, paint);
            }
            paint.setColor(i == 0 && firstSelected ? 0xFFFFFFFF : 0x99FFFFFF);
            canvas.drawCircle(cx, cy, 6f * density, paint);
        }
    }

    private static int visibleTabCount() {
        int count = 0;
        for (String name : GlassworksSettings.TAB_NAMES) {
            if (!GlassworksSettings.isTabHidden(name)) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    /** Colorful album-like tiles, recorded once into the node the glass blurs. */
    private void recordContent(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        content.setPosition(0, 0, width, height);
        RecordingCanvas recording = content.beginRecording(width, height);
        try {
            drawTiles(recording, width, height);
        } finally {
            content.endRecording();
        }
    }

    private void drawTiles(Canvas canvas, int width, int height) {
        canvas.drawColor(0xFF000000);
        float pad = 12f * density;
        float size = (width - 4 * pad) / 3f;
        Paint tile = new Paint(Paint.ANTI_ALIAS_FLAG);
        int index = 0;
        for (float top = pad - size * 0.35f; top < height; top += size + pad) {
            for (int column = 0; column < 3; column++) {
                float left = pad + column * (size + pad);
                tile.setColor(TILE_COLORS[index++ % TILE_COLORS.length]);
                canvas.drawRoundRect(left, top, left + size, top + size, 6 * density, 6 * density, tile);
                // A lighter stripe, so the blur and lens have detail to show.
                tile.setColor(0x66FFFFFF);
                canvas.drawRect(left, top + size * 0.62f, left + size, top + size * 0.7f, tile);
            }
        }
    }
}
