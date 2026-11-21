package net.kdt.pojavlaunch.customcontrols.buttons;

import static net.kdt.pojavlaunch.LwjglGlfwKeycode.GLFW_KEY_UNKNOWN;
import static org.lwjgl.glfw.CallbackBridge.sendKeyPress;
import static org.lwjgl.glfw.CallbackBridge.sendMouseButton;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;

import net.kdt.pojavlaunch.EfficientAndroidLWJGLKeycode;
import net.kdt.pojavlaunch.LwjglGlfwKeycode;
import net.kdt.pojavlaunch.MainActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.handleview.EditControlSideDialog;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import org.lwjgl.glfw.CallbackBridge;

@SuppressLint({"ViewConstructor", "AppCompatCustomView"})
public class ControlButton extends TextView implements ControlInterface {
    private final Paint mRectPaint = new Paint();
    protected ControlData mProperties;
    private final ControlLayout mControlLayout;

    /* Cache value from the ControlData radius for drawing purposes */
    private float mComputedRadius;
    private Drawable mIcon;
    private boolean mHoldFired;
    private static boolean sHoldHintShown;
    private final Runnable mHoldPress = () -> {
        mHoldFired = true;
        sendKeyPresses(true);
    };

    protected boolean mIsToggled = false;
    protected boolean mIsPointerOutOfBounds = false;

    public ControlButton(ControlLayout layout, ControlData properties) {
        super(layout.getContext());
        mControlLayout = layout;
        setGravity(Gravity.CENTER);
        setAllCaps(LauncherPreferences.PREF_BUTTON_ALL_CAPS);
        setTextColor(Color.WHITE);
        setPadding(4, 4, 4, 4);
        setTextSize(14); // Nullify the default size setting
        setShadowLayer(Tools.dpToPx(2), 0, Tools.dpToPx(1), 0xE6000000);
        setOutlineProvider(null); // Disable shadow casting, removing one drawing pass

        //setOnLongClickListener(this);

        //When a button is created, the width/height has yet to be processed to fit the scaling.
        setProperties(preProcessProperties(properties, layout));

        injectBehaviors();
    }

    @Override
    public View getControlView() {return this;}

    public ControlData getProperties() {
        return mProperties;
    }

    public void setProperties(ControlData properties, boolean changePos) {
        mProperties = properties;
        ControlInterface.super.setProperties(properties, changePos);
        mComputedRadius = ControlInterface.super.computeCornerRadius(mProperties.cornerRadius);

        if (mProperties.isToggle) {
            //For the toggle layer
            final TypedValue value = new TypedValue();
            getContext().getTheme().resolveAttribute(R.attr.colorAccent, value, true);
            mRectPaint.setColor(value.data);
            mRectPaint.setAlpha(128);
        } else {
            mRectPaint.setColor(Color.WHITE);
            mRectPaint.setAlpha(60);
        }

        useIcon(properties.icon);
        setText(mIcon == null ? properties.name : "");
        setContentDescription(properties.name.replace('\n', ' '));
    }

    protected void useIcon(String name) {
        int icon = iconRes(name);
        mIcon = icon == 0 ? null : AppCompatResources.getDrawable(getContext(), icon).mutate();
        invalidate();
    }

    public static int iconRes(String name) {
        if (name == null) return 0;
        switch (name) {
            case "menu": return R.drawable.brilliant_icon_menu;
            case "more": return R.drawable.brilliant_icon_chevron_down;
            case "chat": return R.drawable.brilliant_icon_chat;
            case "pause": return R.drawable.brilliant_icon_pause;
            case "jump": return R.drawable.brilliant_icon_arrow_up;
            case "crouch": return R.drawable.brilliant_icon_arrow_down;
            case "sprint": return R.drawable.brilliant_icon_zap;
            case "inventory": return R.drawable.brilliant_icon_grid;
            case "drop": return R.drawable.brilliant_icon_download;
            case "close": return R.drawable.brilliant_icon_close;
            case "mouse": return R.drawable.brilliant_icon_mouse_right;
            case "players": return R.drawable.brilliant_icon_users;
            case "camera": return R.drawable.brilliant_icon_camera;
            case "keyboard": return R.drawable.brilliant_icon_keyboard;
            case "expand": return R.drawable.brilliant_icon_chevron_left;
            case "collapse": return R.drawable.brilliant_icon_chevron_right;
            case "eye": return R.drawable.brilliant_icon_eye;
            case "eye-closed": return R.drawable.brilliant_icon_eye_closed;
            default: return 0;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mIsToggled || (!mProperties.isToggle && isActivated()))
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), mComputedRadius, mComputedRadius, mRectPaint);
        if (mIcon != null) {
            int size = Math.round(Math.min(getWidth(), getHeight()) * 0.5f);
            int left = (getWidth() - size) / 2, top = (getHeight() - size) / 2, shadow = Math.max(1, Math.round(Tools.dpToPx(1)));
            mIcon.setTint(0xCC000000);
            boolean bare = Color.alpha(mProperties.bgColor) == 0 && mProperties.strokeWidth == 0;
            int[][] offsets = bare ? new int[][]{{-shadow, 0}, {shadow, 0}, {0, -shadow}, {0, shadow * 2}} : new int[][]{{0, shadow}};
            for (int[] offset : offsets) {
                mIcon.setBounds(left + offset[0], top + offset[1], left + size + offset[0], top + size + offset[1]);
                mIcon.draw(canvas);
            }
            mIcon.setBounds(left, top, left + size, top + size);
            mIcon.setTint(Color.WHITE);
            mIcon.draw(canvas);
        }
    }


    public void loadEditValues(EditControlSideDialog editControlPopup){
        editControlPopup.loadValues(getProperties());
    }

    /** Add another instance of the ControlButton to the parent layout */
    public void cloneButton(){
        ControlData cloneData = new ControlData(getProperties());
        cloneData.dynamicX = "0.5 * ${screen_width}";
        cloneData.dynamicY = "0.5 * ${screen_height}";
        ((ControlLayout) getParent()).addControlButton(cloneData);
    }

    /** Remove any trace of this button from the layout */
    public void removeButton() {
        getControlLayoutParent().getLayout().mControlDataList.remove(getProperties());
        getControlLayoutParent().removeView(this);
    }


    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()){
            case MotionEvent.ACTION_MOVE:
                //Send the event to be taken as a mouse action
                if(getProperties().passThruEnabled && CallbackBridge.isGrabbing()){
                    View gameSurface = getControlLayoutParent().getGameSurface();
                    if(gameSurface != null) gameSurface.dispatchTouchEvent(event);
                }

                //If out of bounds
                if(event.getX() < getControlView().getLeft() || event.getX() > getControlView().getRight() ||
                        event.getY() < getControlView().getTop()  || event.getY() > getControlView().getBottom()){
                    if(getProperties().isSwipeable && !mIsPointerOutOfBounds){
                        //Remove keys
                        if(!triggerToggle()) {
                            sendKeyPresses(false);
                        }
                    }
                    mIsPointerOutOfBounds = true;
                    getControlLayoutParent().onTouch(this, event);
                    break;
                }

                //Else if we now are in bounds
                if(mIsPointerOutOfBounds) {
                    getControlLayoutParent().onTouch(this, event);
                    //RE-press the button
                    if(getProperties().isSwipeable && !getProperties().isToggle){
                        sendKeyPresses(true);
                    }
                }
                mIsPointerOutOfBounds = false;
                break;

            case MotionEvent.ACTION_DOWN: // 0
            case MotionEvent.ACTION_POINTER_DOWN: // 5
                if(getProperties().holdToPress){
                    mHoldFired = false;
                    postDelayed(mHoldPress, 350);
                }else if(!getProperties().isToggle){
                    sendKeyPresses(true);
                }
                break;

            case MotionEvent.ACTION_UP: // 1
            case MotionEvent.ACTION_CANCEL: // 3
            case MotionEvent.ACTION_POINTER_UP: // 6
                if(getProperties().passThruEnabled){
                    View gameSurface = getControlLayoutParent().getGameSurface();
                    if(gameSurface != null) gameSurface.dispatchTouchEvent(event);
                }
                if(mIsPointerOutOfBounds) getControlLayoutParent().onTouch(this, event);
                mIsPointerOutOfBounds = false;

                removeCallbacks(mHoldPress);
                if(getProperties().holdToPress && !mHoldFired){
                    if(!sHoldHintShown) net.kdt.pojavlaunch.BrilliantToast.show(getContext(), R.string.brilliant_hold_hint);
                    sHoldHintShown = true;
                    break;
                }
                mHoldFired = false;
                if(!triggerToggle()) {
                    sendKeyPresses(false);
                }
                break;

            default:
                return false;
        }

        return super.onTouchEvent(event);
    }



    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean triggerToggle(){
        //returns true a the toggle system is triggered
        if(mProperties.isToggle){
            mIsToggled = !mIsToggled;
            invalidate();
            sendKeyPresses(mIsToggled);
            return true;
        }
        return false;
    }

    public void sendKeyPresses(boolean isDown){
        setActivated(isDown);
        for(int keycode : mProperties.keycodes){
            if(keycode >= GLFW_KEY_UNKNOWN){
                sendKeyPress(keycode, EfficientAndroidLWJGLKeycode.getLwjglChar(keycode), CallbackBridge.getCurrentMods(), isDown);
                CallbackBridge.setModifiers(keycode, isDown);
            }else{
                sendSpecialKey(keycode, isDown);
            }
        }
    }

    private void sendSpecialKey(int keycode, boolean isDown){
        switch (keycode) {
            case ControlData.SPECIALBTN_KEYBOARD:
                if(isDown) MainActivity.switchKeyboardState();
                break;

            case ControlData.SPECIALBTN_TOGGLECTRL:
                if(isDown) {
                    getControlLayoutParent().toggleControlVisible();
                    if ("eye".equals(mProperties.icon)) useIcon(getControlLayoutParent().areControlVisible() ? "eye" : "eye-closed");
                }
                break;

            case ControlData.SPECIALBTN_VIRTUALMOUSE:
                if(isDown) MainActivity.toggleMouse(getContext());
                break;

            case ControlData.SPECIALBTN_MOUSEPRI:
                sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT, isDown);
                break;

            case ControlData.SPECIALBTN_MOUSEMID:
                sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_MIDDLE, isDown);
                break;

            case ControlData.SPECIALBTN_MOUSESEC:
                sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT, isDown);
                break;

            case ControlData.SPECIALBTN_SCROLLDOWN:
                if (!isDown) CallbackBridge.sendScroll(0, 1f);
                break;

            case ControlData.SPECIALBTN_SCROLLUP:
                if (!isDown) CallbackBridge.sendScroll(0, -1f);
                break;

            case ControlData.SPECIALBTN_MENU:
                mControlLayout.notifyAppMenu();
                break;

            case ControlData.SPECIALBTN_MOUSEBCK:
                sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_4, isDown);
                break;

            case ControlData.SPECIALBTN_MOUSEFWD:
                sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_5, isDown);
                break;

            default:
                net.kdt.pojavlaunch.BrilliantToast.show(getContext(), getContext().getString(R.string.error_key_unsupported), true);
                break;
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
