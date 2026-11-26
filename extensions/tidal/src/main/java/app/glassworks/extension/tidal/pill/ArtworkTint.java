package app.glassworks.extension.tidal.pill;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadata;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import app.glassworks.extension.tidal.settings.GlassworksSettings;

/**
 * The glass tint taken from the current track's artwork, for the "Album art" tint color.
 * <p>
 * The color is picked on a background thread from a small copy of the artwork: the most common
 * colorful hue, darkened so white icons stay readable. When the track changes, the tint fades
 * from the old color to the new one.
 */
public final class ArtworkTint {

    private static final String TAG = "Glassworks";

    /** Used before any artwork is known (Graphite). */
    static final int FALLBACK_RGB = GlassworksSettings.TINT_COLORS[0];
    private static final long FADE_MS = 450;
    private static final int SAMPLE_SIZE = 32;
    private static final int HUE_BINS = 12;

    private static volatile int fromRgb = FALLBACK_RGB;
    private static volatile int toRgb = FALLBACK_RGB;
    private static volatile long fadeStart;

    private static volatile Bitmap latestArtwork;
    private static Bitmap pickedArtwork;
    private static Handler worker;

    private ArtworkTint() {
    }

    /** Called with every metadata update of TIDAL's media session. */
    public static void onMetadata(MediaMetadata metadata) {
        if (metadata == null) {
            return;
        }
        Bitmap artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (artwork == null) {
            artwork = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        }
        if (artwork != null) {
            // Kept even while another tint is selected, so switching to "Album art" has a color.
            latestArtwork = artwork;
            update();
        }
    }

    /** Picks the color of the latest artwork if the "Album art" tint is selected and it changed. */
    public static synchronized void update() {
        Bitmap artwork = latestArtwork;
        if (artwork == null || artwork == pickedArtwork
                || GlassworksSettings.getTintColorIndex() != GlassworksSettings.TINT_ALBUM_ART) {
            return;
        }
        pickedArtwork = artwork;
        worker().post(() -> {
            try {
                setColor(pickColor(artwork));
            } catch (Throwable t) {
                Log.w(TAG, "Could not pick a color from the artwork", t);
            }
        });
    }

    /** The tint to draw now, part way through a fade if one is running. */
    static int currentRgb() {
        float fraction = fadeFraction();
        return fraction >= 1f ? toRgb : blend(fromRgb, toRgb, fraction);
    }

    /** Whether a fade is running, so pills should keep redrawing. */
    static boolean isFading() {
        return fadeFraction() < 1f;
    }

    private static float fadeFraction() {
        long elapsed = SystemClock.uptimeMillis() - fadeStart;
        return Math.min(1f, Math.max(0f, elapsed / (float) FADE_MS));
    }

    private static synchronized void setColor(int rgb) {
        if (rgb == toRgb) {
            return;
        }
        fromRgb = currentRgb();
        toRgb = rgb;
        fadeStart = SystemClock.uptimeMillis();
        FrostedPillNode.invalidateAll();
    }

    /**
     * The most common colorful hue of the artwork, as a dark color for the tint. Grey artwork
     * gives a dark grey with a hint of its own tone.
     */
    static int pickColor(Bitmap artwork) {
        Bitmap software = artwork.getConfig() == Bitmap.Config.HARDWARE
                ? artwork.copy(Bitmap.Config.ARGB_8888, false)
                : artwork;
        Bitmap small = Bitmap.createScaledBitmap(software, SAMPLE_SIZE, SAMPLE_SIZE, true);
        int[] pixels = new int[SAMPLE_SIZE * SAMPLE_SIZE];
        small.getPixels(pixels, 0, SAMPLE_SIZE, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE);

        float[] weight = new float[HUE_BINS];
        float[][] sum = new float[HUE_BINS][3];
        float[] hsv = new float[3];
        float grayR = 0, grayG = 0, grayB = 0;
        for (int pixel : pixels) {
            int r = Color.red(pixel), g = Color.green(pixel), b = Color.blue(pixel);
            grayR += r;
            grayG += g;
            grayB += b;
            Color.RGBToHSV(r, g, b, hsv);
            if (hsv[1] < 0.2f || hsv[2] < 0.15f) {
                continue;
            }
            int bin = Math.min(HUE_BINS - 1, (int) (hsv[0] / 360f * HUE_BINS));
            float w = hsv[1] * hsv[2];
            weight[bin] += w;
            sum[bin][0] += r * w;
            sum[bin][1] += g * w;
            sum[bin][2] += b * w;
        }

        int best = 0;
        for (int i = 1; i < HUE_BINS; i++) {
            if (weight[i] > weight[best]) {
                best = i;
            }
        }

        int color;
        // Colorful pixels must make up a noticeable part of the image, or it counts as grey.
        if (weight[best] > pixels.length * 0.04f) {
            color = Color.rgb(Math.round(sum[best][0] / weight[best]),
                    Math.round(sum[best][1] / weight[best]),
                    Math.round(sum[best][2] / weight[best]));
            Color.colorToHSV(color, hsv);
            hsv[1] = Math.min(hsv[1], 0.75f);
            hsv[2] = 0.42f;
        } else {
            int n = pixels.length;
            color = Color.rgb(Math.round(grayR / n), Math.round(grayG / n), Math.round(grayB / n));
            Color.colorToHSV(color, hsv);
            hsv[1] = Math.min(hsv[1], 0.25f);
            hsv[2] = 0.2f;
        }
        return Color.HSVToColor(hsv) & 0xFFFFFF;
    }

    private static synchronized Handler worker() {
        if (worker == null) {
            HandlerThread thread = new HandlerThread("Glassworks-Artwork");
            thread.start();
            worker = new Handler(thread.getLooper());
        }
        return worker;
    }

    static int blend(int from, int to, float fraction) {
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * fraction);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * fraction);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * fraction);
        return (r << 16) | (g << 8) | b;
    }
}
