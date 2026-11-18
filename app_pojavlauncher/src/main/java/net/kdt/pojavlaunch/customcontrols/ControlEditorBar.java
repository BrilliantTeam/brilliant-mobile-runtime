package net.kdt.pojavlaunch.customcontrols;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import net.kdt.pojavlaunch.BrilliantLaunch;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;

@SuppressLint("ViewConstructor")
public class ControlEditorBar extends LinearLayout {
    private static final int ACCENT = 0xFFF6E0A4;
    private static final int ACCENT_INK = 0xFF111111;
    private static final int[] CONTEXTS = {R.string.brilliant_editor_game, R.string.brilliant_editor_menu, R.string.brilliant_editor_all};

    private final ControlLayout mLayout;
    private final EditorExitable mExit;
    private final TextView[] mTabs = new TextView[CONTEXTS.length];
    private int mContext = 0;
    private boolean mAtBottom = false;
    private String mSnapshot;

    public ControlEditorBar(Context context, ControlLayout layout, EditorExitable exit) {
        super(context);
        mLayout = layout;
        mExit = exit;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundColor(0xF226282C);
        int pad = dp(6);
        setPadding(pad, pad, pad, pad);
        setTranslationZ(15);
        setClickable(true);

        for (int i = 0; i < CONTEXTS.length; i++) {
            final int index = i;
            mTabs[i] = chip(CONTEXTS[i], v -> showContext(index));
            addView(mTabs[i]);
        }

        TextView hint = new TextView(context);
        hint.setText(R.string.brilliant_editor_hint);
        hint.setTextColor(0xFFB8BEC4);
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
        TextView save = chip(R.string.brilliant_editor_save, v -> save());
        paint(save, ACCENT, ACCENT_INK);
        addView(save);

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
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(14);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(40));
        view.setMinWidth(dp(48));
        view.setPadding(dp(12), 0, dp(12), 0);
        view.setSingleLine(true);
        view.setOnClickListener(listener);
        paint(view, 0x26FFFFFF, Color.WHITE);
        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.setMarginStart(dp(4));
        view.setLayoutParams(params);
        return view;
    }

    private void paint(TextView view, int background, int ink) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(background);
        shape.setCornerRadius(dp(6));
        view.setBackground(shape);
        view.setTextColor(ink);
    }

    private void showContext(int index) {
        mContext = index;
        for (int i = 0; i < mTabs.length; i++) {
            boolean on = i == index;
            paint(mTabs[i], on ? 0xFFFFFFFF : 0x26FFFFFF, on ? ACCENT_INK : Color.WHITE);
            mTabs[i].setSelected(on);
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
        PopupMenu menu = new PopupMenu(getContext(), anchor);
        menu.getMenu().add(0, 0, 0, R.string.brilliant_editor_add_button);
        menu.getMenu().add(0, 1, 1, R.string.brilliant_editor_add_drawer);
        menu.getMenu().add(0, 2, 2, R.string.brilliant_editor_add_joystick);
        menu.setOnMenuItemClickListener(item -> {
            String centerX = "${screen_width} / 2 - ${width} / 2";
            String centerY = "${screen_height} / 2 - ${height} / 2";
            if (item.getItemId() == 0) {
                ControlData data = ControlData.brilliantStyle(new ControlData(getContext().getString(R.string.brilliant_editor_new), new int[]{}, centerX, centerY, 64, 48, false));
                applyContext(data);
                mLayout.addControlButton(data);
            } else if (item.getItemId() == 1) {
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
            return true;
        });
        menu.show();
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
        Toast.makeText(getContext(), R.string.brilliant_editor_saved, Toast.LENGTH_SHORT).show();
        mExit.exitEditor();
    }

    private static void setDefault(File file) {
        LauncherPreferences.DEFAULT_PREF.edit().putString("defaultCtrl", file.getAbsolutePath()).apply();
        LauncherPreferences.PREF_DEFAULTCTRL_PATH = file.getAbsolutePath();
    }
}
