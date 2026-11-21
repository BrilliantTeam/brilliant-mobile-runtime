package net.kdt.pojavlaunch.customcontrols;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import net.kdt.pojavlaunch.BrilliantLaunch;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;

@SuppressLint("ViewConstructor")
public class ControlEditorBar extends LinearLayout {
    private static final int[] CONTEXTS = {R.string.brilliant_editor_game, R.string.brilliant_editor_menu, R.string.brilliant_editor_all};

    private final ControlLayout mLayout;
    private final EditorExitable mExit;
    private final TextView[] mTabs = new TextView[CONTEXTS.length];
    private android.graphics.Typeface mTabFont;
    private PopupWindow mPopup;
    private int mContext = 0;
    private boolean mAtBottom = false;
    private String mSnapshot;

    public ControlEditorBar(Context context, ControlLayout layout, EditorExitable exit) {
        super(context);
        mLayout = layout;
        mExit = exit;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundResource(R.drawable.brilliant_float);
        int pad = dp(6);
        setPadding(pad, pad, pad, dp(4));
        setTranslationZ(15);
        setClickable(true);

        LinearLayout tabs = new LinearLayout(context);
        tabs.setBackgroundResource(R.drawable.mc_sunken);
        tabs.setPadding(dp(2), dp(2), dp(2), dp(2));
        for (int i = 0; i < CONTEXTS.length; i++) {
            final int index = i;
            TextView tab = new TextView(context);
            tab.setText(CONTEXTS[i]);
            tab.setTextSize(14);
            tab.setGravity(Gravity.CENTER);
            tab.setMinHeight(dp(36));
            tab.setPadding(dp(16), 0, dp(16), 0);
            tab.setSingleLine(true);
            tab.setBackgroundResource(R.drawable.mc_segment);
            tab.setTextColor(getResources().getColorStateList(R.color.mc_segment_text, context.getTheme()));
            tab.setOnClickListener(v -> showContext(index));
            mTabFont = tab.getTypeface();
            mTabs[i] = tab;
            tabs.addView(tab);
        }
        addView(tabs, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(context);
        hint.setText(R.string.brilliant_editor_hint);
        hint.setTextColor(getResources().getColor(R.color.secondary_text, context.getTheme()));
        hint.setTextSize(12);
        hint.setSingleLine(true);
        hint.setEllipsize(TextUtils.TruncateAt.END);
        hint.setPadding(dp(10), 0, dp(6), 0);
        addView(hint, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        TextView add = chip(R.string.brilliant_editor_add, null);
        add.setOnClickListener(v -> openAddMenu(add));
        addView(add);
        TextView flip = chip(R.string.brilliant_editor_flip, v -> flip());
        flip.setContentDescription(context.getString(R.string.brilliant_editor_flip_label));
        addView(flip);
        addView(chip(R.string.brilliant_editor_reset, v -> confirmReset()));
        addView(chip(R.string.brilliant_editor_cancel, v -> requestCancel()));
        addView(button(R.string.brilliant_editor_save, v -> save(), R.style.BrilliantChip_Accent));

        mSnapshot = mLayout.snapshot();
        settle();
        showContext(0);
    }

    private void settle() {
        postDelayed(() -> mSnapshot = mLayout.snapshot(), 400);
    }

    public FrameLayout.LayoutParams placement() {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, mAtBottom ? Gravity.BOTTOM : Gravity.TOP);
    }

    private int dp(float value) {
        return (int) Tools.dpToPx(value);
    }

    private TextView chip(int text, OnClickListener listener) {
        return button(text, listener, R.style.BrilliantChip);
    }

    private TextView button(int text, OnClickListener listener, int style) {
        TextView view = new TextView(getContext(), null, 0, style);
        view.setText(text);
        view.setOnClickListener(listener);
        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.setMarginStart(dp(6));
        view.setLayoutParams(params);
        return view;
    }

    private void showContext(int index) {
        mContext = index;
        for (int i = 0; i < mTabs.length; i++) {
            boolean on = i == index;
            mTabs[i].setSelected(on);
            mTabs[i].setTypeface(mTabFont, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
        mLayout.filterForEditor(index != 1, index != 0);
    }

    private void flip() {
        mAtBottom = !mAtBottom;
        setLayoutParams(placement());
    }

    private void applyContext(ControlData data) {
        data.displayInGame = mContext != 1;
        data.displayInMenu = mContext != 0;
    }

    private void openAddMenu(View anchor) {
        LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(VERTICAL);
        list.setBackgroundResource(R.drawable.background_control_editor);
        PopupWindow popup = new PopupWindow(list, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, false);
        mPopup = popup;
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(8));
        int[] labels = {R.string.brilliant_editor_add_button, R.string.brilliant_editor_add_drawer, R.string.brilliant_editor_add_joystick};
        int[] icons = {R.drawable.brilliant_icon_grid, R.drawable.brilliant_icon_menu, R.drawable.brilliant_icon_gamepad};
        for (int i = 0; i < labels.length; i++) {
            final int kind = i;
            TextView item = (TextView) android.view.LayoutInflater.from(getContext()).inflate(R.layout.brilliant_menu_item, list, false);
            item.setText(labels[i]);
            item.setCompoundDrawablesRelativeWithIntrinsicBounds(icons[i], 0, 0, 0);
            item.setMinWidth(dp(200));
            item.setOnClickListener(v -> {
                popup.dismiss();
                addControl(kind);
            });
            list.addView(item);
        }
        popup.showAsDropDown(anchor);
    }

    private void addControl(int kind) {
        String centerX = "${screen_width} / 2 - ${width} / 2";
        String centerY = "${screen_height} / 2 - ${height} / 2";
        if (kind == 0) {
            ControlData data = ControlData.brilliantStyle(new ControlData(getContext().getString(R.string.brilliant_editor_new), new int[]{}, centerX, centerY, 64, 48, false));
            applyContext(data);
            mLayout.addControlButton(data);
        } else if (kind == 1) {
            ControlDrawerData drawer = new ControlDrawerData();
            ControlData.brilliantStyle(drawer.properties);
            drawer.properties.dynamicX = centerX;
            drawer.properties.dynamicY = centerY;
            applyContext(drawer.properties);
            mLayout.addDrawer(drawer);
        } else {
            ControlJoystickData joystick = new ControlJoystickData();
            applyContext(joystick);
            mLayout.addJoystickButton(joystick);
        }
        mLayout.editNewest();
    }

    private void confirmReset() {
        new AlertDialog.Builder(getContext())
                .setMessage(R.string.brilliant_editor_reset_confirm)
                .setNegativeButton(R.string.brilliant_editor_keep, null)
                .setPositiveButton(R.string.brilliant_editor_reset, (d, w) -> {
                    File preset = BrilliantLaunch.controlsFile();
                    try {
                        mLayout.loadLayout(preset.getAbsolutePath());
                    } catch (Exception e) {
                        Tools.showError(getContext(), e);
                        return;
                    }
                    setDefault(preset);
                    new File(Tools.CTRLMAP_PATH, BrilliantLaunch.CUSTOM_CONTROLS).delete();
                    settle();
                    showContext(mContext);
                })
                .show();
    }

    public boolean closePopup(boolean close) {
        if (mPopup == null || !mPopup.isShowing()) return false;
        if (close) mPopup.dismiss();
        return true;
    }

    public void requestCancel() {
        mLayout.removeEditWindow();
        if (mLayout.snapshot().equals(mSnapshot)) {
            mExit.exitEditor();
            return;
        }
        new AlertDialog.Builder(getContext())
                .setMessage(R.string.brilliant_editor_discard)
                .setNegativeButton(R.string.brilliant_editor_keep, null)
                .setPositiveButton(R.string.brilliant_editor_discard_ok, (d, w) -> mExit.exitEditor())
                .show();
    }

    private void save() {
        mLayout.removeEditWindow();
        File target = new File(Tools.CTRLMAP_PATH, BrilliantLaunch.CUSTOM_CONTROLS);
        try {
            if (!mLayout.snapshot().equals(mSnapshot) || target.isFile()) {
                mLayout.saveLayout(target.getAbsolutePath());
                setDefault(target);
            }
        } catch (Exception e) {
            Tools.showError(getContext(), e);
            return;
        }
        net.kdt.pojavlaunch.BrilliantToast.show(getContext(), R.string.brilliant_editor_saved);
        mExit.exitEditor();
    }

    private static void setDefault(File file) {
        LauncherPreferences.DEFAULT_PREF.edit().putString("defaultCtrl", file.getAbsolutePath()).apply();
        LauncherPreferences.PREF_DEFAULTCTRL_PATH = file.getAbsolutePath();
    }
}
