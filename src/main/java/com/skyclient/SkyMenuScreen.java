package com.skyclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SkyMenuScreen extends Screen {
    private static final String[] TABS = {"HUD", "PvP", "Lyrics", "Music", "Theme"};
    private static final int SB = 112;

    private int tab;
    private float scroll;
    private int maxScroll;
    private boolean needsRebuild = true;
    private boolean confirmReset;
    private String search = "";
    private boolean searchFocus;
    private float animT;
    private final long openedAt = System.currentTimeMillis();
    private final List<String> hudNames = new ArrayList<>();
    private Widgets.Widget active;
    private final List<List<Widgets.Widget>> pages = new ArrayList<>();

    private int wx, wy, ww, wh, cx, cy, cw, ch;

    public SkyMenuScreen(int tab) {
        super(Text.literal("Sky Client"));
        this.tab = Math.max(0, Math.min(TABS.length - 1, tab));
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void removed() {
        ClientConfig.save();
        LyricsConfig.save();
    }

    private void layout() {
        ww = Math.min(500, width - 16);
        wh = Math.min(310, height - 16);
        wx = (width - ww) / 2;
        wy = (height - wh) / 2;
        float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 160f);
        animT = 1f - (1f - t) * (1f - t);
        wy += (int) ((1f - animT) * 14f);
        cx = wx + SB + 12;
        cy = wy + 38;
        cw = ww - SB - 24;
        ch = wh - 38 - 22;
    }

    // ------------------------------------------------------------------ pages
    private void rebuild() {
        needsRebuild = false;
        active = null;
        pages.clear();
        ClientConfig cfg = ClientConfig.get();
        Runnable dirty = () -> { };

        // HUD
        List<Widgets.Widget> hud = new ArrayList<>();
        hudNames.clear();
        hud.add(new Widgets.ButtonW("Edit HUD layout (drag modules)", () -> client.setScreen(new HudEditorScreen(tab))));
        hudNames.add("");
        for (HudModule m : HudModules.ALL) {
            hud.add(Widgets.card(m.name, m.desc, m.cfg(), dirty));
            hudNames.add(m.name.toLowerCase(Locale.ROOT));
        }
        pages.add(hud);

        // PvP
        List<Widgets.Widget> pvp = new ArrayList<>();
        pvp.add(new Widgets.HeaderW("Gameplay"));
        pvp.add(Widgets.card("Always sprint", "Holds sprint while you move forward.", cfg.sprint, dirty));
        pvp.add(Widgets.card("Zoom", "Hold the zoom key to zoom in.", cfg.zoom, dirty));
        pvp.add(new Widgets.HeaderW("Keybinds"));
        pvp.add(new Widgets.InfoW(
                "Menu: " + SkyClientMod.keyName(SkyClientMod.menuKey)
                        + "    Zoom: " + SkyClientMod.keyName(SkyClientMod.zoomKey),
                "Lyrics toggle: " + SkyClientMod.keyName(SkyClientMod.lyricsToggleKey)
                        + "    Lyrics pause: " + SkyClientMod.keyName(SkyClientMod.lyricsPauseKey),
                "Change them in Options > Controls > Key Binds > Sky Client."));
        pvp.add(new Widgets.HeaderW("Fair play"));
        pvp.add(new Widgets.InfoW(
                "Sky Client only changes what YOU see (HUD, zoom, sprint toggle).",
                "It has no combat advantages such as reach, aim or auto-click."));
        pages.add(pvp);

        // Lyrics
        List<Widgets.Widget> lyr = new ArrayList<>();
        LyricsConfig lc = LyricsConfig.get();
        Runnable lyricsChanged = () -> { LyricsConfig.get().clamp(); LyricsPlayer.INSTANCE.applyVolume(); };
        lyr.add(new Widgets.ModuleCard("Sky lyrics", "Lyrics floating in the sky. Play a song in the Music tab.",
                () -> LyricsConfig.get().enabled, v -> LyricsConfig.get().enabled = v, new ArrayList<>()));
        for (Map.Entry<String, List<Widgets.Widget>> e : Widgets.optionsByGroup(lc, lyricsChanged).entrySet()) {
            lyr.add(new Widgets.ModuleCard(e.getKey().isEmpty() ? "Other" : e.getKey(), "", null, null, e.getValue()));
        }
        pages.add(lyr);

        // Music
        List<Widgets.Widget> mus = new ArrayList<>();
        mus.add(new Widgets.HeaderW("Now playing"));
        mus.add(new Widgets.PlayerW());
        mus.add(new Widgets.HeaderW("Library"));
        List<String> songs = LyricsPlayer.listSongs();
        if (songs.isEmpty()) {
            mus.add(new Widgets.InfoW("No songs yet. Put NAME.lrc (and optionally NAME.wav) in:",
                    ".minecraft/skyclient/songs/  - then press Refresh."));
        }
        for (String s : songs) mus.add(new Widgets.SongRowW(s));
        mus.add(new Widgets.ButtonW("Refresh list", () -> needsRebuild = true));
        mus.add(new Widgets.ButtonW("Open songs folder", () ->
                Util.getOperatingSystem().open(LyricsPlayer.songsDir().toFile())));
        mus.add(new Widgets.HeaderW("Playback"));
        List<Widgets.Widget> sync = Widgets.optionsByGroup(lc, lyricsChanged).get("Sync & audio");
        if (sync != null) mus.addAll(sync);
        pages.add(mus);

        // Theme
        List<Widgets.Widget> theme = new ArrayList<>();
        theme.add(new Widgets.HeaderW("Appearance"));
        theme.add(new Widgets.ColorW("Accent color", () -> ClientConfig.get().accent, v -> ClientConfig.get().accent = v));
        theme.add(new Widgets.ToggleW("Rainbow accent", () -> ClientConfig.get().rainbowAccent, v -> ClientConfig.get().rainbowAccent = v));
        theme.add(new Widgets.HeaderW("Layout"));
        theme.add(new Widgets.ButtonW("Edit HUD layout", () -> client.setScreen(new HudEditorScreen(tab))));
        theme.add(new Widgets.ButtonW("Reset HUD positions and sizes", () -> {
            ClientConfig.get().resetPositions();
            Ui.toast("HUD positions reset.");
        }));
        theme.add(new Widgets.HeaderW("HUD presets"));
        theme.add(new Widgets.PairW("Minimal", () -> applyPreset("minimal"), "PvP", () -> applyPreset("pvp")));
        theme.add(new Widgets.ButtonW("Everything", () -> applyPreset("full")));
        theme.add(new Widgets.HeaderW("Profiles"));
        for (int slot = 1; slot <= 3; slot++) {
            final int sl = slot;
            theme.add(new Widgets.PairW("Save slot " + sl, () -> Ui.toast(ClientConfig.saveProfile(sl)),
                    "Load slot " + sl, () -> { Ui.toast(ClientConfig.loadProfile(sl)); needsRebuild = true; }));
        }
        theme.add(new Widgets.HeaderW("Danger zone"));
        theme.add(new Widgets.ButtonW("Reset ALL settings (click twice)", () -> {
            if (confirmReset) {
                ClientConfig.resetAll();
                confirmReset = false;
                needsRebuild = true;
                Ui.toast("All settings reset.");
            } else {
                confirmReset = true;
                Ui.toast("Click again to confirm.");
            }
        }));
        pages.add(theme);
    }

    private void applyPreset(String p) {
        ClientConfig.get().applyPreset(p);
        Ui.toast("HUD preset applied: " + p);
    }

    private List<Widgets.Widget> page() {
        List<Widgets.Widget> p = pages.get(tab);
        if (tab != 0 || search.isEmpty() || hudNames.size() != p.size()) return p;
        String q = search.toLowerCase(Locale.ROOT);
        List<Widgets.Widget> out = new ArrayList<>();
        for (int i = 0; i < p.size(); i++) {
            if (i == 0 || hudNames.get(i).contains(q)) out.add(p.get(i));
        }
        return out;
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        try {
            renderInner(ctx, mx, my);
        } catch (Throwable t) {
            SkyClientMod.LOGGER.error("Sky Client menu render failed", t);
            try { ctx.disableScissor(); } catch (Throwable ignored) { }
            if (++renderErrors >= 3) close();
        }
    }

    private int renderErrors;

    private void safeRebuild() {
        try {
            rebuild();
        } catch (Throwable t) {
            SkyClientMod.LOGGER.error("Sky Client menu build failed", t);
            needsRebuild = false;
            pages.clear();
            for (int i = 0; i < TABS.length; i++) {
                pages.add(List.of(new Widgets.InfoW("This page failed to load.", String.valueOf(t))));
            }
        }
    }

    private void renderInner(DrawContext ctx, int mx, int my) {
        if (needsRebuild) safeRebuild();
        layout();
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int accent = Ui.accent();

        ctx.fill(0, 0, width, height, ((int) (0x8C * animT)) << 24);
        Ui.round(ctx, wx, wy, ww, wh, 7, Ui.BG);
        Ui.round(ctx, wx, wy, SB, wh, 7, Ui.SIDEBAR);
        ctx.fill(wx + SB - 7, wy, wx + SB, wy + wh, Ui.SIDEBAR);

        ctx.drawText(tr, Text.literal("SKY CLIENT").formatted(Formatting.BOLD), wx + 14, wy + 12, 0xFF000000 | accent, false);
        ctx.drawText(tr, "PvP suite  v1.1", wx + 14, wy + 24, Ui.DIM, false);

        for (int i = 0; i < TABS.length; i++) {
            int ty = wy + 44 + i * 24;
            boolean act = i == tab;
            boolean hov = Ui.in(mx, my, wx + 8, ty, SB - 16, 20);
            if (act) {
                Ui.round(ctx, wx + 8, ty, SB - 16, 20, 4, Ui.argb(accent, 60));
                ctx.fill(wx + 8, ty + 4, wx + 10, ty + 16, 0xFF000000 | accent);
            } else if (hov) {
                Ui.round(ctx, wx + 8, ty, SB - 16, 20, 4, 0x22FFFFFF);
            }
            ctx.drawText(tr, TABS[i], wx + 18, ty + 6, act ? Ui.WHITE : Ui.DIM, false);
        }

        boolean hovEdit = Ui.in(mx, my, wx + 8, wy + wh - 30, SB - 16, 20);
        Ui.round(ctx, wx + 8, wy + wh - 30, SB - 16, 20, 4, hovEdit ? Ui.argb(accent, 150) : Ui.CARD_HOVER);
        ctx.drawCenteredTextWithShadow(tr, "Edit HUD", wx + SB / 2, wy + wh - 24, Ui.TEXT);

        ctx.drawText(tr, Text.literal(TABS[tab]).formatted(Formatting.BOLD), cx, wy + 14, Ui.TEXT, false);
        ctx.drawText(tr, "x", wx + ww - 18, wy + 13, Ui.DIM, false);
        if (tab == 0) {
            int sx = wx + ww - 150;
            int sy = wy + 8;
            Ui.round(ctx, sx, sy, 120, 16, 4, searchFocus ? Ui.argb(accent, 70) : Ui.CARD);
            String shown = (search.isEmpty() && !searchFocus) ? "Search modules..."
                    : search + ((searchFocus && (System.currentTimeMillis() / 500) % 2 == 0) ? "_" : "");
            ctx.drawText(tr, tr.trimToWidth(shown, 110), sx + 5, sy + 4, search.isEmpty() ? Ui.DIM : Ui.TEXT, false);
        }

        // content
        List<Widgets.Widget> page = page();
        int total = 0;
        for (Widgets.Widget w : page) total += w.h() + 6;
        maxScroll = Math.max(0, total - ch);
        scroll = MathHelper.clamp(scroll, 0, maxScroll);

        ctx.enableScissor(cx, cy, cx + cw, cy + ch);
        int y = cy - (int) scroll;
        for (Widgets.Widget w : page) {
            int h = w.h();
            if (y + h >= cy && y <= cy + ch) w.drawAt(ctx, tr, cx, y, cw - 8, mx, my);
            y += h + 6;
        }
        ctx.disableScissor();

        if (maxScroll > 0) {
            int th = Math.max(16, ch * ch / total);
            int ty = cy + (int) ((ch - th) * (scroll / maxScroll));
            Ui.round(ctx, cx + cw - 3, cy, 3, ch, 1, 0x33FFFFFF);
            Ui.round(ctx, cx + cw - 3, ty, 3, th, 1, 0xFF000000 | accent);
        }

        String toast = Ui.currentToast();
        if (toast != null) {
            ctx.drawText(tr, tr.trimToWidth(toast, cw), cx, wy + wh - 16, 0xFF000000 | accent, false);
        }
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(double mxd, double myd, int button) {
        try {
            return clickInner(mxd, myd, button);
        } catch (Throwable t) {
            SkyClientMod.LOGGER.error("Sky Client menu click failed", t);
            return true;
        }
    }

    private boolean clickInner(double mxd, double myd, int button) {
        layout();
        int mx = (int) mxd, my = (int) myd;
        if (button != 0) return super.mouseClicked(mxd, myd, button);

        if (tab == 0 && Ui.in(mx, my, wx + ww - 150, wy + 8, 120, 16)) { searchFocus = true; return true; }
        searchFocus = false;
        if (Ui.in(mx, my, wx + ww - 24, wy + 8, 16, 16)) { close(); return true; }
        for (int i = 0; i < TABS.length; i++) {
            if (Ui.in(mx, my, wx + 8, wy + 44 + i * 24, SB - 16, 20)) {
                tab = i;
                scroll = 0;
                active = null;
                confirmReset = false;
                return true;
            }
        }
        if (Ui.in(mx, my, wx + 8, wy + wh - 30, SB - 16, 20)) {
            client.setScreen(new HudEditorScreen(tab));
            return true;
        }
        if (Ui.in(mx, my, cx, cy, cw, ch)) {
            int y = cy - (int) scroll;
            for (Widgets.Widget w : page()) {
                int h = w.h();
                if (my >= y && my < y + h) {
                    if (w.click(mx, my, button)) active = w;
                    return true;
                }
                y += h + 6;
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchFocus && tab == 0 && chr >= 32 && chr != 127 && search.length() < 24) {
            search += chr;
            scroll = 0;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searchFocus) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
                scroll = 0;
            } else if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                searchFocus = false;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (active != null) {
            active.drag((int) mx, (int) my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (active != null) {
            active.release();
            active = null;
            ClientConfig.save();
            LyricsConfig.save();
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        scroll = MathHelper.clamp(scroll - (float) vertical * 22f, 0, maxScroll);
        return true;
    }
}
