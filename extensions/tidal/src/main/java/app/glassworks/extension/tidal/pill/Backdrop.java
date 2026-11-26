package app.glassworks.extension.tidal.pill;

import android.content.Context;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import java.util.WeakHashMap;

import app.glassworks.extension.tidal.settings.GlassworksSettings;

/**
 * A blurred copy of the screen content that sits behind TIDAL's bottom bar.
 * <p>
 * TIDAL's main layout is a FrameLayout with the screens in {@code @id/coordinatorLayout} and the
 * Compose bottom bar ({@code @id/appScaffold}) on top of it. Before every frame this records the
 * coordinator layout into a {@link RenderNode} with a blur and saturation {@link RenderEffect}.
 * Pills draw that node, so the blur follows scrolling without the pills having to redraw.
 */
final class Backdrop implements ViewTreeObserver.OnPreDrawListener {

    /** One backdrop per source view, shared by all pills in that window. */
    private static final WeakHashMap<View, Backdrop> BACKDROPS = new WeakHashMap<>();

    private final View source;
    private final RenderNode node = new RenderNode("GlassworksBackdrop");
    private ViewTreeObserver observer;
    private int users;
    private boolean ready;
    /** Settings version the blur effect was made for. */
    private int effectVersion = -1;

    private Backdrop(View source) {
        this.source = source;
        updateEffect();
    }

    /** Re-creates the blur when the settings changed, so changes apply without a restart. */
    private void updateEffect() {
        int version = GlassworksSettings.getVersion();
        if (version != effectVersion) {
            effectVersion = version;
            node.setRenderEffect(createEffect(source.getResources().getDisplayMetrics().density));
        }
    }

    /** Blur and saturation from the settings. */
    static RenderEffect createEffect(float density) {
        ColorMatrix saturation = new ColorMatrix();
        saturation.setSaturation(GlassworksSettings.getSaturationPercent() / 100f);
        ColorMatrixColorFilter saturationFilter = new ColorMatrixColorFilter(saturation);

        float radius = GlassworksSettings.getBlurDp() * density;
        if (radius <= 0f) {
            return RenderEffect.createColorFilterEffect(saturationFilter);
        }

        return RenderEffect.createColorFilterEffect(
                saturationFilter,
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        );
    }

    /**
     * @param host The AndroidComposeView that hosts the pill.
     * @return The backdrop for the content behind {@code host}, or null if it cannot be found.
     */
    static Backdrop acquire(View host) {
        View source = findSource(host);
        if (source == null) {
            return null;
        }

        Backdrop backdrop = BACKDROPS.get(source);
        if (backdrop == null) {
            backdrop = new Backdrop(source);
            BACKDROPS.put(source, backdrop);
        }

        if (backdrop.users++ == 0) {
            backdrop.start();
        }
        return backdrop;
    }

    void release() {
        if (users > 0 && --users == 0) {
            stop();
        }
    }

    View getSource() {
        return source;
    }

    RenderNode getNode() {
        return node;
    }

    boolean isReady() {
        return ready;
    }

    @Override
    public boolean onPreDraw() {
        record();
        return true;
    }

    private void start() {
        observer = source.getViewTreeObserver();
        observer.addOnPreDrawListener(this);
        record();
    }

    private void stop() {
        if (observer != null && observer.isAlive()) {
            observer.removeOnPreDrawListener(this);
        }
        observer = null;
        ready = false;
        node.discardDisplayList();
    }

    private void record() {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= 0 || height <= 0) {
            ready = false;
            return;
        }

        updateEffect();
        node.setPosition(0, 0, width, height);
        RecordingCanvas canvas = node.beginRecording(width, height);
        try {
            // The source has no background of its own: TIDAL's black comes from the window.
            // Without it the recording is transparent where the screen looks black, and the
            // sharp content would show through the blur.
            drawWindowBackground(canvas, width, height);
            source.draw(canvas);
        } finally {
            node.endRecording();
        }
        ready = true;
    }

    private void drawWindowBackground(RecordingCanvas canvas, int width, int height) {
        Drawable background = source.getRootView().getBackground();
        if (background == null) {
            canvas.drawColor(Color.BLACK);
            return;
        }

        Rect previousBounds = background.copyBounds();
        background.setBounds(0, 0, width, height);
        background.draw(canvas);
        background.setBounds(previousBounds);
    }

    /**
     * Finds the view drawn behind the Compose bottom bar.
     * Uses {@code @id/coordinatorLayout} if it exists, otherwise the first sibling of the
     * bar's ComposeView that is drawn before it.
     */
    private static View findSource(View host) {
        View root = host.getRootView();
        Context context = host.getContext();

        int id = context.getResources().getIdentifier("coordinatorLayout", "id", context.getPackageName());
        if (id != 0) {
            View view = root.findViewById(id);
            if (view != null) {
                return view;
            }
        }

        // Walk up from the host to the first parent that has an earlier (lower) sibling.
        View child = host;
        ViewParent parent = host.getParent();
        while (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;
            int index = group.indexOfChild(child);
            if (index > 0) {
                return group.getChildAt(0);
            }
            child = group;
            parent = group.getParent();
        }
        return null;
    }
}
