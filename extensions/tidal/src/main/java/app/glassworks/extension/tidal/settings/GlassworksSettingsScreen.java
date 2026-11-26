package app.glassworks.extension.tidal.settings;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Glassworks settings pages, built from plain Android views so they need no resources or manifest
 * entries. The main page lists one row per category (one per patch area); each category opens
 * its own page.
 * <p>
 * Changes are saved immediately. When the main page closes after a change on any page, the
 * activity is recreated so patched UI is rebuilt with the new settings.
 */
final class GlassworksSettingsScreen {

    private static final int BACKGROUND = Color.BLACK;
    private static final int PRIMARY_TEXT = Color.WHITE;
    private static final int SECONDARY_TEXT = 0xFF9E9E9E;
    private static final int ACCENT = 0xFF33FFEE;
    private static final int DIVIDER = 0xFF1F1F1F;
    private static final int CONTROL_BACKGROUND = 0xFF3A3A3C;

    private final Activity activity;
    private final GlassworksSettingsScreen parent;
    private final Dialog dialog;
    private final float density;
    private final LinearLayout content;

    /** Rows that only apply to the pill bar; disabled when the original bar is selected. */
    private final List<View> pillOnlyRows = new ArrayList<>();
    private final List<Runnable> refreshers = new ArrayList<>();
    private boolean changed;

    /** Opens the main Glassworks page. */
    static void showMain(Activity activity) {
        GlassworksSettingsScreen screen = new GlassworksSettingsScreen(activity, null, "Glassworks");
        screen.buildMain();
        screen.dialog.show();
    }

    private GlassworksSettingsScreen(Activity activity, GlassworksSettingsScreen parent, String title) {
        this.activity = activity;
        this.parent = parent;
        this.density = activity.getResources().getDisplayMetrics().density;

        dialog = new Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar);
        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(8), 0, dp(32));
        addTopBar(title);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Android 15+ draws the dialog edge-to-edge, so keep content out of the system bars.
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsets.CONSUMED;
        });

        dialog.setContentView(scroll);
        dialog.setOnDismissListener(d -> onClosed());

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setStatusBarColor(BACKGROUND);
            window.setNavigationBarColor(BACKGROUND);
        }
    }

    private void onClosed() {
        if (!changed) {
            return;
        }
        if (parent != null) {
            // Sub-page: let the main page recreate the activity once it closes.
            parent.changed = true;
        } else if (!activity.isFinishing()) {
            activity.recreate();
        }
    }

    // region Pages

    /** Main page: one row per category. Add a row here for each new patch area. */
    private void buildMain() {
        categoryRow("Navigation bar", "Frosted pill bar, glass and spacing",
                () -> openPage("Navigation bar", GlassworksSettingsScreen::buildNavigationBar));

        footer("Glassworks patches are unofficial and not affiliated with TIDAL.");
    }

    private void buildNavigationBar() {
        section("Style", false);

        switchRow("Frosted pill bar",
                "Split the mini player and tabs into frosted glass pills. Off restores TIDAL's original bar.",
                false,
                GlassworksSettings::isPillBarEnabled,
                value -> {
                    GlassworksSettings.putBoolean(GlassworksSettings.KEY_PILL_BAR, value);
                    updatePillOnlyRows();
                });

        sliderRow("Gap between pills", "dp", 0, GlassworksSettings.MAX_PILL_GAP_DP,
                GlassworksSettings::getPillGapDp,
                value -> GlassworksSettings.putInt(GlassworksSettings.KEY_PILL_GAP_DP, value));

        section("Glass", true);

        sliderRow("Tint", "%", 0, 100,
                GlassworksSettings::getTintPercent,
                value -> GlassworksSettings.putInt(GlassworksSettings.KEY_TINT_PERCENT, value));

        sliderRow("Blur", "dp", 0, GlassworksSettings.MAX_BLUR_DP,
                GlassworksSettings::getBlurDp,
                value -> GlassworksSettings.putInt(GlassworksSettings.KEY_BLUR_DP, value));

        sliderRow("Color saturation", "%", GlassworksSettings.MIN_SATURATION_PERCENT,
                GlassworksSettings.MAX_SATURATION_PERCENT,
                GlassworksSettings::getSaturationPercent,
                value -> GlassworksSettings.putInt(GlassworksSettings.KEY_SATURATION_PERCENT, value));

        switchRow("Edge highlight", "A thin light outline around each pill.", true,
                GlassworksSettings::isEdgeHighlightEnabled,
                value -> GlassworksSettings.putBoolean(GlassworksSettings.KEY_EDGE_HIGHLIGHT, value));

        button("Reset to defaults", () -> {
            GlassworksSettings.resetNavigationBar();
            changed = true;
            for (Runnable refresher : refreshers) {
                refresher.run();
            }
            updatePillOnlyRows();
        });

        footer("Changes apply when you leave Glassworks.");
        updatePillOnlyRows();
    }

    private void openPage(String title, PageBuilder builder) {
        GlassworksSettingsScreen page = new GlassworksSettingsScreen(activity, this, title);
        builder.build(page);
        page.dialog.show();
    }

    // endregion

    // region Rows

    private void addTopBar(String title) {
        LinearLayout topBar = new LinearLayout(activity);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(8), dp(8), dp(16), dp(8));

        TextView back = text("←", 24, PRIMARY_TEXT, false);
        back.setPadding(dp(12), dp(4), dp(16), dp(4));
        back.setContentDescription("Back");
        back.setOnClickListener(v -> dialog.dismiss());
        topBar.addView(back);
        topBar.addView(text(title, 22, PRIMARY_TEXT, true));
        content.addView(topBar);
    }

    private void section(String title, boolean withDivider) {
        if (withDivider) {
            View divider = new View(activity);
            divider.setBackgroundColor(DIVIDER);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2));
            dividerParams.setMargins(0, dp(16), 0, 0);
            content.addView(divider, dividerParams);
        }

        TextView header = text(title, 13, SECONDARY_TEXT, true);
        header.setPadding(dp(16), dp(16), dp(16), dp(4));
        content.addView(header);
    }

    private void categoryRow(String title, String summary, Runnable onClick) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(16), dp(16), dp(16));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 17, PRIMARY_TEXT, false));
        TextView summaryView = text(summary, 14, SECONDARY_TEXT, false);
        summaryView.setPadding(0, dp(2), 0, 0);
        texts.addView(summaryView);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(text("›", 24, SECONDARY_TEXT, false));
        row.setOnClickListener(v -> onClick.run());
        content.addView(row);
    }

    private void switchRow(String title, String summary, boolean pillOnly,
                           BooleanGetter getter, BooleanSetter setter) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 17, PRIMARY_TEXT, false));
        TextView summaryView = text(summary, 14, SECONDARY_TEXT, false);
        summaryView.setPadding(0, dp(2), 0, 0);
        texts.addView(summaryView);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(activity);
        toggle.setThumbTintList(switchColors(Color.WHITE, 0xFFBDBDBD));
        toggle.setTrackTintList(switchColors(ACCENT, CONTROL_BACKGROUND));
        toggle.setChecked(getter.get());
        toggle.setOnCheckedChangeListener((button, checked) -> {
            setter.set(checked);
            changed = true;
        });
        row.addView(toggle);
        row.setOnClickListener(v -> toggle.toggle());

        refreshers.add(() -> toggle.setChecked(getter.get()));
        if (pillOnly) {
            pillOnlyRows.add(row);
        }
        content.addView(row);
    }

    private void sliderRow(String title, String unit, int min, int max,
                           IntGetter getter, IntSetter setter) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(12), dp(16), dp(4));

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(text(title, 17, PRIMARY_TEXT, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView valueView = text("", 15, SECONDARY_TEXT, false);
        header.addView(valueView);
        row.addView(header);

        SeekBar seekBar = new SeekBar(activity);
        seekBar.setMin(min);
        seekBar.setMax(max);
        seekBar.setProgress(getter.get());
        seekBar.setProgressTintList(ColorStateList.valueOf(ACCENT));
        seekBar.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(CONTROL_BACKGROUND));
        valueView.setText(getter.get() + " " + unit);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                valueView.setText(progress + " " + unit);
                if (fromUser) {
                    setter.set(progress);
                    changed = true;
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        seekParams.topMargin = dp(4);
        row.addView(seekBar, seekParams);

        refreshers.add(() -> {
            seekBar.setProgress(getter.get());
            valueView.setText(getter.get() + " " + unit);
        });
        pillOnlyRows.add(row);
        content.addView(row);
    }

    private void button(String label, Runnable onClick) {
        Button button = new Button(activity);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(PRIMARY_TEXT);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF1C1C1E);
        background.setCornerRadius(dp(24));
        button.setBackground(background);
        button.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        params.setMargins(dp(16), dp(24), dp(16), 0);
        content.addView(button, params);
    }

    private void footer(String value) {
        TextView footer = text(value, 13, SECONDARY_TEXT, false);
        footer.setPadding(dp(16), dp(16), dp(16), 0);
        content.addView(footer);
    }

    // endregion

    private void updatePillOnlyRows() {
        boolean enabled = GlassworksSettings.isPillBarEnabled();
        for (View row : pillOnlyRows) {
            setEnabledRecursively(row, enabled);
            row.setAlpha(enabled ? 1f : 0.4f);
        }
    }

    private static void setEnabledRecursively(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setEnabledRecursively(group.getChildAt(i), enabled);
            }
        }
    }

    private TextView text(String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private static ColorStateList switchColors(int checked, int unchecked) {
        return new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{checked, unchecked});
    }

    private int dp(float value) {
        return Math.round(value * density);
    }

    private interface PageBuilder {
        void build(GlassworksSettingsScreen page);
    }

    private interface BooleanGetter {
        boolean get();
    }

    private interface BooleanSetter {
        void set(boolean value);
    }

    private interface IntGetter {
        int get();
    }

    private interface IntSetter {
        void set(int value);
    }
}
