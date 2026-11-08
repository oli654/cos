package com.skyclient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;

public class SkyClientMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("skyclient");

    public static KeyBinding menuKey;
    public static KeyBinding zoomKey;
    public static KeyBinding lyricsToggleKey;
    public static KeyBinding lyricsPauseKey;

    private static KeyBinding reg(String id, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(id, InputUtil.Type.KEYSYM, key, "category.skyclient"));
    }

    public static String keyName(KeyBinding k) {
        try {
            return k == null ? "?" : k.getBoundKeyLocalizedText().getString();
        } catch (Throwable t) {
            return "?";
        }
    }

    /** Each init step is isolated: if one fails, the rest of the mod (and the game) keeps working. */
    private static void step(String name, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            LOGGER.error("Sky Client: init step '{}' failed", name, t);
        }
    }

    @Override
    public void onInitializeClient() {
        step("config", () -> {
            ClientConfig.load();
            LyricsConfig.load();
        });
        step("folders", () -> {
            try {
                Files.createDirectories(LyricsPlayer.songsDir());
            } catch (IOException e) {
                LOGGER.warn("Could not create songs folder", e);
            }
        });
        step("keybinds", () -> {
            menuKey = reg("key.skyclient.menu", GLFW.GLFW_KEY_RIGHT_SHIFT);
            zoomKey = reg("key.skyclient.zoom", GLFW.GLFW_KEY_C);
            lyricsToggleKey = reg("key.skyclient.lyrics_toggle", GLFW.GLFW_KEY_RIGHT_BRACKET);
            lyricsPauseKey = reg("key.skyclient.lyrics_pause", GLFW.GLFW_KEY_LEFT_BRACKET);
        });
        step("hud-modules", HudModules::init);
        step("sky-renderer", SkyRenderer::init);
        step("gameplay", Gameplay::init);
        step("hud-callback", () -> HudRenderCallback.EVENT.register(
                (ctx, tickCounter) -> Safe.run("hud", () -> HudModules.renderAll(ctx))));
        step("commands", () -> ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> LyricsCommand.register(dispatcher)));
        step("tick", () -> ClientTickEvents.END_CLIENT_TICK.register(
                client -> Safe.run("tick", () -> onTick(client))));
        step("lifecycle", () -> {
            ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LyricsPlayer.INSTANCE.stop());
            ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
                LyricsPlayer.INSTANCE.stop();
                Gameplay.restoreZoom();
                ClientConfig.save();
                LyricsConfig.save();
            });
        });
        LOGGER.info("Sky Client initialised");
    }

    private static void onTick(MinecraftClient client) {
        LyricsPlayer.INSTANCE.tick();
        if (menuKey != null) {
            while (menuKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new SkyMenuScreen(0));
            }
        }
        if (lyricsToggleKey != null) {
            while (lyricsToggleKey.wasPressed()) {
                LyricsConfig cfg = LyricsConfig.get();
                cfg.enabled = !cfg.enabled;
                LyricsConfig.save();
                actionBar(client, cfg.enabled ? "Sky lyrics: on" : "Sky lyrics: off");
            }
        }
        if (lyricsPauseKey != null) {
            while (lyricsPauseKey.wasPressed()) {
                LyricsPlayer p = LyricsPlayer.INSTANCE;
                actionBar(client, p.isPlaying() ? p.pause() : p.resume());
            }
        }
    }

    private static void actionBar(MinecraftClient client, String msg) {
        if (client.player != null) client.player.sendMessage(Text.literal(msg), true);
    }
}
