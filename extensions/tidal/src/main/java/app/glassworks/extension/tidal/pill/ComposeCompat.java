package app.glassworks.extension.tidal.pill;

import android.util.Log;

import androidx.compose.ui.Modifier;
import androidx.compose.ui.graphics.Brush;
import androidx.compose.ui.graphics.Shape;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Calls Compose functions that Java source cannot reference directly: names mangled with '-'
 * (Kotlin inline classes) and synthetic {@code $default} methods (Kotlin default arguments).
 * Methods are looked up once and cached. If a lookup fails, the modifier is returned unchanged
 * instead of crashing the app.
 */
final class ComposeCompat {

    private static final String TAG = "Glassworks";

    private static final String BACKGROUND_KT = "androidx.compose.foundation.BackgroundKt";
    private static final String PADDING_KT = "androidx.compose.foundation.layout.PaddingKt";
    private static final String SIZE_KT = "androidx.compose.foundation.layout.SizeKt";
    private static final String SHAPE_KT = "androidx.compose.foundation.shape.RoundedCornerShapeKt";

    /** Cached lookups. A null value means the lookup failed and will not be retried. */
    private static final Map<String, Method> METHODS = new HashMap<>();

    private ComposeCompat() {
    }

    /** {@code modifier.background(color, shape)}, where color is a packed Compose Color. */
    static Modifier background(Modifier modifier, long color, Shape shape) {
        Method method = find(BACKGROUND_KT, "background-bw27NRU",
                Modifier.class, long.class, Shape.class);
        return invoke(method, modifier, modifier, color, shape);
    }

    /** {@code modifier.background(brush, shape, alpha)}, using TIDAL's original arguments. */
    static Modifier brushBackground(Modifier modifier, Brush brush, Shape shape,
                                    float alpha, int defaultsMask, Object unused) {
        Method method = find(BACKGROUND_KT, "background$default",
                Modifier.class, Brush.class, Shape.class, float.class, int.class, Object.class);
        return invoke(method, modifier, modifier, brush, shape, alpha, defaultsMask, unused);
    }

    /** {@code modifier.padding(bottom = bottomDp.dp)}. */
    static Modifier paddingBottom(Modifier modifier, float bottomDp) {
        return padding(modifier, 0f, 0f, 0f, bottomDp);
    }

    /** {@code modifier.padding(start, top, end, bottom)}, all in dp. */
    static Modifier padding(Modifier modifier, float startDp, float topDp, float endDp, float bottomDp) {
        if (startDp <= 0f && topDp <= 0f && endDp <= 0f && bottomDp <= 0f) {
            return modifier;
        }
        // padding(start, top, end, bottom) with default arguments. All four are passed,
        // so the defaults mask is 0.
        Method method = find(PADDING_KT, "padding-qDBjuR0$default", Modifier.class,
                float.class, float.class, float.class, float.class, int.class, Object.class);
        return invoke(method, modifier, modifier, startDp, topDp, endDp, bottomDp, 0, null);
    }

    /** {@code modifier.width(widthDp.dp)}. */
    static Modifier width(Modifier modifier, float widthDp) {
        Method method = find(SIZE_KT, "width-3ABfNKs", Modifier.class, float.class);
        return invoke(method, modifier, modifier, widthDp);
    }

    /** {@code RoundedCornerShape(radiusDp.dp)}, or null if it cannot be created. */
    static Shape roundedCornerShape(float radiusDp) {
        String key = SHAPE_KT + ".RoundedCornerShape-0680j_4";
        Method method = find(SHAPE_KT, "RoundedCornerShape-0680j_4", float.class);
        if (method == null) {
            return null;
        }
        try {
            return (Shape) method.invoke(null, radiusDp);
        } catch (ReflectiveOperationException e) {
            Log.e(TAG, "Compose method failed: " + key, e);
            return null;
        }
    }

    private static synchronized Method find(String className, String name, Class<?>... parameters) {
        String key = className + '.' + name;
        if (METHODS.containsKey(key)) {
            return METHODS.get(key);
        }

        Method method = null;
        try {
            method = Class.forName(className).getMethod(name, parameters);
        } catch (ReflectiveOperationException e) {
            Log.e(TAG, "Compose method not found: " + key, e);
        }
        METHODS.put(key, method);
        return method;
    }

    private static Modifier invoke(Method method, Modifier fallback, Object... arguments) {
        if (method == null) {
            return fallback;
        }
        try {
            return (Modifier) method.invoke(null, arguments);
        } catch (ReflectiveOperationException e) {
            Log.e(TAG, "Compose method failed: " + method.getName(), e);
            return fallback;
        }
    }
}
