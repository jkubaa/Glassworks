package app.glassworks.extension.tidal.pill;

import android.util.Log;

import androidx.compose.ui.Modifier;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import app.glassworks.extension.tidal.settings.GlassworksSettings;

/**
 * Hides tabs and moves one tab into its own round pill at the right of the tab bar.
 * <p>
 * Called from bytecode injected into TIDAL's TabBar composables: {@link #tabs} filters the list of
 * {@code TabBarContract$TabType} values before TIDAL builds the tab items, and
 * {@link #setCurrentItem} plus {@link #itemModifier} replace the layout modifier of the separate
 * tab, which is always last.
 * <p>
 * Layout of a split tab bar (row padding and item spacing are TIDAL's 4dp):
 * <pre>
 * | 4 | tab | 4 | tab | 4 |  gap  | 4 | separate tab (48) | 4 |
 * |<------ left pill ------>|       |<------- round pill ------>|
 * </pre>
 * The separate tab is {@code gap + 4 + 48} wide with {@code gap + 4} padding at its start, so its
 * 48dp square is centered in the round pill and the tab before it keeps 4dp to the pill edge.
 */
@SuppressWarnings({"unused", "rawtypes", "unchecked"})
public final class TabBarHook {

    private static final String TAG = "Glassworks";
    private static final String TAB_TYPE_CLASS = "com.tidal.android.feature.appscaffold.ui.TabBarContract$TabType";

    /** Height of a tab item: a 24dp icon with 12dp padding (TabBar.kt). */
    private static final float ITEM_SIZE_DP = 48f;
    /** TIDAL's spacing between tab items, and the tab bar row padding. */
    private static final float ITEM_SPACING_DP = 4f;

    /** Whether the current tab list ends with the separate tab, drawn in its own pill. */
    private static volatile boolean splitActive;
    private static volatile String splitTab = "";

    private static Object currentItem;
    private static Class<?> itemClass;
    private static Field itemTabField;

    private TabBarHook() {
    }

    /**
     * Injected after TIDAL builds the ordered list of tab types, before it removes the
     * Upload tab for users without it.
     */
    public static List tabs(List types) {
        if (types == null) {
            return null;
        }
        try {
            List result = new ArrayList();
            for (Object type : types) {
                if (!GlassworksSettings.isTabHidden(name(type))) {
                    result.add(type);
                }
            }
            if (result.isEmpty()) {
                // Never hide every tab.
                result = new ArrayList(types);
            }

            String separate = GlassworksSettings.isTwoPills() ? GlassworksSettings.getSeparateTab() : "";
            Object separateType = null;
            if (!separate.isEmpty() && result.size() > 1) {
                for (Object type : result) {
                    if (separate.equals(name(type))) {
                        separateType = type;
                        break;
                    }
                }
            }
            if (separateType != null) {
                result.remove(separateType);
                result.add(separateType);
            }
            splitTab = separateType != null ? separate : "";
            splitActive = separateType != null;
            return result;
        } catch (Throwable t) {
            Log.e(TAG, "Failed to change the tabs", t);
            splitActive = false;
            return types;
        }
    }

    /** Injected before each tab item is composed, with TIDAL's tab item object. */
    public static void setCurrentItem(Object item) {
        currentItem = item;
    }

    /** Injected before each tab item is composed, replacing its {@code Modifier.weight(1f)}. */
    public static Modifier itemModifier(Modifier modifier) {
        Object item = currentItem;
        currentItem = null;
        if (!splitActive || item == null || modifier == null) {
            return modifier;
        }
        try {
            if (!splitTab.equals(name(tabTypeOf(item)))) {
                return modifier;
            }
            float start = getSplitGapDp() + ITEM_SPACING_DP;
            Modifier sized = ComposeCompat.width(Modifier.Companion, start + ITEM_SIZE_DP);
            return ComposeCompat.padding(sized, start, 0f, 0f, 0f);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to separate the tab", t);
            return modifier;
        }
    }

    /** Whether the tab bar pill is drawn split, with the separate tab in its own pill. */
    static boolean isSplitActive() {
        return splitActive && GlassworksSettings.isTwoPills();
    }

    static float getSplitGapDp() {
        return GlassworksSettings.getPillGapDp();
    }

    private static Object tabTypeOf(Object item) throws ReflectiveOperationException {
        Class<?> cls = item.getClass();
        if (cls != itemClass) {
            Class<?> tabType = Class.forName(TAB_TYPE_CLASS, false, cls.getClassLoader());
            Field found = null;
            for (Field field : cls.getDeclaredFields()) {
                if (field.getType() == tabType) {
                    field.setAccessible(true);
                    found = field;
                    break;
                }
            }
            if (found == null) {
                throw new NoSuchFieldException("No tab type field in " + cls.getName());
            }
            itemTabField = found;
            itemClass = cls;
        }
        return itemTabField.get(item);
    }

    private static String name(Object type) {
        return type instanceof Enum ? ((Enum) type).name() : String.valueOf(type);
    }
}
