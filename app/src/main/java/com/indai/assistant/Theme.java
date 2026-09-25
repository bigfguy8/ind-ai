package com.indai.assistant;

import android.content.Context;
import android.util.TypedValue;

public final class Theme {

    // ---- Colors ----
    public static final int BG               = 0xFF0A0E1A;
    public static final int BG_ELEVATED      = 0xFF0F1420;
    public static final int SURFACE          = 0xFF131826;
    public static final int SURFACE_HIGH     = 0xFF1A2132;
    public static final int SURFACE_STROKE   = 0x14FFFFFF;

    public static final int PRIMARY          = 0xFF00E5FF;
    public static final int PRIMARY_DEEP     = 0xFF00B8D4;
    public static final int PRIMARY_SOFT     = 0x1F00E5FF;
    public static final int ON_PRIMARY       = 0xFF001318;

    public static final int TEXT_PRIMARY     = 0xFFF5F7FA;
    public static final int TEXT_SECONDARY   = 0xFF8A95A8;
    public static final int TEXT_TERTIARY    = 0xFF5A6478;

    public static final int BUBBLE_AI        = 0xFF161C2B;
    public static final int BUBBLE_USER      = 0xFF00E5FF;
    public static final int BUBBLE_USER_TEXT = 0xFF001318;

    public static final int ERROR            = 0xFFFF5252;
    public static final int SUCCESS          = 0xFF4ADE80;

    // ---- Spacing (8px grid) ----
    public static final int S1 = 4;
    public static final int S2 = 8;
    public static final int S3 = 12;
    public static final int S4 = 16;
    public static final int S5 = 24;
    public static final int S6 = 32;
    public static final int S7 = 40;

    // ---- Radius ----
    public static final float R_SM   = 8f;
    public static final float R_MD   = 12f;
    public static final float R_LG   = 18f;
    public static final float R_XL   = 24f;
    public static final float R_PILL = 999f;

    // ---- Typography ----
    public static final float T_DISPLAY  = 26f;
    public static final float T_TITLE    = 18f;
    public static final float T_BODY     = 15.5f;
    public static final float T_LABEL    = 11f;
    public static final float T_CAPTION  = 12.5f;

    private Theme() {}

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }
}
