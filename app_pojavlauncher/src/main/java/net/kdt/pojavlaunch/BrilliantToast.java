package net.kdt.pojavlaunch;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class BrilliantToast {
    private static final Object TAG = new Object();

    private BrilliantToast() {}

    public static void show(Context context, int text) {
        show(context, context.getString(text), false);
    }

    public static void show(Context context, CharSequence text) {
        show(context, text, false);
    }

    public static void show(Context context, CharSequence text, boolean longer) {
        Activity activity = activityOf(context);
        if (activity == null || activity.isFinishing()) {
            Toast.makeText(context, text, longer ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
            return;
        }
        activity.runOnUiThread(() -> {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) return;
            for (int i = root.getChildCount() - 1; i >= 0; i--) {
                View child = root.getChildAt(i);
                if (child.getTag() == TAG) root.removeView(child);
            }
            float d = activity.getResources().getDisplayMetrics().density;
            TextView view = new TextView(activity);
            view.setTag(TAG);
            view.setText(text);
            view.setTextSize(14);
            view.setTextColor(activity.getResources().getColor(R.color.primary_text, activity.getTheme()));
            view.setBackgroundResource(R.drawable.mc_toast);
            view.setPadding((int) (19 * d), (int) (10 * d), (int) (16 * d), (int) (12 * d));
            view.setMaxWidth((int) (420 * d));
            view.setElevation(12 * d);
            view.setOnClickListener(v -> root.removeView(v));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            params.topMargin = (int) (64 * d);
            root.addView(view, params);
            view.setAlpha(0f);
            view.setTranslationY(-12 * d);
            view.animate().alpha(1f).translationY(0f).setDuration(280).start();
            view.postDelayed(() -> view.animate().alpha(0f).setDuration(250).withEndAction(() -> root.removeView(view)).start(), longer ? 4000 : 2500);
        });
    }

    private static Activity activityOf(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
