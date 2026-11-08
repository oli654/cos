package com.skyclient;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Registry + implementations of all HUD modules. */
public final class HudModules {
    private HudModules() {}

    /** Same order as ClientConfig.huds(). */
    public static final List<HudModule> ALL = new ArrayList<>();

    public static void renderAll(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        if (mc.currentScreen instanceof HudEditorScreen) return;
        for (HudModule m : ALL) {
            if (m.cfg().enabled) Safe.run("hud-" + m.name, () -> m.render(ctx, mc, false));
        }
    }

    private static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    private static String ticks(int t) {
        int s = t / 20;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    public static void init() {
        ALL.clear();

        ALL.add(new HudModule.TextHud("Watermark", "Shows the client name.", () -> ClientConfig.get().watermark) {
            @Override protected List<String> lines(MinecraftClient mc) { return List.of("Sky Client"); }
        });

        ALL.add(new HudModule.TextHud("FPS", "Frames per second.", () -> ClientConfig.get().fps) {
            @Override protected List<String> lines(MinecraftClient mc) { return List.of(mc.getCurrentFps() + " FPS"); }
        });

        ALL.add(new HudModule.TextHud("CPS", "Clicks per second.", () -> ClientConfig.get().cps) {
            @Override protected List<String> lines(MinecraftClient mc) {
                ClientConfig.Cps c = ClientConfig.get().cps;
                return List.of(c.right ? InputTracker.left() + " | " + InputTracker.right() + " CPS" : InputTracker.left() + " CPS");
            }
        });

        ALL.add(new HudModule.TextHud("Ping", "Latency to the server.", () -> ClientConfig.get().ping) {
            @Override protected List<String> lines(MinecraftClient mc) {
                int ping = 0;
                if (mc.getNetworkHandler() != null && mc.player != null) {
                    PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                    if (e != null) ping = e.getLatency();
                }
                return List.of(ping + " ms");
            }
        });

        ALL.add(new HudModule.TextHud("Coordinates", "XYZ position and facing direction.", () -> ClientConfig.get().coords) {
            @Override protected List<String> lines(MinecraftClient mc) {
                ClientConfig.Coords c = ClientConfig.get().coords;
                ClientPlayerEntity p = mc.player;
                String f = "%." + c.decimals + "f";
                String xyz = String.format(Locale.ROOT, "XYZ: " + f + " / " + f + " / " + f, p.getX(), p.getY(), p.getZ());
                return c.facing ? List.of(xyz, "Facing: " + p.getHorizontalFacing().asString()) : List.of(xyz);
            }
        });

        ALL.add(new HudModule.TextHud("Speed", "Horizontal speed in blocks per second.", () -> ClientConfig.get().speed) {
            @Override protected List<String> lines(MinecraftClient mc) {
                return List.of(String.format(Locale.ROOT, "%.2f b/s", Gameplay.speed));
            }
        });

        ALL.add(new HudModule.TextHud("Memory", "Java memory usage.", () -> ClientConfig.get().memory) {
            @Override protected List<String> lines(MinecraftClient mc) {
                Runtime rt = Runtime.getRuntime();
                long used = (rt.totalMemory() - rt.freeMemory()) / 1048576L;
                long max = rt.maxMemory() / 1048576L;
                return List.of("RAM: " + used + " / " + max + " MB");
            }
        });

        ALL.add(new HudModule.TextHud("Clock", "Real time.", () -> ClientConfig.get().clock) {
            @Override protected List<String> lines(MinecraftClient mc) {
                ClientConfig.ClockHud c = ClientConfig.get().clock;
                String pat = c.h24 ? (c.seconds ? "HH:mm:ss" : "HH:mm") : (c.seconds ? "hh:mm:ss a" : "hh:mm a");
                return List.of(LocalTime.now().format(DateTimeFormatter.ofPattern(pat, Locale.ENGLISH)));
            }
        });

        ALL.add(new HudModule.TextHud("Combo", "Hits in a row without being hit.", () -> ClientConfig.get().combo) {
            @Override protected List<String> lines(MinecraftClient mc) {
                return Gameplay.combo > 0 ? List.of("Combo: " + Gameplay.combo) : List.of();
            }
        });

        ALL.add(new HudModule.TextHud("Reach", "Distance of your last hit.", () -> ClientConfig.get().reach) {
            @Override protected List<String> lines(MinecraftClient mc) {
                if (Gameplay.lastReach <= 0 || System.currentTimeMillis() - Gameplay.lastHitMs > 5000) return List.of();
                int d = ClientConfig.get().reach.decimals;
                return List.of(String.format(Locale.ROOT, "Reach: %." + d + "f", Gameplay.lastReach));
            }
        });

        ALL.add(new HudModule.TextHud("Item counter", "Counts PvP items in your inventory.", () -> ClientConfig.get().items) {
            @Override protected List<String> lines(MinecraftClient mc) {
                ClientConfig.Items c = ClientConfig.get().items;
                var inv = mc.player.getInventory();
                List<String> l = new ArrayList<>();
                if (c.gapples) l.add("Gapples: " + (inv.count(Items.GOLDEN_APPLE) + inv.count(Items.ENCHANTED_GOLDEN_APPLE)));
                if (c.pearls) l.add("Pearls: " + inv.count(Items.ENDER_PEARL));
                if (c.potions) l.add("Splash pots: " + inv.count(Items.SPLASH_POTION));
                if (c.arrows) l.add("Arrows: " + inv.count(Items.ARROW));
                if (c.totems) l.add("Totems: " + inv.count(Items.TOTEM_OF_UNDYING));
                return l;
            }
        });

        ALL.add(new HudModule.TextHud("Potion effects", "Active status effects with timers.", () -> ClientConfig.get().potions) {
            @Override protected List<String> lines(MinecraftClient mc) {
                ClientConfig.Potions c = ClientConfig.get().potions;
                List<String> l = new ArrayList<>();
                for (StatusEffectInstance e : mc.player.getStatusEffects()) {
                    String n = e.getEffectType().value().getName().getString();
                    if (e.getAmplifier() > 0) n += " " + roman(e.getAmplifier() + 1);
                    if (c.duration) n += " (" + (e.isInfinite() ? "inf" : ticks(e.getDuration())) + ")";
                    l.add(n);
                }
                return l;
            }
        });

        ALL.add(new KeystrokesHud());
        ALL.add(new ArmorHud());

        ALL.add(new HudModule.TextHud("Now playing", "Current song from the lyrics player.", () -> ClientConfig.get().nowPlaying) {
            @Override protected List<String> lines(MinecraftClient mc) {
                LyricsPlayer p = LyricsPlayer.INSTANCE;
                if (!p.isActive()) return List.of();
                List<String> l = new ArrayList<>();
                l.add("\u266A " + p.song().name() + "  " + Ui.fmtTime(p.displayPositionMs()) + " / " + Ui.fmtTime(p.durationMs()));
                if (ClientConfig.get().nowPlaying.line) {
                    int idx = p.indexAt(p.positionMs());
                    if (idx >= 0 && p.positionMs() < p.song().ends()[idx]) l.add(p.song().lines().get(idx).text());
                }
                return l;
            }
        });

        ALL.add(new HudModule.TextHud("Biome", "Current biome.", () -> ClientConfig.get().biome) {
            @Override protected List<String> lines(MinecraftClient mc) {
                if (mc.world == null || mc.player == null) return List.of();
                String b = mc.world.getBiome(mc.player.getBlockPos()).getKey()
                        .map(k -> k.getValue().getPath().replace('_', ' ')).orElse("unknown");
                return List.of("Biome: " + b);
            }
        });

        ALL.add(new HudModule.TextHud("Server", "Server address you are connected to.", () -> ClientConfig.get().server) {
            @Override protected List<String> lines(MinecraftClient mc) {
                return List.of(mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "Singleplayer");
            }
        });

        ALL.add(new HudModule.TextHud("Target info", "Name and health of the entity you are looking at.", () -> ClientConfig.get().target) {
            @Override protected List<String> lines(MinecraftClient mc) {
                if (mc.targetedEntity instanceof LivingEntity le) {
                    return List.of(le.getName().getString(),
                            String.format(Locale.ROOT, "%.1f / %.1f HP", le.getHealth(), le.getMaxHealth()));
                }
                return List.of();
            }
        });
    }

    // ------------------------------------------------------------------ keystrokes
    static final class KeystrokesHud extends HudModule {
        private static final int GAP = 2;

        KeystrokesHud() {
            super("Keystrokes", "WASD, mouse buttons and space with CPS.", () -> ClientConfig.get().keystrokes);
        }

        private ClientConfig.Keystrokes c() { return ClientConfig.get().keystrokes; }

        @Override protected int contentWidth(MinecraftClient mc, boolean editing) { return 3 * c().keySize + 2 * GAP; }

        @Override protected int contentHeight(MinecraftClient mc, boolean editing) {
            ClientConfig.Keystrokes c = c();
            int h = c.keySize * 2 + GAP;
            if (c.mouse) h += GAP + c.keySize;
            if (c.space) h += GAP + c.keySize / 2 + 2;
            return h;
        }

        @Override
        protected void drawContent(DrawContext ctx, MinecraftClient mc, int w, int h, boolean editing) {
            ClientConfig.Keystrokes c = c();
            TextRenderer tr = mc.textRenderer;
            int ks = c.keySize;
            var o = mc.options;
            key(ctx, tr, ks + GAP, 0, ks, ks, "W", null, o.forwardKey.isPressed(), c);
            int y = ks + GAP;
            key(ctx, tr, 0, y, ks, ks, "A", null, o.leftKey.isPressed(), c);
            key(ctx, tr, ks + GAP, y, ks, ks, "S", null, o.backKey.isPressed(), c);
            key(ctx, tr, 2 * (ks + GAP), y, ks, ks, "D", null, o.rightKey.isPressed(), c);
            y += ks + GAP;
            if (c.mouse) {
                int bw = (w - GAP) / 2;
                key(ctx, tr, 0, y, bw, ks, "LMB", c.cps ? InputTracker.left() + " CPS" : null, o.attackKey.isPressed(), c);
                key(ctx, tr, bw + GAP, y, w - bw - GAP, ks, "RMB", c.cps ? InputTracker.right() + " CPS" : null, o.useKey.isPressed(), c);
                y += ks + GAP;
            }
            if (c.space) {
                key(ctx, tr, 0, y, w, ks / 2 + 2, "", null, o.jumpKey.isPressed(), c);
                int lw = Math.max(6, w - 12);
                ctx.fill((w - lw) / 2, y + (ks / 2 + 2) / 2, (w + lw) / 2, y + (ks / 2 + 2) / 2 + 1,
                        o.jumpKey.isPressed() ? 0xFF111111 : 0xFF000000 | LyricsConfig.rgb(c.color, 0xFFFFFF));
            }
        }

        private void key(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, String label, String sub,
                         boolean down, ClientConfig.Keystrokes c) {
            int bgA = (int) (c.background / 100f * 255f);
            int pr = LyricsConfig.rgb(c.pressed, 0xFFFFFF);
            Ui.round(ctx, x, y, w, h, 3, down ? (0xD0 << 24 | pr) : (bgA << 24));
            int tc = down ? 0xFF111111 : (0xFF000000 | LyricsConfig.rgb(c.color, 0xFFFFFF));
            boolean sh = c.shadow && !down;
            if (label.isEmpty()) return;
            if (sub == null) {
                ctx.drawText(tr, label, x + (w - tr.getWidth(label)) / 2, y + (h - 8) / 2, tc, sh);
            } else {
                ctx.drawText(tr, label, x + (w - tr.getWidth(label)) / 2, y + 3, tc, sh);
                ctx.drawText(tr, sub, x + (w - tr.getWidth(sub)) / 2, y + h - 10, down ? 0xFF333333 : 0xFFB8B8C8, false);
            }
        }
    }

    // ------------------------------------------------------------------ armor
    static final class ArmorHud extends HudModule {
        private static final int PAD = 4;

        ArmorHud() {
            super("Armor status", "Armor pieces and held item with durability.", () -> ClientConfig.get().armor);
        }

        private ClientConfig.Armor c() { return ClientConfig.get().armor; }

        private List<ItemStack> stacks(MinecraftClient mc, boolean editing) {
            List<ItemStack> l = new ArrayList<>();
            ClientPlayerEntity p = mc.player;
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (EquipmentSlot s : slots) {
                ItemStack st = p.getEquippedStack(s);
                if (!st.isEmpty()) l.add(st);
            }
            if (c().held) {
                ItemStack st = p.getMainHandStack();
                if (!st.isEmpty()) l.add(st);
            }
            if (l.isEmpty() && editing) {
                l.add(new ItemStack(Items.DIAMOND_HELMET));
                l.add(new ItemStack(Items.DIAMOND_CHESTPLATE));
                l.add(new ItemStack(Items.DIAMOND_LEGGINGS));
                l.add(new ItemStack(Items.DIAMOND_BOOTS));
            }
            return l;
        }

        private String label(ItemStack s) {
            if (!c().durability) return "";
            if (s.isDamageable()) return String.valueOf(s.getMaxDamage() - s.getDamage());
            return s.getCount() > 1 ? String.valueOf(s.getCount()) : "";
        }

        private int labelColor(ItemStack s) {
            if (s.isDamageable()) {
                float ratio = 1f - (float) s.getDamage() / Math.max(1, s.getMaxDamage());
                return 0xFF000000 | (MathHelper.hsvToRgb(ratio / 3f, 0.85f, 1f) & 0xFFFFFF);
            }
            return 0xFFFFFFFF;
        }

        @Override protected int contentWidth(MinecraftClient mc, boolean editing) {
            List<ItemStack> l = stacks(mc, editing);
            if (l.isEmpty()) return 0;
            TextRenderer tr = mc.textRenderer;
            if (c().horizontal) {
                int w = PAD * 2;
                for (ItemStack s : l) w += Math.max(16, tr.getWidth(label(s))) + 4;
                return w - 4;
            }
            int tw = 0;
            for (ItemStack s : l) tw = Math.max(tw, tr.getWidth(label(s)));
            return PAD * 2 + 16 + (tw > 0 ? 4 + tw : 0);
        }

        @Override protected int contentHeight(MinecraftClient mc, boolean editing) {
            List<ItemStack> l = stacks(mc, editing);
            if (l.isEmpty()) return 0;
            if (c().horizontal) return PAD * 2 + 16 + (c().durability ? 10 : 0);
            return PAD * 2 + l.size() * 18 - 2;
        }

        @Override
        protected void drawContent(DrawContext ctx, MinecraftClient mc, int w, int h, boolean editing) {
            ClientConfig.Armor c = c();
            box(ctx, w, h, c);
            TextRenderer tr = mc.textRenderer;
            List<ItemStack> l = stacks(mc, editing);
            if (c.horizontal) {
                int x = PAD;
                for (ItemStack s : l) {
                    String t = label(s);
                    int cw = Math.max(16, tr.getWidth(t));
                    ctx.drawItem(s, x + (cw - 16) / 2, PAD);
                    if (!t.isEmpty()) ctx.drawText(tr, t, x + (cw - tr.getWidth(t)) / 2, PAD + 17, labelColor(s), c.shadow);
                    x += cw + 4;
                }
            } else {
                int y = PAD;
                for (ItemStack s : l) {
                    ctx.drawItem(s, PAD, y);
                    String t = label(s);
                    if (!t.isEmpty()) ctx.drawText(tr, t, PAD + 20, y + 4, labelColor(s), c.shadow);
                    y += 18;
                }
            }
        }
    }
}
