package com.skyclient;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;

import java.util.Optional;

/** Non-HUD features (sprint, zoom) and the data behind combo / reach / speed. */
public final class Gameplay {
    private Gameplay() {}

    public static int combo;
    public static long lastHitMs;
    public static double lastReach;
    public static double speed;

    private static int prevHurt;
    private static double px, pz;
    private static boolean zooming;
    private static int savedFov;
    private static double savedSens;
    private static boolean sprintForced;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> Safe.run("gameplay", () -> tick(mc)));
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient() && entity instanceof LivingEntity && player == MinecraftClient.getInstance().player) {
                onAttack(player, entity);
            }
            return ActionResult.PASS;
        });
    }

    private static void onAttack(PlayerEntity player, Entity target) {
        combo++;
        lastHitMs = System.currentTimeMillis();
        Vec3d eye = player.getEyePos();
        Vec3d end = eye.add(player.getRotationVec(1f).multiply(8.0));
        Optional<Vec3d> hit = target.getBoundingBox().expand(target.getTargetingMargin()).raycast(eye, end);
        if (hit.isPresent()) lastReach = eye.distanceTo(hit.get());
    }

    private static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        ClientConfig cfg = ClientConfig.get();
        InputTracker.poll(mc);
        if (p == null) {
            combo = 0;
            speed = 0;
            return;
        }

        // always sprint
        if (cfg.sprint.enabled) {
            if (mc.currentScreen == null && mc.options.forwardKey.isPressed() && !p.isSneaking()) {
                mc.options.sprintKey.setPressed(true);
                sprintForced = true;
            }
        } else if (sprintForced) {
            mc.options.sprintKey.setPressed(false);
            sprintForced = false;
        }

        // combo: reset when we get hurt or stop hitting
        if (p.hurtTime > 0 && prevHurt == 0) combo = 0;
        prevHurt = p.hurtTime;
        if (combo > 0 && System.currentTimeMillis() - lastHitMs > cfg.combo.timeout * 1000L) combo = 0;

        // speed in blocks per second
        double dx = p.getX() - px;
        double dz = p.getZ() - pz;
        speed = speed * 0.7 + Math.hypot(dx, dz) * 20.0 * 0.3;
        px = p.getX();
        pz = p.getZ();

        // zoom
        boolean wantZoom = cfg.zoom.enabled && mc.currentScreen == null && SkyClientMod.zoomKey != null && SkyClientMod.zoomKey.isPressed();
        if (wantZoom) {
            if (!zooming) {
                savedFov = mc.options.getFov().getValue();
                savedSens = mc.options.getMouseSensitivity().getValue();
                zooming = true;
            }
            mc.options.getFov().setValue(Math.max(30, Math.min(110, cfg.zoom.fov)));
            if (cfg.zoom.lowerSens) mc.options.getMouseSensitivity().setValue(Math.max(0.0, savedSens * 0.45));
        } else if (zooming) {
            restoreZoom();
        }
    }

    public static void restoreZoom() {
        if (!zooming) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.options.getFov().setValue(savedFov);
        mc.options.getMouseSensitivity().setValue(savedSens);
        zooming = false;
    }
}
