package com.skyclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

import java.util.List;
import java.util.function.Supplier;

/** A draggable, scalable HUD element. */
public abstract class HudModule {
    public final String name;
    public final String desc;
    private final Supplier<? extends ClientConfig.Hud> cfg;

    protected HudModule(String name, String desc, Supplier<? extends ClientConfig.Hud> cfg) {
        this.name = name;
        this.desc = desc;
        this.cfg = cfg;
    }

    public ClientConfig.Hud cfg() { return cfg.get(); }

    /** Unscaled content size; 0 means "nothing to show right now". */
    protected abstract int contentWidth(MinecraftClient mc, boolean editing);
    protected abstract int contentHeight(MinecraftClient mc, boolean editing);
    protected abstract void drawContent(DrawContext ctx, MinecraftClient mc, int w, int h, boolean editing);

    /** {x, y, scaledW, scaledH, contentW, contentH} in GUI coordinates, or null when hidden. */
    public int[] bounds(MinecraftClient mc, boolean editing) {
        int w = contentWidth(mc, editing);
        int h = contentHeight(mc, editing);
        if (w <= 0 || h <= 0) return null;
        ClientConfig.Hud c = cfg();
        float sc = c.scale;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int x = Math.round(c.xf * Math.max(0f, sw - w * sc));
        int y = Math.round(c.yf * Math.max(0f, sh - h * sc));
        return new int[]{x, y, Math.round(w * sc), Math.round(h * sc), w, h};
    }

    public void render(DrawContext ctx, MinecraftClient mc, boolean editing) {
        int[] b = bounds(mc, editing);
        if (b == null) return;
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        try {
            ms.translate(b[0], b[1], 0);
            ms.scale(cfg().scale, cfg().scale, 1f);
            drawContent(ctx, mc, b[4], b[5], editing);
        } finally {
            ms.pop();
        }
    }

    protected static void box(DrawContext ctx, int w, int h, ClientConfig.Hud c) {
        int a = (int) (c.background / 100f * 255f);
        Ui.round(ctx, 0, 0, w, h, 3, a << 24);
        if (c.accentBar) ctx.fill(0, 1, 2, h - 1, 0xFF000000 | Ui.accent());
    }

    /** HUD element that is just a few lines of text. */
    public abstract static class TextHud extends HudModule {
        static final int PAD = 4;

        protected TextHud(String name, String desc, Supplier<? extends ClientConfig.Hud> cfg) {
            super(name, desc, cfg);
        }

        protected abstract List<String> lines(MinecraftClient mc);

        private List<String> get(MinecraftClient mc, boolean editing) {
            List<String> l = lines(mc);
            if (l.isEmpty() && editing) return List.of(name);
            return l;
        }

        @Override
        protected int contentWidth(MinecraftClient mc, boolean editing) {
            List<String> l = get(mc, editing);
            if (l.isEmpty()) return 0;
            int w = 0;
            for (String s : l) w = Math.max(w, mc.textRenderer.getWidth(s));
            return w + PAD * 2 + (cfg().accentBar ? 3 : 0);
        }

        @Override
        protected int contentHeight(MinecraftClient mc, boolean editing) {
            List<String> l = get(mc, editing);
            return l.isEmpty() ? 0 : l.size() * 10 + PAD * 2 - 1;
        }

        @Override
        protected void drawContent(DrawContext ctx, MinecraftClient mc, int w, int h, boolean editing) {
            ClientConfig.Hud c = cfg();
            box(ctx, w, h, c);
            TextRenderer tr = mc.textRenderer;
            int rgb = 0xFF000000 | LyricsConfig.rgb(c.color, 0xFFFFFF);
            int x = PAD + (c.accentBar ? 3 : 0);
            int y = PAD;
            for (String s : get(mc, editing)) {
                ctx.drawText(tr, s, x, y, rgb, c.shadow);
                y += 10;
            }
        }
    }
}
