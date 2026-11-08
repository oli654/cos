package com.skyclient;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/** Draws the lyrics as a camera-facing billboard far away in the sky. */
public final class SkyRenderer {
    private SkyRenderer() {}

    public static void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> Safe.run("sky-lyrics", () -> render(ctx)));
    }

    private static float baseAlpha(LyricsConfig cfg, int k) {
        return k == 0 ? 1f : (cfg.contextAlpha / 100f) / Math.abs(k);
    }

    private static void render(WorldRenderContext ctx) {
        LyricsConfig cfg = LyricsConfig.get();
        LyricsPlayer player = LyricsPlayer.INSTANCE;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!cfg.enabled || !player.isActive() || mc.player == null) return;
        if (cfg.hideWithHud && mc.options.hudHidden) return;

        MatrixStack ms = ctx.matrixStack();
        VertexConsumerProvider vcp = ctx.consumers();
        LyricsPlayer.Song song = player.song();
        if (ms == null || vcp == null || song == null) return;

        List<LrcParser.Line> lines = song.lines();
        long pos = player.positionMs();
        int cur = player.indexAt(pos);

        float fade = 1f;
        boolean gap = false;
        float progress = 0f;
        if (cur >= 0) {
            long start = lines.get(cur).timeMs();
            long end = song.ends()[cur];
            fade = cfg.fadeMs <= 0 ? 1f : MathHelper.clamp((pos - start) / (float) cfg.fadeMs, 0f, 1f);
            fade = fade * fade * (3f - 2f * fade);
            gap = pos >= end;
            float raw = (pos - start) / (float) Math.max(1L, end - start);
            progress = MathHelper.clamp(raw / 0.85f, 0f, 1f);
        }

        // --- where in the sky ---
        float camYaw = ctx.camera().getYaw();
        float camPitch = ctx.camera().getPitch();
        float yawDeg = switch (cfg.mode) {
            case ANCHORED -> player.anchorYaw();
            case FOLLOW -> camYaw;
            case FIXED -> cfg.fixedYaw;
        };
        double yaw = Math.toRadians(yawDeg);
        double el = Math.toRadians(cfg.elevation);
        double dx = -Math.sin(yaw) * Math.cos(el);
        double dy = Math.sin(el);
        double dz = Math.cos(yaw) * Math.cos(el);
        float d = cfg.distance;

        ms.push();
        try {
        ms.translate(dx * d, dy * d, dz * d);
        if (cfg.bobbing) {
            ms.translate(0, Math.sin(System.currentTimeMillis() / 700.0) * 0.02 * d, 0);
        }

        // Billboard: local x = camera right, local y = camera up, local z = towards the camera.
        double cy = Math.toRadians(camYaw);
        double cp = Math.toRadians(camPitch);
        Vector3f f = new Vector3f((float) (-Math.sin(cy) * Math.cos(cp)), (float) (-Math.sin(cp)), (float) (Math.cos(cy) * Math.cos(cp)));
        Vector3f r = new Vector3f((float) -Math.cos(cy), 0f, (float) -Math.sin(cy));
        Vector3f n = new Vector3f(f).negate();
        Vector3f u = new Vector3f(n).cross(r);
        Matrix3f rot = new Matrix3f(r.x, r.y, r.z, u.x, u.y, u.z, n.x, n.y, n.z);
        ms.multiply(new Quaternionf().setFromNormalized(rot));

        float s = d * 0.0058f * cfg.size;
        ms.scale(s, -s, s);

        // --- lines ---
        TextRenderer tr = mc.textRenderer;
        float lh = 9f * cfg.lineSpacing;
        float slide = (1f - fade) * lh;
        float extra = (cfg.currentScale - 1f) * 9f * 0.5f;

        for (int k = -cfg.showPrevious; k <= cfg.showNext; k++) {
            int idx = cur + k;
            if (idx < 0 || idx >= lines.size()) continue;
            if (k == 0 && gap) continue;

            float a = MathHelper.lerp(fade, baseAlpha(cfg, k + 1), baseAlpha(cfg, k));
            int alpha = (int) (a * 255f);
            if (alpha < 8) continue;

            float y = k * lh + slide;
            if (k > 0) y += extra;
            if (k < 0) y -= extra;

            drawLine(tr, ms, vcp, cfg, lines.get(idx).text(), y,
                    k == 0 ? cfg.currentScale : 1f, Math.min(alpha, 255), k == 0, progress);
        }

        if (cfg.showTitle) {
            float ty = (cfg.showNext + 1) * lh + extra + 8f;
            drawLine(tr, ms, vcp, cfg, "\u266A " + song.name(), ty, 0.6f, 170, false, 0f);
        }
        } finally {
            ms.pop();
        }
    }

    private static void drawLine(TextRenderer tr, MatrixStack ms, VertexConsumerProvider vcp, LyricsConfig cfg,
                                 String text, float y, float scale, int alpha, boolean current, float progress) {
        Style style = Style.EMPTY.withBold(cfg.bold).withItalic(cfg.italic);
        Text full = Text.literal(text).setStyle(style);
        float w = tr.getWidth(full);
        float fit = (w * scale > cfg.maxWidth) ? cfg.maxWidth / (w * scale) : 1f;
        float sc = scale * fit;

        int rainbowRgb = MathHelper.hsvToRgb((System.currentTimeMillis() % 4000L) / 4000f, 0.9f, 1f) & 0xFFFFFF;
        int base = (alpha << 24) | cfg.colorRgb();
        int hi = (alpha << 24) | (cfg.rainbow ? rainbowRgb : cfg.highlightRgb());
        int bg = cfg.backgroundOpacity > 0 ? (((int) (cfg.backgroundOpacity / 100f * alpha)) << 24) : 0;

        TextRenderer.TextLayerType layer = cfg.throughBlocks
                ? TextRenderer.TextLayerType.SEE_THROUGH : TextRenderer.TextLayerType.NORMAL;
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        ms.push();
        try {
        ms.translate(0, y, 0);
        ms.scale(sc, sc, 1f);
        Matrix4f m = ms.peek().getPositionMatrix();
        float x = -w / 2f;
        float ty = -4.5f;

        if (current && cfg.karaoke) {
            int n = MathHelper.clamp(Math.round(progress * text.length()), 0, text.length());
            Text left = Text.literal(text.substring(0, n)).setStyle(style);
            Text right = Text.literal(text.substring(n)).setStyle(style);
            float lw = tr.getWidth(left);
            if (n > 0) tr.draw(left, x, ty, hi, cfg.shadow, m, vcp, layer, bg, light);
            if (n < text.length()) tr.draw(right, x + lw, ty, base, cfg.shadow, m, vcp, layer, bg, light);
        } else {
            int col = (current && cfg.rainbow) ? hi : base;
            tr.draw(full, x, ty, col, cfg.shadow, m, vcp, layer, bg, light);
        }
        } finally {
            ms.pop();
        }
    }
}
