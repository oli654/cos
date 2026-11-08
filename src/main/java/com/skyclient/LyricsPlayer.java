package com.skyclient;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class LyricsPlayer {
    public static final LyricsPlayer INSTANCE = new LyricsPlayer();

    /** lines = non-empty lyric lines; ends[i] = time when line i stops being "current". */
    public record Song(String name, List<LrcParser.Line> lines, long[] ends, long durationMs) {}

    private Song song;
    private boolean playing;
    private long baseMs;
    private long startNano;
    private Clip clip;
    private boolean soundEvent;
    private Identifier soundId;
    private float anchorYaw;

    private LyricsPlayer() {}

    public static Path songsDir() {
        return FabricLoader.getInstance().getGameDir().resolve("skyclient").resolve("songs");
    }

    public static List<String> listSongs() {
        try (Stream<Path> s = Files.list(songsDir())) {
            return s.map(p -> p.getFileName().toString())
                    .filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".lrc"))
                    .map(n -> n.substring(0, n.length() - 4))
                    .sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public static boolean hasWav(String name) {
        return Files.isRegularFile(songsDir().resolve(name + ".wav"));
    }

    public Song song() { return song; }
    public boolean isActive() { return song != null; }
    public boolean isPlaying() { return playing; }
    public float anchorYaw() { return anchorYaw; }
    public void setAnchorYaw(float yaw) { anchorYaw = yaw; }

    /** Starts a song. Returns an info message; throws IllegalArgumentException with a user-facing message on failure. */
    public String play(String name) {
        stop();
        if (name.isBlank() || name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new IllegalArgumentException("Invalid song name.");
        }
        Path lrc = songsDir().resolve(name + ".lrc");
        if (!Files.isRegularFile(lrc)) {
            throw new IllegalArgumentException("File not found: " + lrc.getFileName());
        }

        List<LrcParser.Line> all;
        try {
            String content = new String(Files.readAllBytes(lrc), StandardCharsets.UTF_8);
            all = LrcParser.parse(Arrays.asList(content.split("\\R")));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read file: " + e.getMessage());
        }

        List<LrcParser.Line> shown = new ArrayList<>();
        List<Long> ends = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            LrcParser.Line l = all.get(i);
            if (l.text().isEmpty()) continue;
            long end = (i + 1 < all.size()) ? all.get(i + 1).timeMs() : l.timeMs() + 6000;
            shown.add(l);
            ends.add(end);
        }
        if (shown.isEmpty()) {
            throw new IllegalArgumentException("The .lrc file has no timed lines, e.g. [00:12.50]text");
        }
        long[] endArr = ends.stream().mapToLong(Long::longValue).toArray();
        long duration = endArr[endArr.length - 1] + 1500;

        MinecraftClient mc = MinecraftClient.getInstance();
        LyricsConfig cfg = LyricsConfig.get();
        anchorYaw = mc.player != null ? mc.player.getYaw() : 0f;

        String audio = "lyrics only (no audio)";
        Path wav = songsDir().resolve(name + ".wav");
        if (Files.isRegularFile(wav)) {
            try {
                AudioInputStream in = AudioSystem.getAudioInputStream(wav.toFile());
                Clip c = AudioSystem.getClip();
                c.open(in);
                clip = c;
                applyVolume();
                audio = "audio from " + wav.getFileName();
            } catch (Exception e) {
                clip = null;
                audio = "could not load WAV (" + e.getMessage() + "), lyrics only";
            }
        } else if (cfg.resourcePackAudio) {
            String id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
            if (id.isEmpty()) id = "song";
            soundId = Identifier.of("skyclient", id);
            soundEvent = true;
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(soundId), 1.0f, cfg.volume));
            audio = "resource pack sound " + soundId;
        }

        song = new Song(name, List.copyOf(shown), endArr, duration);
        baseMs = 0;
        startNano = System.nanoTime();
        playing = true;
        if (clip != null) {
            clip.setMicrosecondPosition(0);
            clip.start();
        }
        return "Playing " + name + " - " + audio;
    }

    public void stop() {
        if (clip != null) {
            try { clip.stop(); clip.close(); } catch (Exception ignored) { }
            clip = null;
        }
        if (soundEvent) {
            try { MinecraftClient.getInstance().getSoundManager().stopSounds(soundId, SoundCategory.MASTER); } catch (Exception ignored) { }
            soundEvent = false;
        }
        song = null;
        playing = false;
    }

    public String pause() {
        if (song == null) return "Nothing is playing.";
        if (soundEvent) return "Pause does not work with resource pack audio (use a .wav file).";
        if (!playing) return "Already paused.";
        if (clip != null) clip.stop();
        else baseMs += (System.nanoTime() - startNano) / 1_000_000L;
        playing = false;
        return "Paused.";
    }

    public String resume() {
        if (song == null) return "Nothing is playing.";
        if (playing) return "Already playing.";
        if (clip != null) clip.start();
        else startNano = System.nanoTime();
        playing = true;
        return "Resumed.";
    }

    public String seek(long ms) {
        if (song == null) return "Nothing is playing.";
        if (soundEvent) return "Seeking does not work with resource pack audio (use a .wav file).";
        if (clip != null) {
            clip.setMicrosecondPosition(Math.max(0, ms) * 1000L);
        } else {
            baseMs = Math.max(0, ms);
            startNano = System.nanoTime();
        }
        return "Seeked to " + (ms / 1000) + " s.";
    }

    public void applyVolume() {
        if (clip == null) return;
        try {
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl ctl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float v = Math.max(0.0001f, LyricsConfig.get().volume);
                float db = (float) (20.0 * Math.log10(v));
                ctl.setValue(Math.max(ctl.getMinimum(), Math.min(ctl.getMaximum(), db)));
            }
        } catch (Exception ignored) { }
    }

    private long rawPositionMs() {
        if (clip != null) return clip.getMicrosecondPosition() / 1000L;
        return baseMs + (playing ? (System.nanoTime() - startNano) / 1_000_000L : 0L);
    }

    /** Position without the user's sync offset (for progress bars). */
    public long displayPositionMs() { return rawPositionMs(); }

    public long durationMs() {
        if (song == null) return 0;
        return clip != null ? clip.getMicrosecondLength() / 1000L : song.durationMs();
    }

    /** Position used for lyric lookup, including the user's sync offset. */
    public long positionMs() {
        return rawPositionMs() + LyricsConfig.get().offsetMs;
    }

    /** Index of the latest line whose start time is <= pos, or -1 before the first line. */
    public int indexAt(long posMs) {
        if (song == null) return -1;
        List<LrcParser.Line> l = song.lines();
        int idx = -1;
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).timeMs() <= posMs) idx = i; else break;
        }
        return idx;
    }

    public void tick() {
        if (song == null || !playing) return;
        if (rawPositionMs() >= durationMs()) {
            if (LyricsConfig.get().loop) {
                restart();
            } else {
                String finished = song.name();
                stop();
                if (LyricsConfig.get().autoNext) autoAdvance(finished);
            }
        }
    }

    /** Plays the next song in the library (wraps around). */
    public String next() {
        List<String> all = listSongs();
        if (all.isEmpty()) throw new IllegalArgumentException("No songs in the library.");
        int i = song == null ? -1 : all.indexOf(song.name());
        return play(all.get((i + 1) % all.size()));
    }

    private void autoAdvance(String finished) {
        List<String> all = listSongs();
        int i = all.indexOf(finished);
        if (all.size() > 1 && i >= 0) {
            try {
                play(all.get((i + 1) % all.size()));
            } catch (IllegalArgumentException ignored) { }
        }
    }

    private void restart() {
        baseMs = 0;
        startNano = System.nanoTime();
        if (clip != null) {
            clip.setMicrosecondPosition(0);
            clip.start();
        }
        if (soundEvent) {
            MinecraftClient.getInstance().getSoundManager().play(
                    PositionedSoundInstance.master(SoundEvent.of(soundId), 1.0f, LyricsConfig.get().volume));
        }
    }
}
