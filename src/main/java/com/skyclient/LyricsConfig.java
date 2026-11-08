package com.skyclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Sky lyrics options. Saved to config/skyclient-lyrics.json. */
public final class LyricsConfig {
    public enum Mode { ANCHORED, FOLLOW, FIXED }

    public boolean enabled = true;

    @Opt(value = "Mode", group = "Position") public Mode mode = Mode.ANCHORED;
    @Opt(value = "Distance", group = "Position", min = 8, max = 110, step = 1) public float distance = 64f;
    @Opt(value = "Elevation", group = "Position", min = -10, max = 85, step = 1) public float elevation = 35f;
    @Opt(value = "Fixed yaw (FIXED mode)", group = "Position", min = -180, max = 180, step = 5) public float fixedYaw = 0f;
    @Opt(value = "Floating animation", group = "Position") public boolean bobbing = false;

    @Opt(value = "Size", group = "Look", min = 0.2, max = 5, step = 0.05) public float size = 1f;
    @Opt(value = "Current line scale", group = "Look", min = 0.5, max = 2.5, step = 0.05) public float currentScale = 1.2f;
    @Opt(value = "Line spacing", group = "Look", min = 1, max = 3, step = 0.05) public float lineSpacing = 1.4f;
    @Opt(value = "Text color", group = "Look", color = true) public String color = "#FFFFFF";
    @Opt(value = "Highlight color", group = "Look", color = true) public String highlightColor = "#FFD84D";
    @Opt(value = "Karaoke highlight", group = "Look") public boolean karaoke = true;
    @Opt(value = "Rainbow highlight", group = "Look") public boolean rainbow = false;
    @Opt(value = "Text shadow", group = "Look") public boolean shadow = true;
    @Opt(value = "Bold", group = "Look") public boolean bold = false;
    @Opt(value = "Italic", group = "Look") public boolean italic = false;
    @Opt(value = "Background", group = "Look", min = 0, max = 100, step = 5) public int backgroundOpacity = 0;
    @Opt(value = "Visible through blocks", group = "Look") public boolean throughBlocks = true;
    @Opt(value = "Max line width", group = "Look", min = 60, max = 1000, step = 10) public int maxWidth = 360;
    @Opt(value = "Show song title", group = "Look") public boolean showTitle = true;
    @Opt(value = "Hide with F1", group = "Look") public boolean hideWithHud = true;

    @Opt(value = "Previous lines", group = "Lines", min = 0, max = 5, step = 1) public int showPrevious = 1;
    @Opt(value = "Next lines", group = "Lines", min = 0, max = 5, step = 1) public int showNext = 2;
    @Opt(value = "Other lines opacity", group = "Lines", min = 0, max = 100, step = 5) public int contextAlpha = 40;
    @Opt(value = "Transition (ms)", group = "Lines", min = 0, max = 1500, step = 50) public int fadeMs = 350;

    @Opt(value = "Sync offset (ms)", group = "Sync & audio", min = -5000, max = 5000, step = 50) public int offsetMs = 0;
    @Opt(value = "Volume (WAV)", group = "Sync & audio", min = 0, max = 1, step = 0.05) public float volume = 1f;
    @Opt(value = "Loop", group = "Sync & audio") public boolean loop = false;
    @Opt(value = "Auto-play next song", group = "Sync & audio") public boolean autoNext = false;
    @Opt(value = "Use resource pack audio", group = "Sync & audio") public boolean resourcePackAudio = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static LyricsConfig instance = new LyricsConfig();

    public static LyricsConfig get() { return instance; }

    public static Path configFile() { return file(); }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("skyclient-lyrics.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                try (Reader r = Files.newBufferedReader(f)) {
                    LyricsConfig c = GSON.fromJson(r, LyricsConfig.class);
                    if (c != null) {
                        c.clamp();
                        instance = c;
                    }
                }
            } else {
                save();
            }
        } catch (Exception e) {
            SkyClientMod.LOGGER.warn("Could not load lyrics config, using defaults", e);
        }
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(file())) {
            GSON.toJson(instance, w);
        } catch (Exception e) {
            SkyClientMod.LOGGER.warn("Could not save lyrics config", e);
        }
    }

    public static void resetToDefaults() {
        instance = new LyricsConfig();
        save();
    }

    private static float c(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    private static int c(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    public void clamp() {
        if (mode == null) mode = Mode.ANCHORED;
        distance = c(distance, 8f, 110f);
        elevation = c(elevation, -10f, 85f);
        size = c(size, 0.2f, 5f);
        currentScale = c(currentScale, 0.5f, 2.5f);
        lineSpacing = c(lineSpacing, 1f, 3f);
        backgroundOpacity = c(backgroundOpacity, 0, 100);
        maxWidth = c(maxWidth, 60, 2000);
        showPrevious = c(showPrevious, 0, 5);
        showNext = c(showNext, 0, 5);
        contextAlpha = c(contextAlpha, 0, 100);
        fadeMs = c(fadeMs, 0, 3000);
        offsetMs = c(offsetMs, -60000, 60000);
        volume = c(volume, 0f, 1f);
        try { parseColor(color); } catch (Exception e) { color = "#FFFFFF"; }
        try { parseColor(highlightColor); } catch (Exception e) { highlightColor = "#FFD84D"; }
    }

    public static int parseColor(String s) {
        return Integer.parseInt(s.trim().replace("#", ""), 16) & 0xFFFFFF;
    }

    public static int rgb(String s, int fallback) {
        try { return parseColor(s); } catch (Exception e) { return fallback; }
    }

    public int colorRgb() { return rgb(color, 0xFFFFFF); }
    public int highlightRgb() { return rgb(highlightColor, 0xFFD84D); }

    // ---- generic access used by /lyrics set ----

    public static List<String> optionNames() {
        return Arrays.stream(LyricsConfig.class.getFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .map(Field::getName).toList();
    }

    public static List<String> valueHints(String option) {
        try {
            Class<?> t = LyricsConfig.class.getField(option).getType();
            if (t == boolean.class) return List.of("true", "false");
            if (t.isEnum()) return Arrays.stream(t.getEnumConstants()).map(Object::toString).toList();
        } catch (NoSuchFieldException ignored) { }
        return List.of();
    }

    public String get(String option) throws Exception {
        return String.valueOf(LyricsConfig.class.getField(option).get(this));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public String set(String option, String value) throws Exception {
        Field f = LyricsConfig.class.getField(option);
        Class<?> t = f.getType();
        String v = value.trim();
        if (t == boolean.class) {
            switch (v.toLowerCase()) {
                case "true", "on", "1", "yes", "tak" -> f.setBoolean(this, true);
                case "false", "off", "0", "no", "nie" -> f.setBoolean(this, false);
                default -> throw new IllegalArgumentException("Use true or false");
            }
        } else if (t == int.class) {
            f.setInt(this, Integer.parseInt(v));
        } else if (t == float.class) {
            f.setFloat(this, Float.parseFloat(v.replace(',', '.')));
        } else if (t == String.class) {
            parseColor(v);
            f.set(this, v.startsWith("#") ? v : "#" + v);
        } else if (t.isEnum()) {
            f.set(this, Enum.valueOf((Class<Enum>) t, v.toUpperCase()));
        }
        clamp();
        save();
        return String.valueOf(f.get(this));
    }
}
