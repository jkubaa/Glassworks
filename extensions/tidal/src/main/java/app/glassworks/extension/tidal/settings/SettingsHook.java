package app.glassworks.extension.tidal.settings;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * Adds a "Glassworks" row at the top of TIDAL's main settings page.
 * <p>
 * TIDAL's settings rows implement {@code com.aspiro.wamp.settings.a} (not obfuscated) and return
 * a view state whose class and click-callback interface are obfuscated and change between
 * versions. Instead of hard-coding those names, this finds the view state class of an existing
 * navigation row at runtime and builds the Glassworks row from it with {@link Proxy} objects.
 */
@SuppressWarnings("unused")
public final class SettingsHook {

    private static final String TAG = "Glassworks";

    /** TIDAL's settings item interface: {@code T a()} returns the view state, {@code b()} refreshes it. */
    private static final String SETTINGS_ITEM_INTERFACE = "com.aspiro.wamp.settings.a";
    private static final String MAYBE_CLASS = "io.reactivex.Maybe";

    private static final String ROW_TITLE = "Glassworks";

    private static WeakReference<Activity> currentActivity = new WeakReference<>(null);
    private static boolean lifecycleCallbacksRegistered;

    /** Cached row, rebuilt only if TIDAL's view state class changes (it won't within one run). */
    private static Object cachedItem;
    private static Class<?> cachedViewStateClass;

    private SettingsHook() {
    }

    /**
     * Injected at the start of {@code MainActivity.onCreate}, before any UI is created.
     */
    public static void onMainActivityCreate(Activity activity) {
        GlassworksSettings.init(activity);
        currentActivity = new WeakReference<>(activity);

        if (!lifecycleCallbacksRegistered) {
            lifecycleCallbacksRegistered = true;
            activity.getApplication().registerActivityLifecycleCallbacks(new ActivityTracker());
        }
    }

    /**
     * Injected before every {@code return} of TIDAL's main settings list provider.
     *
     * @return The same list with the Glassworks row inserted first,
     * or unchanged if anything goes wrong.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List addSettingsItem(List items) {
        if (items == null) {
            return null;
        }

        try {
            Class<?> itemInterface = Class.forName(SETTINGS_ITEM_INTERFACE);
            Method viewStateGetter = itemInterface.getMethod("a");

            Constructor<?> viewStateConstructor = null;

            for (Object item : items) {
                if (!itemInterface.isInstance(item)) {
                    continue;
                }

                Object viewState = viewStateGetter.invoke(item);
                if (viewState == null) {
                    continue;
                }

                viewStateConstructor = findNavigationRowConstructor(viewState.getClass());
                if (viewStateConstructor != null) {
                    break;
                }
            }

            if (viewStateConstructor == null) {
                Log.w(TAG, "No navigation row found in TIDAL settings");
                return items;
            }

            items.add(0, getOrCreateItem(itemInterface, viewStateConstructor));
        } catch (Throwable t) {
            Log.e(TAG, "Failed to add the Glassworks settings row", t);
        }
        return items;
    }

    /**
     * The navigation row view state is constructed as
     * {@code (title, subtitle, textRight, isEnabled, isClickable, isDestructive,
     * showNavigationIndicator, onClick)}, where onClick is TIDAL's renamed Kotlin Function0.
     */
    private static Constructor<?> findNavigationRowConstructor(Class<?> viewStateClass) {
        for (Constructor<?> constructor : viewStateClass.getConstructors()) {
            Class<?>[] p = constructor.getParameterTypes();
            if (p.length == 8
                    && p[0] == CharSequence.class && p[1] == CharSequence.class && p[2] == CharSequence.class
                    && p[3] == boolean.class && p[4] == boolean.class
                    && p[5] == boolean.class && p[6] == boolean.class
                    && p[7].isInterface()) {
                return constructor;
            }
        }
        return null;
    }

    private static synchronized Object getOrCreateItem(Class<?> itemInterface, Constructor<?> constructor)
            throws ReflectiveOperationException {
        Class<?> viewStateClass = constructor.getDeclaringClass();
        if (cachedItem != null && cachedViewStateClass == viewStateClass) {
            return cachedItem;
        }

        Class<?> onClickInterface = constructor.getParameterTypes()[7];
        ClassLoader loader = viewStateClass.getClassLoader();

        Object onClick = Proxy.newProxyInstance(loader, new Class<?>[]{onClickInterface},
                new ProxyHandler("GlassworksOnClick") {
                    @Override
                    Object onInvoke(Method method, Object[] args) throws ReflectiveOperationException {
                        if (method.getName().equals("invoke") && (args == null || args.length == 0)) {
                            openSettingsScreen();
                            // The click callback returns an RxJava Maybe of a navigation event.
                            return Class.forName(MAYBE_CLASS).getMethod("empty").invoke(null);
                        }
                        return null;
                    }
                });

        Object viewState = constructor.newInstance(ROW_TITLE, null, null,
                true, true, false, false, onClick);

        cachedItem = Proxy.newProxyInstance(loader, new Class<?>[]{itemInterface},
                new ProxyHandler("GlassworksSettingsItem") {
                    @Override
                    Object onInvoke(Method method, Object[] args) {
                        // a(): the view state. b(): refresh, nothing to do.
                        return method.getName().equals("a") ? viewState : null;
                    }
                });
        cachedViewStateClass = viewStateClass;
        return cachedItem;
    }

    private static void openSettingsScreen() {
        new Handler(Looper.getMainLooper()).post(() -> {
            Activity activity = currentActivity.get();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                Log.w(TAG, "No activity to open the Glassworks settings in");
                return;
            }
            GlassworksSettingsScreen.showMain(activity);
        });
    }

    /**
     * Handles Object methods with identity semantics, and everything else in {@link #onInvoke}.
     */
    private abstract static class ProxyHandler implements InvocationHandler {
        private final String name;

        ProxyHandler(String name) {
            this.name = name;
        }

        @Override
        public final Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "equals":
                    if (args != null && args.length == 1) {
                        return proxy == args[0];
                    }
                    break;
                case "hashCode":
                    if (args == null || args.length == 0) {
                        return System.identityHashCode(proxy);
                    }
                    break;
                case "toString":
                    if (args == null || args.length == 0) {
                        return name;
                    }
                    break;
            }
            return onInvoke(method, args);
        }

        abstract Object onInvoke(Method method, Object[] args) throws Throwable;
    }

    /** Keeps {@link #currentActivity} pointing at the resumed activity. */
    private static final class ActivityTracker implements Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityResumed(Activity activity) {
            currentActivity = new WeakReference<>(activity);
        }

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(Activity activity) {
        }

        @Override
        public void onActivityPaused(Activity activity) {
        }

        @Override
        public void onActivityStopped(Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
        }
    }
}
