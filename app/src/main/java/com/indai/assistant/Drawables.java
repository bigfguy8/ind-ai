package com.indai.assistant;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;

public final class Drawables {

    private Drawables() {}

    /** Solid rounded rectangle. */
    public static GradientDrawable rounded(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(Theme.dp(c, radiusDp));
        return g;
    }

    /** Rounded rectangle with a 1px-ish hairline stroke. */
    public static GradientDrawable outlined(Context c, int fill, int strokeColor,
                                            float radiusDp, float strokeDp) {
        GradientDrawable g = rounded(c, fill, radiusDp);
        g.setStroke(Theme.dp(c, strokeDp), strokeColor);
        return g;
    }

    /** Vertical gradient rounded rectangle. */
    public static GradientDrawable gradient(Context c, int top, int bottom, float radiusDp) {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{top, bottom});
        g.setCornerRadius(Theme.dp(c, radiusDp));
        return g;
    }

    /** Wrap a drawable in a ripple for tappable surfaces. */
    public static Drawable ripple(Context c, int rippleColor, Drawable content) {
        return new RippleDrawable(
                ColorStateList.valueOf(rippleColor), content, null);
    }

    /** Convenience: rounded solid + ripple in one call. */
    public static Drawable tappable(Context c, int fill, float radiusDp) {
        return ripple(c, 0x33FFFFFF, rounded(c, fill, radiusDp));
    }

    /** Chat bubble with a "tail" (one corner less round). */
    public static GradientDrawable bubble(Context c, int color,
                                          boolean fromUser) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        float r = Theme.dp(c, Theme.R_LG);
        float tail = Theme.dp(c, 6);
        if (fromUser) {
            g.setCornerRadii(new float[]{ r, r,  r, r,  tail, tail,  r, r });
        } else {
            g.setCornerRadii(new float[]{ r, r,  r, r,  r, r,  tail, tail });
        }
        return g;
    }
}
