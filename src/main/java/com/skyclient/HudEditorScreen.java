package com.skyclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/** Drag HUD modules around, scroll over one to resize it. */
public final class HudEditorScreen extends Screen {
    private final int returnTab;
    private HudModule dragging;
    private int offX, offY;

    public HudEditorScreen(int returnTab) {
        super(Text.literal("HUD editor"));
        this.returnTab = returnTab;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void removed() {
        ClientConfig.save();
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(new SkyMenuScreen(returnTab));
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int accent = 0xFF000000 | Ui.accent();

        ctx.fill(0, 0, width, height, 0x66000000);
        ctx.fill(width / 2, 0, width / 2 + 1, height, 0x22FFFFFF);
        ctx.fill(0, height / 2, width, height / 2 + 1, 0x22FFFFFF);

        for (HudModule m : HudModules.ALL) {
            if (!m.cfg().enabled) continue;
            Safe.run("hud-" + m.name, () -> m.render(ctx, mc, true));
            int[] b = m.bounds(mc, true);
            if (b == null) continue;
            boolean hov = Ui.in(mx, my, b[0], b[1], b[2], b[3]);
            int col = (m == dragging) ? accent : hov ? 0xCCFFFFFF : 0x66FFFFFF;
            ctx.drawBorder(b[0] - 1, b[1] - 1, b[2] + 2, b[3] + 2, col);
            if (hov || m == dragging) {
                ctx.drawText(tr, m.name, b[0], Math.max(0, b[1] - 11), accent, true);
            }
        }

        ctx.drawCenteredTextWithShadow(tr, "Drag modules to move them  |  Scroll over a module to resize  |  Enable modules in the menu",
                width / 2, 8, Ui.TEXT);

        int by = height - 30;
        boolean hDone = Ui.in(mx, my, width / 2 - 66, by, 62, 20);
        boolean hReset = Ui.in(mx, my, width / 2 + 4, by, 62, 20);
        Ui.round(ctx, width / 2 - 66, by, 62, 20, 4, hDone ? Ui.argb(Ui.accent(), 220) : Ui.argb(Ui.accent(), 140));
        Ui.round(ctx, width / 2 + 4, by, 62, 20, 4, hReset ? 0xFF3A3A4E : 0xFF2A2A3B);
        ctx.drawCenteredTextWithShadow(tr, "Done", width / 2 - 35, by + 6, Ui.WHITE);
        ctx.drawCenteredTextWithShadow(tr, "Reset", width / 2 + 35, by + 6, Ui.WHITE);
    }

    @Override
    public boolean mouseClicked(double mxd, double myd, int button) {
        int mx = (int) mxd, my = (int) myd;
        if (button != 0) return super.mouseClicked(mxd, myd, button);
        int by = height - 30;
        if (Ui.in(mx, my, width / 2 - 66, by, 62, 20)) { close(); return true; }
        if (Ui.in(mx, my, width / 2 + 4, by, 62, 20)) {
            ClientConfig.get().resetPositions();
            return true;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        for (int i = HudModules.ALL.size() - 1; i >= 0; i--) {
            HudModule m = HudModules.ALL.get(i);
            if (!m.cfg().enabled) continue;
            int[] b = m.bounds(mc, true);
            if (b != null && Ui.in(mx, my, b[0], b[1], b[2], b[3])) {
                dragging = m;
                offX = mx - b[0];
                offY = my - b[1];
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mxd, double myd, int button, double dx, double dy) {
        if (dragging == null) return super.mouseDragged(mxd, myd, button, dx, dy);
        MinecraftClient mc = MinecraftClient.getInstance();
        int[] b = dragging.bounds(mc, true);
        if (b == null) return true;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int nx = (int) mxd - offX;
        int ny = (int) myd - offY;
        int freeX = sw - b[2];
        int freeY = sh - b[3];
        ClientConfig.Hud c = dragging.cfg();
        c.xf = freeX <= 0 ? 0f : MathHelper.clamp(nx / (float) freeX, 0f, 1f);
        c.yf = freeY <= 0 ? 0f : MathHelper.clamp(ny / (float) freeY, 0f, 1f);
        if (Math.abs(nx + b[2] / 2 - sw / 2) < 4) c.xf = 0.5f;
        if (Math.abs(ny + b[3] / 2 - sh / 2) < 4) c.yf = 0.5f;
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mxd, double myd, double horizontal, double vertical) {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (HudModule m : HudModules.ALL) {
            if (!m.cfg().enabled) continue;
            int[] b = m.bounds(mc, true);
            if (b != null && Ui.in(mxd, myd, b[0], b[1], b[2], b[3])) {
                ClientConfig.Hud c = m.cfg();
                c.scale = MathHelper.clamp(c.scale + (float) vertical * 0.05f, 0.5f, 2.5f);
                return true;
            }
        }
        return true;
    }
}
