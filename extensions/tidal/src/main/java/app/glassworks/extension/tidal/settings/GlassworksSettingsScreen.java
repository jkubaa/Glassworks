package app.glassworks.extension.tidal.settings;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import app.glassworks.extension.tidal.lastfm.LastFmAccount;
import app.glassworks.extension.tidal.lastfm.LastFmSettings;
import app.glassworks.extension.tidal.pill.ArtworkTint;
import app.glassworks.extension.tidal.pill.FrostedPillPatch;
import app.glassworks.extension.tidal.pill.GlassPreview;

/**
 * Glassworks settings pages, built from plain Android views so they need no resources or manifest
 * entries, and styled after TIDAL's own settings (see {@link TidalStyle}). The main page lists one
 * row per category (one per patch area); each category opens its own page.
 * <p>
 * Changes are saved immediately. Changes that only affect how the glass is drawn apply at once;
 * when the main page closes after a change to the bar's layout, the activity is recreated so
 * patched UI is rebuilt with the new settings.
 */
final class GlassworksSettingsScreen {

    private static final String LAST_FM_API_ACCOUNT_URL = "https://www.last.fm/api/account/create";

    private final Activity activity;
    private final GlassworksSettingsScreen parent;
    private final Dialog dialog;
    private final float density;
    private final LinearLayout content;

    /** Re-reads every row's value, such as after a reset. */
    private final List<Runnable> refreshers = new ArrayList<>();
    /** Rows enabled only when their condition holds; re-checked after every change. */
    private final List<View> conditionalRows = new ArrayList<>();
    private final List<BooleanGetter> conditions = new ArrayList<>();

    /** Whether changes on this page need the activity to be recreated. */
    private boolean restartOnChange;
    /** While true, rows being added only change how the glass is drawn and apply without a restart. */
    private boolean buildingLiveRows;
    private boolean changed;
    private boolean liveChanged;
    private GlassPreview preview;

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
        content.setPadding(0, 0, 0, dp(32));
        addTopBar(title);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(TidalStyle.BACKGROUND);
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
            window.setStatusBarColor(TidalStyle.BACKGROUND);
            window.setNavigationBarColor(TidalStyle.BACKGROUND);
            window.setWindowAnimations(android.R.style.Animation_Activity);
        }
    }

    private void onClosed() {
        if (parent != null) {
            // Sub-page: let the main page recreate the activity once it closes.
            parent.changed |= changed;
            parent.liveChanged |= liveChanged;
            parent.refresh();
        } else if (changed && !activity.isFinishing()) {
            activity.recreate();
        } else if (liveChanged) {
            FrostedPillPatch.onSettingsChanged();
        }
    }

    private void markChanged(boolean live) {
        if (live) {
            liveChanged = true;
            ArtworkTint.update();
        } else if (restartOnChange) {
            changed = true;
        }
        if (preview != null) {
            preview.update();
        }
        updateConditionalRows();
    }

    /** Adds rows whose changes apply without recreating the activity. */
    private void liveRows(Runnable rows) {
        buildingLiveRows = true;
        try {
            rows.run();
        } finally {
            buildingLiveRows = false;
        }
    }

    private void refresh() {
        for (Runnable refresher : refreshers) {
            refresher.run();
        }
        if (preview != null) {
            preview.update();
        }
        updateConditionalRows();
    }

    // region Pages

    /** Main page: one row per category. Add a row here for each new patch area. */
    private void buildMain() {
        if (Features.navigationBar()) {
            categoryRow("Navigation bar", () -> {
                switch (GlassworksSettings.getBarStyle()) {
                    case GlassworksSettings.STYLE_TWO_PILLS:
                        return "Frosted pills";
                    case GlassworksSettings.STYLE_ONE_PILL:
                        return "Frosted pill";
                    default:
                        return "TIDAL's original bar";
                }
            }, () -> openPage("Navigation bar", GlassworksSettingsScreen::buildNavigationBar));
        }

        if (Features.lastFm()) {
            categoryRow("Last.fm", () -> {
                if (!LastFmSettings.isLoggedIn()) {
                    return "Not logged in";
                }
                if (!LastFmSettings.isEnabled()) {
                    return "Scrobbling off";
                }
                return "Scrobbling as " + LastFmSettings.getUsername();
            }, () -> openPage("Last.fm", GlassworksSettingsScreen::buildLastFm));
        }

    }

    private void buildNavigationBar() {
        restartOnChange = true;
        BooleanGetter pills = GlassworksSettings::isPillBarEnabled;
        BooleanGetter twoPills = GlassworksSettings::isTwoPills;

        preview = new GlassPreview(activity);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        previewParams.setMargins(dp(16), dp(8), dp(16), 0);
        content.addView(preview, previewParams);

        section("Style");

        choiceRow("Bar style",
                new String[]{"Two pills", "One pill", "TIDAL's original bar"},
                () -> {
                    switch (GlassworksSettings.getBarStyle()) {
                        case GlassworksSettings.STYLE_ONE_PILL:
                            return 1;
                        case GlassworksSettings.STYLE_ORIGINAL:
                            return 2;
                        default:
                            return 0;
                    }
                },
                index -> GlassworksSettings.putBarStyle(index == 0 ? GlassworksSettings.STYLE_TWO_PILLS
                        : index == 1 ? GlassworksSettings.STYLE_ONE_PILL : GlassworksSettings.STYLE_ORIGINAL),
                null);

        sliderRow("Gap between pills", "dp", 0, GlassworksSettings.MAX_PILL_GAP_DP,
                GlassworksSettings::getPillGapDp,
                value -> GlassworksSettings.putInt(GlassworksSettings.KEY_PILL_GAP_DP, value),
                twoPills);

        section("Shape and size");

        sliderRow("Corner roundness", "%", 0, 100,
                GlassworksSettings::getRoundnessPercent,
                value -> GlassworksSettings.putInt(GlassworksSettings.KEY_ROUNDNESS_PERCENT, value),
                pills);

        choiceRow("Mini player size", new String[]{"Regular", "Compact"},
                () -> GlassworksSettings.isCompactMiniPlayer() ? 1 : 0,
                index -> GlassworksSettings.putBoolean(GlassworksSettings.KEY_COMPACT_MINI_PLAYER, index == 1),
                pills);

        choiceRow("Artwork shape", new String[]{"TIDAL's", "Rounded", "Circle"},
                GlassworksSettings::getArtworkShape,
                index -> GlassworksSettings.putInt(GlassworksSettings.KEY_ARTWORK_SHAPE, index),
                pills);
        note("The cover in the mini player. Rounded follows the corner roundness.");

        sliderRow("Side margins", "dp", 0, GlassworksSettings.MAX_SIDE_MARGIN_DP,
                GlassworksSettings::getSideMarginDp,
                value -> GlassworksSettings.putInt(GlassworksSettings.KEY_SIDE_MARGIN_DP, value),
                pills);

        sliderRow("Lift from bottom", "dp", 0, GlassworksSettings.MAX_LIFT_DP,
                GlassworksSettings::getLiftDp,
                value -> GlassworksSettings.putInt(GlassworksSettings.KEY_LIFT_DP, value),
                pills);

        section("Glass");

        liveRows(() -> {
            choiceRow("Glass style", new String[]{"Frosted", "Liquid"},
                    GlassworksSettings::getGlassStyle,
                    index -> GlassworksSettings.putInt(GlassworksSettings.KEY_GLASS_STYLE, index),
                    pills);
            note(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                    ? "Liquid adds a sheen and a brighter rim, and bends the content near the edges like a lens."
                    : "Liquid adds a sheen and a brighter rim. Bending the content needs Android 13 or newer.");

            choiceRow("Tint color", GlassworksSettings.TINT_COLOR_NAMES,
                    GlassworksSettings::getTintColorIndex,
                    index -> GlassworksSettings.putInt(GlassworksSettings.KEY_TINT_COLOR, index),
                    pills);
            note("Album art takes its color from the current track's cover and fades as tracks change.");

            sliderRow("Tint", "%", 0, 100,
                    GlassworksSettings::getTintPercent,
                    value -> GlassworksSettings.putInt(GlassworksSettings.KEY_TINT_PERCENT, value),
                    pills);

            sliderRow("Blur", "dp", 0, GlassworksSettings.MAX_BLUR_DP,
                    GlassworksSettings::getBlurDp,
                    value -> GlassworksSettings.putInt(GlassworksSettings.KEY_BLUR_DP, value),
                    pills);

            sliderRow("Color saturation", "%", GlassworksSettings.MIN_SATURATION_PERCENT,
                    GlassworksSettings.MAX_SATURATION_PERCENT,
                    GlassworksSettings::getSaturationPercent,
                    value -> GlassworksSettings.putInt(GlassworksSettings.KEY_SATURATION_PERCENT, value),
                    pills);

            sliderRow("Shadow", "%", 0, 100,
                    GlassworksSettings::getShadowPercent,
                    value -> GlassworksSettings.putInt(GlassworksSettings.KEY_SHADOW_PERCENT, value),
                    pills);

            switchRow("Edge highlight", "A thin light outline around each pill.",
                    GlassworksSettings::isEdgeHighlightEnabled,
                    value -> GlassworksSettings.putBoolean(GlassworksSettings.KEY_EDGE_HIGHLIGHT, value),
                    pills);
        });

        section("Tabs");

        String[] separateOptions = new String[GlassworksSettings.TAB_LABELS.length + 1];
        separateOptions[0] = "None";
        System.arraycopy(GlassworksSettings.TAB_LABELS, 0, separateOptions, 1, GlassworksSettings.TAB_LABELS.length);
        choiceRow("Separate tab", separateOptions,
                () -> {
                    String separate = GlassworksSettings.getSeparateTab();
                    for (int i = 0; i < GlassworksSettings.TAB_NAMES.length; i++) {
                        if (GlassworksSettings.TAB_NAMES[i].equals(separate)) {
                            return i + 1;
                        }
                    }
                    return 0;
                },
                index -> GlassworksSettings.putString(GlassworksSettings.KEY_SEPARATE_TAB,
                        index == 0 ? null : GlassworksSettings.TAB_NAMES[index - 1]),
                twoPills);
        note("Shows one tab in its own round pill at the right end of the bar. Needs two pills.");

        actionRow("Tab order", null, this::showTabOrder, null);

        for (int i = 0; i < GlassworksSettings.TAB_NAMES.length; i++) {
            String name = GlassworksSettings.TAB_NAMES[i];
            String key = GlassworksSettings.KEY_HIDDEN_TAB_PREFIX + name;
            switchRow("Show " + GlassworksSettings.TAB_LABELS[i], null,
                    () -> !GlassworksSettings.isTabHidden(name),
                    value -> GlassworksSettings.putBoolean(key, !value),
                    null);
        }
        note("The Upload tab only appears for accounts that have it. At least one tab is always shown.");

        section("");
        actionRow("Reset to defaults", null, () -> {
            GlassworksSettings.resetNavigationBar();
            changed = true;
            refresh();
        }, null);

        footer("Glass changes apply right away. Changes to the layout and tabs apply when you leave Glassworks.");
        updateConditionalRows();
    }

    private void buildLastFm() {
        BooleanGetter loggedIn = LastFmSettings::isLoggedIn;

        section("Account");

        TextView status = text("", TidalStyle.TITLE_SP, TidalStyle.PRIMARY_TEXT);
        TextView statusDetail = text("", TidalStyle.SUBTITLE_SP, TidalStyle.SECONDARY_TEXT);
        LinearLayout statusRow = rowContainer(LinearLayout.VERTICAL);
        statusRow.addView(status);
        statusDetail.setPadding(0, dp(4), 0, 0);
        statusRow.addView(statusDetail);
        content.addView(statusRow);
        Runnable updateStatus = () -> {
            if (LastFmSettings.isLoggedIn()) {
                status.setText("Logged in as " + LastFmSettings.getUsername());
                int pending = LastFmAccount.getPendingScrobbles();
                String detail = LastFmSettings.getScrobbleCount() + " scrobbled from this device";
                if (pending > 0) {
                    detail += " · " + pending + " waiting to be sent";
                }
                String error = LastFmSettings.getLastError();
                statusDetail.setText(error.isEmpty() ? detail : detail + "\n" + error);
                statusDetail.setTextColor(error.isEmpty() ? TidalStyle.SECONDARY_TEXT : TidalStyle.ERROR_TEXT);
            } else if (!LastFmSettings.hasApiAccount()) {
                status.setText("Not logged in");
                statusDetail.setText("Add your Last.fm API key and secret, then log in.");
                statusDetail.setTextColor(TidalStyle.SECONDARY_TEXT);
            } else {
                status.setText("Not logged in");
                statusDetail.setText("Log in with your Last.fm username and password.");
                statusDetail.setTextColor(TidalStyle.SECONDARY_TEXT);
            }
        };
        refreshers.add(updateStatus);

        inputRow("API key", LastFmSettings.KEY_API_KEY, LastFmSettings::getApiKey, false);
        inputRow("API secret", LastFmSettings.KEY_API_SECRET, LastFmSettings::getApiSecret, true);

        actionRow("Log in", null, this::showLogin,
                () -> LastFmSettings.hasApiAccount() && !LastFmSettings.isLoggedIn());

        actionRow("Send waiting scrobbles", null, () -> {
            LastFmAccount.sendPendingScrobbles();
            content.postDelayed(this::refresh, 2000);
        }, () -> LastFmSettings.isLoggedIn() && LastFmAccount.getPendingScrobbles() > 0);

        actionRow("Log out", null, () -> {
            LastFmSettings.logOut();
            refresh();
        }, loggedIn);

        actionRow("Get an API key",
                "Opens Last.fm in your browser. Any application name works, and the callback URL can stay empty.",
                () -> {
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(LAST_FM_API_ACCOUNT_URL)));
                    } catch (Exception ignored) {
                        // No browser.
                    }
                }, null);

        section("Scrobbling");

        switchRow("Scrobble to Last.fm", "Add the tracks you play to your Last.fm profile.",
                LastFmSettings::isEnabled,
                value -> GlassworksSettings.putBoolean(LastFmSettings.KEY_ENABLED, value),
                null);

        switchRow("Now playing", "Show the current track on your profile while it plays.",
                LastFmSettings::isNowPlayingEnabled,
                value -> GlassworksSettings.putBoolean(LastFmSettings.KEY_NOW_PLAYING, value),
                LastFmSettings::isEnabled);

        switchRow("Main artist only", "For tracks with several artists, scrobble only the first one.",
                LastFmSettings::isMainArtistOnly,
                value -> GlassworksSettings.putBoolean(LastFmSettings.KEY_MAIN_ARTIST_ONLY, value),
                LastFmSettings::isEnabled);

        sliderRow("Scrobble after", "% played", LastFmSettings.MIN_THRESHOLD_PERCENT,
                LastFmSettings.MAX_THRESHOLD_PERCENT,
                LastFmSettings::getThresholdPercent,
                value -> GlassworksSettings.putInt(LastFmSettings.KEY_THRESHOLD_PERCENT, value),
                LastFmSettings::isEnabled);
        note("A track also counts after 4 minutes of play. Tracks shorter than 30 seconds are not scrobbled.");

        footer("Your password is only sent to Last.fm to log in. It is not stored on this device.");
        refresh();
    }

    private void openPage(String title, PageBuilder builder) {
        GlassworksSettingsScreen page = new GlassworksSettingsScreen(activity, this, title);
        builder.build(page);
        page.dialog.show();
    }

    // endregion

    // region Dialogs

    private void showLogin() {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        EditText username = input("Username", false);
        EditText password = input("Password", true);
        TextView error = text("", TidalStyle.SUBTITLE_SP, TidalStyle.ERROR_TEXT);
        error.setVisibility(View.GONE);
        form.addView(username);
        form.addView(password);
        form.addView(error);

        Dialog login = panel("Log in to Last.fm", form);
        LinearLayout buttons = dialogButtons(form);
        TextView cancel = dialogButton("Cancel", false);
        TextView submit = dialogButton("Log in", true);
        buttons.addView(cancel);
        buttons.addView(submit);

        cancel.setOnClickListener(v -> login.dismiss());
        submit.setOnClickListener(v -> {
            String user = username.getText().toString().trim();
            String pass = password.getText().toString();
            if (user.isEmpty() || pass.isEmpty()) {
                error.setText("Enter your username and password.");
                error.setVisibility(View.VISIBLE);
                return;
            }
            submit.setEnabled(false);
            submit.setAlpha(0.4f);
            error.setVisibility(View.GONE);
            LastFmAccount.logIn(user, pass, result -> {
                if (result == null) {
                    login.dismiss();
                    refresh();
                } else {
                    submit.setEnabled(true);
                    submit.setAlpha(1f);
                    error.setText(result);
                    error.setVisibility(View.VISIBLE);
                }
            });
        });
        login.show();
    }

    /** Moves tabs up and down; each move is saved at once. */
    private void showTabOrder() {
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        Dialog orderDialog = panel("Tab order", list);
        Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            list.removeAllViews();
            String[] order = GlassworksSettings.getFullTabOrder();
            for (int i = 0; i < order.length; i++) {
                int index = i;
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setMinimumHeight(dp(52));
                row.setPadding(dp(24), 0, dp(12), 0);
                row.addView(text(GlassworksSettings.tabLabel(order[i]), TidalStyle.TITLE_SP, TidalStyle.PRIMARY_TEXT),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(moveButton("↑", "Move up", index > 0, () -> {
                    swap(order, index, index - 1);
                    fill[0].run();
                }));
                row.addView(moveButton("↓", "Move down", index < order.length - 1, () -> {
                    swap(order, index, index + 1);
                    fill[0].run();
                }));
                list.addView(row);
            }
            LinearLayout buttons = dialogButtons(list);
            TextView done = dialogButton("Done", true);
            buttons.setPadding(dp(24), dp(16), dp(24), 0);
            buttons.addView(done);
            done.setOnClickListener(v -> orderDialog.dismiss());
        };
        fill[0].run();
        orderDialog.show();
    }

    private void swap(String[] order, int a, int b) {
        String moved = order[a];
        order[a] = order[b];
        order[b] = moved;
        GlassworksSettings.putString(GlassworksSettings.KEY_TAB_ORDER, String.join(",", order));
        markChanged(false);
    }

    private TextView moveButton(String symbol, String description, boolean enabled, Runnable onClick) {
        TextView button = text(symbol, 20, TidalStyle.PRIMARY_TEXT);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(description);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.25f);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(TidalStyle.RIPPLE), null, null));
        button.setOnClickListener(v -> onClick.run());
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        return button;
    }

    private void showTextInput(String title, String current, boolean secret, StringSetter setter) {
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        EditText field = input(title, secret);
        field.setText(current);
        field.setSelection(field.getText().length());
        form.addView(field);

        Dialog dialogView = panel(title, form);
        LinearLayout buttons = dialogButtons(form);
        TextView cancel = dialogButton("Cancel", false);
        TextView save = dialogButton("Save", true);
        buttons.addView(cancel);
        buttons.addView(save);
        cancel.setOnClickListener(v -> dialogView.dismiss());
        save.setOnClickListener(v -> {
            setter.set(field.getText().toString().trim());
            dialogView.dismiss();
        });
        dialogView.show();
    }

    private void showChoice(String title, String[] options, int selected, IntSetter onPick) {
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        Dialog choice = panel(title, list);
        for (int i = 0; i < options.length; i++) {
            int index = i;
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(52));
            row.setPadding(dp(24), 0, dp(24), 0);
            row.setBackground(ripple());
            row.addView(text(options[i], TidalStyle.TITLE_SP, TidalStyle.PRIMARY_TEXT),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView check = text(i == selected ? "✓" : "", 18, TidalStyle.PRIMARY_TEXT);
            row.addView(check);
            row.setOnClickListener(v -> {
                onPick.set(index);
                choice.dismiss();
            });
            list.addView(row);
        }
        choice.show();
    }

    /** A rounded dialog panel with a title above {@code body}. */
    private Dialog panel(String title, LinearLayout body) {
        Dialog panel = new Dialog(activity, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(20), 0, dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(TidalStyle.DIALOG_BACKGROUND);
        background.setCornerRadius(dp(16));
        root.setBackground(background);

        TextView titleView = text(title, TidalStyle.PAGE_TITLE_SP, TidalStyle.PRIMARY_TEXT);
        titleView.setPadding(dp(24), 0, dp(24), dp(12));
        root.addView(titleView);
        if (body.getChildCount() > 0 && body.getChildAt(0) instanceof EditText) {
            body.setPadding(dp(24), 0, dp(24), 0);
        }
        root.addView(body);
        panel.setContentView(root);

        Window window = panel.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(Math.min(dp(360), activity.getResources().getDisplayMetrics().widthPixels - dp(48)),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
        return panel;
    }

    private LinearLayout dialogButtons(LinearLayout form) {
        LinearLayout buttons = new LinearLayout(activity);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        buttons.setPadding(0, dp(16), 0, 0);
        form.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return buttons;
    }

    private TextView dialogButton(String label, boolean primary) {
        TextView button = text(label, TidalStyle.TITLE_SP, primary ? Color.BLACK : TidalStyle.PRIMARY_TEXT);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(20), dp(10), dp(20), dp(10));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(primary ? Color.WHITE : Color.TRANSPARENT);
        shape.setCornerRadius(dp(24));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(TidalStyle.RIPPLE), shape, shape));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(dp(8));
        button.setLayoutParams(params);
        return button;
    }

    private EditText input(String hint, boolean secret) {
        EditText field = new EditText(activity);
        field.setHint(hint);
        field.setSingleLine(true);
        field.setTextColor(TidalStyle.PRIMARY_TEXT);
        field.setHintTextColor(TidalStyle.SWITCH_TRACK_OFF);
        field.setTextSize(TypedValue.COMPLEX_UNIT_SP, TidalStyle.TITLE_SP);
        field.setTypeface(TidalStyle.medium(activity));
        field.setBackgroundTintList(ColorStateList.valueOf(TidalStyle.SECONDARY_TEXT));
        field.setInputType(InputType.TYPE_CLASS_TEXT | (secret
                ? InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        return field;
    }

    // endregion

    // region Rows

    private void addTopBar(String title) {
        LinearLayout topBar = new LinearLayout(activity);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setMinimumHeight(dp(56));
        topBar.setPadding(dp(4), 0, dp(16), 0);

        View back;
        Drawable icon = TidalStyle.backIcon(activity);
        if (icon != null) {
            ImageView image = new ImageView(activity);
            image.setImageDrawable(icon);
            image.setScaleType(ImageView.ScaleType.CENTER);
            back = image;
        } else {
            TextView arrow = text("←", 22, TidalStyle.PRIMARY_TEXT);
            arrow.setGravity(Gravity.CENTER);
            back = arrow;
        }
        back.setContentDescription("Back");
        back.setBackground(new RippleDrawable(ColorStateList.valueOf(TidalStyle.RIPPLE), null, null));
        back.setOnClickListener(v -> dialog.dismiss());
        topBar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView titleView = text(title, TidalStyle.PAGE_TITLE_SP, TidalStyle.PRIMARY_TEXT);
        titleView.setPadding(dp(4), 0, 0, 0);
        topBar.addView(titleView);
        content.addView(topBar);
    }

    private void section(String title) {
        if (title.isEmpty()) {
            content.addView(new View(activity), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(16)));
            return;
        }
        TextView header = text(title, TidalStyle.SECTION_SP, TidalStyle.PRIMARY_TEXT);
        header.setTypeface(TidalStyle.bold(activity));
        header.setPadding(dp(16), dp(24), dp(16), dp(8));
        content.addView(header);
    }

    private LinearLayout rowContainer(int orientation) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(orientation);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        row.setPadding(dp(16), dp(16), dp(16), dp(16));
        return row;
    }

    /** Title with an optional subtitle, for the left side of a row. */
    private LinearLayout texts(String title, String summary, TextView[] summaryOut) {
        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, TidalStyle.TITLE_SP, TidalStyle.PRIMARY_TEXT));
        TextView summaryView = text(summary == null ? "" : summary, TidalStyle.SUBTITLE_SP, TidalStyle.SECONDARY_TEXT);
        summaryView.setPadding(0, dp(4), 0, 0);
        summaryView.setVisibility(summary == null ? View.GONE : View.VISIBLE);
        texts.addView(summaryView);
        if (summaryOut != null) {
            summaryOut[0] = summaryView;
        }
        return texts;
    }

    private void categoryRow(String title, StringGetter summary, Runnable onClick) {
        LinearLayout row = rowContainer(LinearLayout.HORIZONTAL);
        TextView[] summaryView = new TextView[1];
        row.addView(texts(title, "", summaryView),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.setBackground(ripple());
        row.setOnClickListener(v -> onClick.run());
        refreshers.add(() -> summaryView[0].setText(summary.get()));
        summaryView[0].setText(summary.get());
        content.addView(row);
    }

    private void actionRow(String title, String summary, Runnable onClick, BooleanGetter enabledWhen) {
        LinearLayout row = rowContainer(LinearLayout.HORIZONTAL);
        row.addView(texts(title, summary, null),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.setBackground(ripple());
        row.setOnClickListener(v -> onClick.run());
        addRow(row, enabledWhen);
    }

    private void inputRow(String title, String key, StringGetter getter, boolean secret) {
        LinearLayout row = rowContainer(LinearLayout.HORIZONTAL);
        TextView[] summaryView = new TextView[1];
        row.addView(texts(title, "", summaryView),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Runnable update = () -> {
            String value = getter.get();
            summaryView[0].setText(value.isEmpty() ? "Not set"
                    : secret ? "••••••••" + value.substring(Math.max(0, value.length() - 4)) : value);
        };
        row.setBackground(ripple());
        row.setOnClickListener(v -> showTextInput(title, getter.get(), secret, value -> {
            if (!value.equals(getter.get())) {
                GlassworksSettings.putString(key, value.isEmpty() ? null : value);
                // A different API account needs a new session.
                LastFmSettings.logOut();
            }
            refresh();
        }));
        refreshers.add(update);
        update.run();
        addRow(row, null);
    }

    private void switchRow(String title, String summary, BooleanGetter getter, BooleanSetter setter,
                           BooleanGetter enabledWhen) {
        boolean live = buildingLiveRows;
        LinearLayout row = rowContainer(LinearLayout.HORIZONTAL);
        row.addView(texts(title, summary, null),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TidalToggle toggle = new TidalToggle(activity);
        toggle.setChecked(getter.get(), false);
        toggle.setOnChangeListener(checked -> {
            setter.set(checked);
            markChanged(live);
        });
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        toggleParams.setMarginStart(dp(16));
        row.addView(toggle, toggleParams);
        row.setBackground(ripple());
        row.setOnClickListener(v -> toggle.toggle());

        refreshers.add(() -> toggle.setChecked(getter.get(), false));
        addRow(row, enabledWhen);
    }

    private void choiceRow(String title, String[] options, IntGetter getter, IntSetter setter,
                           BooleanGetter enabledWhen) {
        boolean live = buildingLiveRows;
        LinearLayout row = rowContainer(LinearLayout.HORIZONTAL);
        row.addView(text(title, TidalStyle.TITLE_SP, TidalStyle.PRIMARY_TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = text("", TidalStyle.TITLE_SP, TidalStyle.SECONDARY_TEXT);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.setMarginStart(dp(16));
        row.addView(value, valueParams);

        Runnable update = () -> value.setText(options[Math.max(0, Math.min(options.length - 1, getter.get()))]);
        row.setBackground(ripple());
        row.setOnClickListener(v -> showChoice(title, options, getter.get(), index -> {
            if (index != getter.get()) {
                setter.set(index);
                update.run();
                markChanged(live);
            }
        }));
        refreshers.add(update);
        update.run();
        addRow(row, enabledWhen);
    }

    private void sliderRow(String title, String unit, int min, int max,
                           IntGetter getter, IntSetter setter, BooleanGetter enabledWhen) {
        boolean live = buildingLiveRows;
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(16), dp(16), dp(8));
        // The bar reaches into the padding so its thumb is not cut off at either end.
        row.setClipToPadding(false);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(text(title, TidalStyle.TITLE_SP, TidalStyle.PRIMARY_TEXT),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView valueView = text("", TidalStyle.TITLE_SP, TidalStyle.SECONDARY_TEXT);
        header.addView(valueView);
        row.addView(header);

        SeekBar seekBar = new SeekBar(activity);
        seekBar.setMin(min);
        seekBar.setMax(max);
        seekBar.setProgress(getter.get());
        seekBar.setProgressTintList(ColorStateList.valueOf(TidalStyle.SWITCH_TRACK_ON));
        seekBar.setThumbTintList(ColorStateList.valueOf(TidalStyle.PRIMARY_TEXT));
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(TidalStyle.SLIDER_TRACK));
        // Keep room for the thumb at both ends, and pull the bar out by the same amount so the
        // track lines up with the row's text.
        seekBar.setPadding(dp(10), dp(12), dp(10), dp(12));
        valueView.setText(getter.get() + " " + unit);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                valueView.setText(progress + " " + unit);
                if (fromUser) {
                    setter.set(progress);
                    markChanged(live);
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
        seekParams.setMarginStart(-dp(10));
        seekParams.setMarginEnd(-dp(10));
        row.addView(seekBar, seekParams);

        refreshers.add(() -> {
            seekBar.setProgress(getter.get());
            valueView.setText(getter.get() + " " + unit);
        });
        addRow(row, enabledWhen);
    }

    /** A short explanation below the previous row. */
    private void note(String value) {
        TextView note = text(value, TidalStyle.NOTE_SP, TidalStyle.SECONDARY_TEXT);
        note.setTypeface(TidalStyle.medium(activity));
        note.setPadding(dp(16), 0, dp(16), dp(8));
        content.addView(note);
    }

    private void footer(String value) {
        TextView footer = text(value, TidalStyle.NOTE_SP, TidalStyle.SECONDARY_TEXT);
        footer.setTypeface(TidalStyle.medium(activity));
        footer.setPadding(dp(16), dp(24), dp(16), 0);
        content.addView(footer);
    }

    private void addRow(View row, BooleanGetter enabledWhen) {
        if (enabledWhen != null) {
            conditionalRows.add(row);
            conditions.add(enabledWhen);
        }
        content.addView(row);
    }

    // endregion

    private void updateConditionalRows() {
        for (int i = 0; i < conditionalRows.size(); i++) {
            View row = conditionalRows.get(i);
            boolean enabled = conditions.get(i).get();
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

    private Drawable ripple() {
        return new RippleDrawable(ColorStateList.valueOf(TidalStyle.RIPPLE), null,
                new ColorDrawable(Color.WHITE));
    }

    private TextView text(String value, float sizeSp, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTypeface(TidalStyle.demiBold(activity));
        return view;
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

    private interface StringGetter {
        String get();
    }

    private interface StringSetter {
        void set(String value);
    }
}
