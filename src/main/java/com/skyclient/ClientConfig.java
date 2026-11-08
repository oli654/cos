package com.skyclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** All HUD / gameplay module settings. Saved to config/skyclient.json. */
public final class ClientConfig {
    public String accent = "#7C5CFF";
    public boolean rainbowAccent = false;

    // ---------- HUD modules ----------
    public static class Hud {
        public boolean enabled = false;
        public float xf = 0.02f;   // position as a fraction of the free screen space
        public float yf = 0.02f;
        @Opt(value = "Scale", min = 0.5, max = 2.5, step = 0.05) public float scale = 1f;
        @Opt(value = "Background", min = 0, max = 100, step = 5) public int background = 45;
        @Opt(value = "Text color", color = true) public String color = "#FFFFFF";
        @Opt("Text shadow") public boolean shadow = true;
        @Opt("Accent bar") public boolean accentBar = true;

        protected Hud at(boolean en, float x, float y) { enabled = en; xf = x; yf = y; return this; }
    }

    public static class Watermark extends Hud {
        public Watermark() { at(false, 0.01f, 0.01f); }
    }
    public static class Fps extends Hud {
        public Fps() { at(true, 0.01f, 0.01f); }
    }
    public static class Cps extends Hud {
        @Opt("Show right click") public boolean right = true;
        public Cps() { at(true, 0.01f, 0.08f); }
    }
    public static class Ping extends Hud {
        public Ping() { at(false, 0.01f, 0.15f); }
    }
    public static class Coords extends Hud {
        @Opt("Show direction") public boolean facing = true;
        @Opt(value = "Decimals", min = 0, max = 3, step = 1) public int decimals = 0;
        public Coords() { at(false, 0.01f, 0.25f); }
    }
    public static class Speed extends Hud {
        public Speed() { at(false, 0.01f, 0.35f); }
    }
    public static class Memory extends Hud {
        public Memory() { at(false, 0.01f, 0.42f); }
    }
    public static class ClockHud extends Hud {
        @Opt("24-hour format") public boolean h24 = true;
        @Opt("Show seconds") public boolean seconds = false;
        public ClockHud() { at(false, 0.99f, 0.01f); }
    }
    public static class Combo extends Hud {
        @Opt(value = "Reset after (s)", min = 1, max = 10, step = 1) public int timeout = 3;
        public Combo() { at(false, 0.5f, 0.62f); }
    }
    public static class Reach extends Hud {
        @Opt(value = "Decimals", min = 1, max = 3, step = 1) public int decimals = 2;
        public Reach() { at(false, 0.5f, 0.7f); }
    }
    public static class Items extends Hud {
        @Opt("Golden apples") public boolean gapples = true;
        @Opt("Ender pearls") public boolean pearls = true;
        @Opt("Splash potions") public boolean potions = true;
        @Opt("Arrows") public boolean arrows = false;
        @Opt("Totems") public boolean totems = false;
        public Items() { at(false, 0.01f, 0.5f); }
    }
    public static class Potions extends Hud {
        @Opt("Show duration") public boolean duration = true;
        public Potions() { at(true, 0.99f, 0.1f); }
    }
    public static class Keystrokes extends Hud {
        @Opt("Mouse buttons") public boolean mouse = true;
        @Opt("CPS on buttons") public boolean cps = true;
        @Opt("Space bar") public boolean space = true;
        @Opt(value = "Key size", min = 16, max = 34, step = 1) public int keySize = 22;
        @Opt(value = "Pressed color", color = true) public String pressed = "#FFFFFF";
        public Keystrokes() { at(true, 0.01f, 0.72f); background = 50; }
    }
    public static class Armor extends Hud {
        @Opt("Show durability") public boolean durability = true;
        @Opt("Show held item") public boolean held = true;
        @Opt("Horizontal layout") public boolean horizontal = false;
        public Armor() { at(true, 0.99f, 0.62f); }
    }
    public static class NowPlaying extends Hud {
        @Opt("Show current lyric line") public boolean line = false;
        public NowPlaying() { at(false, 0.5f, 0.02f); }
    }

    public static class Biome extends Hud {
        public Biome() { at(false, 0.01f, 0.58f); }
    }
    public static class ServerHud extends Hud {
        public ServerHud() { at(false, 0.01f, 0.65f); }
    }
    public static class Target extends Hud {
        public Target() { at(false, 0.5f, 0.45f); }
    }

    public Watermark watermark = new Watermark();
    public Fps fps = new Fps();
    public Cps cps = new Cps();
    public Ping ping = new Ping();
    public Coords coords = new Coords();
    public Speed speed = new Speed();
    public Memory memory = new Memory();
    public ClockHud clock = new ClockHud();
    public Combo combo = new Combo();
    public Reach reach = new Reach();
    public Items items = new Items();
    public Potions potions = new Potions();
    public Keystrokes keystrokes = new Keystrokes();
    public Armor armor = new Armor();
    public NowPlaying nowPlaying = new NowPlaying();
    public Biome biome = new Biome();
    public ServerHud server = new ServerHud();
    public Target target = new Target();

    /** Same order as HudModules.ALL. */
    public List<Hud> huds() {
        return List.of(watermark, fps, cps, ping, coords, speed, memory, clock, combo, reach, items, potions,
                keystrokes, armor, nowPlaying, biome, server, target);
    }

    // ---------- gameplay modules ----------
    public static class Sprint {
        public boolean enabled = false;
    }
    public static class Zoom {
        public boolean enabled = true;
        @Opt(value = "Zoom FOV", min = 30, max = 70, step = 1) public int fov = 30;
        @Opt("Lower mouse sensitivity") public boolean lowerSens = true;
    }
    public Sprint sprint = new Sprint();
    public Zoom zoom = new Zoom();

    // ---------- io ----------
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ClientConfig instance = new ClientConfig();

    public static ClientConfig get() { return instance; }

    public static Path configFile() { return file(); }

    // ---------- presets ----------
    public void applyPreset(String preset) {
        for (Hud h : huds()) h.enabled = false;
        switch (preset) {
            case "minimal" -> { fps.enabled = true; cps.enabled = true; }
            case "pvp" -> {
                fps.enabled = true; cps.enabled = true; ping.enabled = true; keystrokes.enabled = true;
                armor.enabled = true; potions.enabled = true; combo.enabled = true; reach.enabled = true;
                items.enabled = true; target.enabled = true;
            }
            default -> { for (Hud h : huds()) h.enabled = true; }
        }
        resetPositions();
    }

    // ---------- profiles (3 slots) ----------
    private static Path profileDir(int slot) {
        return FabricLoader.getInstance().getGameDir().resolve("skyclient").resolve("profiles").resolve("slot" + slot);
    }

    public static String saveProfile(int slot) {
        try {
            save();
            LyricsConfig.save();
            Path d = profileDir(slot);
            Files.createDirectories(d);
            Files.copy(file(), d.resolve("skyclient.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.copy(LyricsConfig.configFile(), d.resolve("lyrics.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return "Saved profile in slot " + slot + ".";
        } catch (Exception e) {
            return "Could not save profile: " + e.getMessage();
        }
    }

    public static String loadProfile(int slot) {
        try {
            Path d = profileDir(slot);
            Path a = d.resolve("skyclient.json");
            if (!Files.exists(a)) return "Slot " + slot + " is empty.";
            Files.copy(a, file(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Path b = d.resolve("lyrics.json");
            if (Files.exists(b)) Files.copy(b, LyricsConfig.configFile(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            load();
            LyricsConfig.load();
            return "Loaded profile from slot " + slot + ".";
        } catch (Exception e) {
            return "Could not load profile: " + e.getMessage();
        }
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("skyclient.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                try (Reader r = Files.newBufferedReader(f)) {
                    ClientConfig c = GSON.fromJson(r, ClientConfig.class);
                    if (c != null) instance = c;
                }
            } else {
                save();
            }
        } catch (Exception e) {
            SkyClientMod.LOGGER.warn("Could not load config, using defaults", e);
        }
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(file())) {
            GSON.toJson(instance, w);
        } catch (Exception e) {
            SkyClientMod.LOGGER.warn("Could not save config", e);
        }
    }

    public void resetPositions() {
        ClientConfig fresh = new ClientConfig();
        List<Hud> a = huds();
        List<Hud> b = fresh.huds();
        for (int i = 0; i < a.size(); i++) {
            a.get(i).xf = b.get(i).xf;
            a.get(i).yf = b.get(i).yf;
            a.get(i).scale = b.get(i).scale;
        }
    }

    public static void resetAll() {
        instance = new ClientConfig();
        LyricsConfig.resetToDefaults();
        save();
    }
}
