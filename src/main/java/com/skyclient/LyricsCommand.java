package com.skyclient;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class LyricsCommand {
    private LyricsCommand() {}

    private static final SuggestionProvider<FabricClientCommandSource> SONGS =
            (c, b) -> CommandSource.suggestMatching(LyricsPlayer.listSongs(), b);
    private static final SuggestionProvider<FabricClientCommandSource> OPTIONS =
            (c, b) -> CommandSource.suggestMatching(LyricsConfig.optionNames(), b);
    private static final SuggestionProvider<FabricClientCommandSource> VALUES =
            (c, b) -> CommandSource.suggestMatching(LyricsConfig.valueHints(StringArgumentType.getString(c, "option")), b);

    private static void msg(CommandContext<FabricClientCommandSource> c, String text) {
        c.getSource().sendFeedback(Text.literal(text));
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> d) {
        d.register(literal("skyclient").executes(c -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.send(() -> mc.setScreen(new SkyMenuScreen(0)));
            return 1;
        }).then(literal("doctor").executes(c -> {
            var fl = net.fabricmc.loader.api.FabricLoader.getInstance();
            String mcv = fl.getModContainer("minecraft").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
            String api = fl.getModContainer("fabric-api").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("MISSING");
            msg(c, "Minecraft " + mcv + " | Fabric API " + api + " (expected 1.21.1 / 0.115.0+1.21.1)");
            msg(c, "Songs folder: " + LyricsPlayer.songsDir());
            msg(c, Safe.report());
            return 1;
        })));

        d.register(literal("lyrics")
                .then(literal("menu").executes(c -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.send(() -> mc.setScreen(new SkyMenuScreen(3)));
                    return 1;
                }))
                .then(literal("play").then(argument("song", StringArgumentType.greedyString()).suggests(SONGS)
                        .executes(c -> {
                            try {
                                msg(c, LyricsPlayer.INSTANCE.play(StringArgumentType.getString(c, "song")));
                            } catch (IllegalArgumentException e) {
                                msg(c, e.getMessage());
                            }
                            return 1;
                        })))
                .then(literal("stop").executes(c -> { LyricsPlayer.INSTANCE.stop(); msg(c, "Stopped."); return 1; }))
                .then(literal("pause").executes(c -> { msg(c, LyricsPlayer.INSTANCE.pause()); return 1; }))
                .then(literal("resume").executes(c -> { msg(c, LyricsPlayer.INSTANCE.resume()); return 1; }))
                .then(literal("seek").then(argument("seconds", DoubleArgumentType.doubleArg(0))
                        .executes(c -> {
                            long ms = (long) (DoubleArgumentType.getDouble(c, "seconds") * 1000);
                            msg(c, LyricsPlayer.INSTANCE.seek(ms));
                            return 1;
                        })))
                .then(literal("anchor").executes(c -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.player != null) {
                        LyricsPlayer.INSTANCE.setAnchorYaw(mc.player.getYaw());
                        msg(c, "Lyrics anchored to the direction you are looking.");
                    }
                    return 1;
                }))
                .then(literal("list").executes(c -> {
                    var songs = LyricsPlayer.listSongs();
                    msg(c, songs.isEmpty() ? "No .lrc files in the songs folder." : "Songs: " + String.join(", ", songs));
                    return 1;
                }))
                .then(literal("folder").executes(c -> {
                    Util.getOperatingSystem().open(LyricsPlayer.songsDir().toFile());
                    return 1;
                }))
                .then(literal("config").executes(c -> {
                    LyricsConfig cfg = LyricsConfig.get();
                    for (String o : LyricsConfig.optionNames()) {
                        try { msg(c, o + " = " + cfg.get(o)); } catch (Exception ignored) { }
                    }
                    return 1;
                }))
                .then(literal("set").then(argument("option", StringArgumentType.word()).suggests(OPTIONS)
                        .then(argument("value", StringArgumentType.greedyString()).suggests(VALUES)
                                .executes(c -> {
                                    String opt = StringArgumentType.getString(c, "option");
                                    String val = StringArgumentType.getString(c, "value");
                                    try {
                                        String now = LyricsConfig.get().set(opt, val);
                                        LyricsPlayer.INSTANCE.applyVolume();
                                        msg(c, opt + " = " + now);
                                    } catch (NoSuchFieldException e) {
                                        msg(c, "Unknown option: " + opt + " (see /lyrics config)");
                                    } catch (Exception e) {
                                        msg(c, "Invalid value: " + e.getMessage());
                                    }
                                    return 1;
                                }))))
        );
    }
}
