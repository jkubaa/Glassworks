package app.glassworks.extension.tidal.settings;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * A switch drawn like TIDAL's: a 56x28dp stadium track (cyan when on, grey when off) with a
 * round white thumb. Android's {@link android.widget.Switch} cannot be sized to match it.
 */
final class TidalToggle extends View {

    interface OnChangeListener {
        void onChanged(boolean checked);
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    private boolean checked;
    /** Thumb position: 0 is off (left), 1 is on (right). */
    private float position;
    private ValueAnimator animator;
    private OnChangeListener listener;

    TidalToggle(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        thumbPaint.setColor(TidalStyle.SWITCH_THUMB);
        setClickable(true);
        setOnClickListener(v -> toggle());
    }

    boolean isChecked() {
        return checked;
    }

    void setOnChangeListener(OnChangeListener listener) {
        this.listener = listener;
    }

    void toggle() {
        setChecked(!checked, true);
        if (listener != null) {
            listener.onChanged(checked);
        }
    }

    /** Sets the state without calling the listener. */
    void setChecked(boolean value, boolean animate) {
        checked = value;
        if (animator != null) {
            animator.cancel();
        }
        float target = value ? 1f : 0f;
        if (!animate || !isAttachedToWindow()) {
            position = target;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(position, target);
        animator.setDuration(150);
        animator.addUpdateListener(a -> {
            position = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(Math.round(TidalStyle.SWITCH_WIDTH_DP * density),
                Math.round(TidalStyle.SWITCH_HEIGHT_DP * density));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        float radius = height / 2f;
        trackPaint.setColor(blend(TidalStyle.SWITCH_TRACK_OFF, TidalStyle.SWITCH_TRACK_ON, position));
        canvas.drawRoundRect(0f, 0f, width, height, radius, radius, trackPaint);

        float inset = TidalStyle.SWITCH_THUMB_INSET_DP * density;
        float thumbRadius = radius - inset;
        float left = radius;
        float right = width - radius;
        canvas.drawCircle(left + (right - left) * position, radius, thumbRadius, thumbPaint);
    }

    private static int blend(int from, int to, float fraction) {
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * fraction);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * fraction);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * fraction);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
