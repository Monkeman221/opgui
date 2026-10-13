package com.example.opgui;

/** Colours used by the menu (all ARGB). */
public final class Theme {
    private Theme() {}

    public static final int PANEL = 0xF2161B22;
    public static final int PANEL_LIGHT = 0xFF1E252E;
    public static final int BORDER = 0xFF30363D;
    public static final int ACCENT = 0xFF4C9AFF;
    public static final int TEXT = 0xFFE6EDF3;
    public static final int MUTED = 0xFF8B949E;

    public static final int BTN = 0xFF2A313C;
    public static final int GRAY = 0xFF3A424E;
    public static final int GREEN = 0xFF2A8A47;
    public static final int RED = 0xFFA83232;
    public static final int ORANGE = 0xFFB0661A;
    public static final int BLUE = 0xFF2F6FB5;
    public static final int PURPLE = 0xFF7A4FB5;
    public static final int TEAL = 0xFF1F8A8A;

    public static int lighten(int argb, float amt) {
        int a = argb >>> 24;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r = Math.min(255, r + (int) ((255 - r) * amt));
        g = Math.min(255, g + (int) ((255 - g) * amt));
        b = Math.min(255, b + (int) ((255 - b) * amt));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int darken(int argb, float amt) {
        int a = argb >>> 24;
        int r = (int) (((argb >> 16) & 0xFF) * (1f - amt));
        int g = (int) (((argb >> 8) & 0xFF) * (1f - amt));
        int b = (int) ((argb & 0xFF) * (1f - amt));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
