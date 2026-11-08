package com.skyclient;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/** Colors and small drawing helpers shared by the menu and the HUD. */
public final class Ui {
    private Ui() {}

    public static final int BG = 0xF2111119;
    public static final int SIDEBAR = 0xF20B0B11;
    public static final int CARD = 0xFF1B1B27;
    public static final int CARD_HOVER = 0xFF242436;
    public static final int TEXT = 0xFFECECF4;
    public static final int DIM = 0xFF8D8DA6;
    public static final int OFF = 0xFF3A3A4E;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int LINE = 0xFF2A2A3B;

    private static String toast;
    private static long toastUntil;

    public static void toast(String s) {
        toast = s;
        toastUntil = System.currentTimeMillis() + 3500;
    }

    public static String currentToast() {
        return System.currentTimeMillis() < toastUntil ? toast : null;
    }

    /** Current accent color as 0xRRGGBB. */
    public static int accent() {
        ClientConfig c = ClientConfig.get();
        if (c.rainbowAccent) {
            return MathHelper.hsvToRgb((System.currentTimeMillis() % 6000L) / 6000f, 0.6f, 1f) & 0xFFFFFF;
        }
        return LyricsConfig.rgb(c.accent, 0x7C5CFF);
    }

    public static int argb(int rgb, int a) {
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static String fmtTime(long ms) {
        long s = Math.max(0, ms) / 1000;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    /** Filled rectangle with rounded corners. */
    public static void round(DrawContext c, int x, int y, int w, int h, int r, int color) {
        if ((color >>> 24) == 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            c.fill(x, y, x + w, y + h, color);
            return;
        }
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            c.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
        c.fill(x, y + r, x + w, y + h - r, color);
    }
}
