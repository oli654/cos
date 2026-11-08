package com.skyclient;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** Small immediate-mode widget set used by the menu. */
public final class Widgets {
    private Widgets() {}

    public abstract static class Widget {
        public int lx, ly, lw;

        public abstract int h();

        protected abstract void draw(DrawContext c, TextRenderer tr, int x, int y, int w, int mx, int my);

        public final void drawAt(DrawContext c, TextRenderer tr, int x, int y, int w, int mx, int my) {
            lx = x; ly = y; lw = w;
            draw(c, tr, x, y, w, mx, my);
        }

        public boolean click(int mx, int my, int btn) { return false; }
        public void drag(int mx, int my) {}
        public void release() {}
    }

    static void drawSwitch(DrawContext c, int x, int y, boolean on) {
        Ui.round(c, x, y, 24, 12, 6, on ? Ui.argb(Ui.accent(), 255) : Ui.OFF);
        Ui.round(c, x + (on ? 14 : 2), y + 2, 8, 8, 4, Ui.WHITE);
    }

    // ------------------------------------------------------------ simple widgets
    public static final class HeaderW extends Widget {
        private final String text;
        public HeaderW(String text) { this.text = text.toUpperCase(Locale.ROOT); }
        @Override public int h() { return 14; }
        @Override protected void draw(DrawContext c, TextRenderer tr, int x, int y, int w, int mx, int my) {
            c.drawText(tr, text, x, y + 3, Ui.DIM, false);
            c.fill(x + tr.getWidth(text) + 6, y + 7, x + w, y + 8, Ui.LINE);
        }
    }

    public static final class InfoW extends Widget {
        private final String[] lines;
        public InfoW(String... lines) { this.lines = lines; }
        @Override public int h() { return lines.length * 11; }
        @Override protected void draw(DrawContext c, TextRenderer tr, int x, int y, int w, int mx, int my) {
            for (int i = 0; i < lines.length; i++) c.drawText(tr, lines[i], x, y + i * 11, Ui.DIM, false);
        }
    }

    public static final class ButtonW extends Widget {
        private final String label;
        private final Runnable action;
        public ButtonW(String label, Runnable action) { this.label = label; this.action = action; }
        @Override public int h() { return 20; }
        @Override protected void draw(DrawContext c, TextRenderer tr, int x, int y, int w, int mx, int my) {
            boolean hov = Ui.in(mx, my, x, y, w, 20);
            Ui.round(c, x, y, w, 20, 4, hov ? Ui.argb(Ui.accent(), 150) : Ui.CARD_HOVER);
            c.drawCenteredTextWithShadow(tr, label, x + w / 2, y + 6, Ui.TEXT);
        }
        @Override public boolean click(int mx, int my, int btn) {
            if (btn == 0 && Ui.in(mx, my, lx, ly, lw, 20)) { action.run(); return true; }
            return false;
        }
    }

    public static final class ToggleW extends Widget {
        private final String label;
        private final BooleanSupplier get;
        private final Consumer<Boolean> set;
        public ToggleW(String label, BooleanSupplier get, Consumer<Boolean> set) {
            this.label = label; this.get = get; this.set = set;
        }
        @Override public int h() { return 18; }
        @Override protected void draw(DrawContext c, TextRenderer tr, int x, int y, int w, int mx, int my) {
            c.drawText(tr, label, x, y + 5, Ui.TEXT, false);
            drawSwitch(c, x + w - 24, y + 3, get.getAsBoolean());
        }
        @Override public boolean click(int mx, int my, int btn) {
            if (btn == 0 && Ui.in(mx, my, lx, ly, lw, 18)) { set.accept(!get.getAsBoolean()); return true; }
            return false;
        }
    }

    public static final class SliderW extends Widget {
        private final String label;
        private final double min, max, step;
        private final boolean whole;
        private final DoubleSupplier get;
        private final DoubleConsumer set;
        private boolean dragging;

        public SliderW(String label, double min, double max, double step, boolean whole, DoubleSupplier get, DoubleConsumer set) {
            this.label = label; this.min = min; this.max = max; this.step = step; this.whole = whole || step >= 1;
            this.get = get; this.set = set;
        }
        @Override public int h() { return 26; }
        @Override protected void draw(DrawContext c, TextRenderer tr, int x, int y, int w, int mx, int my) {
            double v = get.getAsDouble();
            String vs = whole ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, step >= 0.1 ? "%.1f" : "%.2f", v);
            c.drawText(tr, label, x, y + 1, Ui.TEXT, false);
            c.drawText(tr, vs, x + w - tr.getWidth(vs), y + 1, Ui.DIM, false);
            int ty = y + 16;
            double t = MathHelper.clamp((v - min) / (max - min), 0, 1);
            Ui.round(c, x, ty, w, 4, 2, Ui.OFF);
            int fw = (int) (w * t);
            if (fw > 0) Ui.round(c, x, ty, Math.max(4, fw), 4, 2, Ui.argb(Ui.accent(), 255));
            Ui.round(c, x + (int) ((w - 8) * t), ty - 2, 8, 8, 4, Ui.WHITE);
        }
        private void update(int mx) {
            double t = MathHelper.clamp((mx - lx) / (double) lw, 0, 1);
            double v = min + t * (max - min);
            if (step > 0) v = Math.round(v / step) * step;
            set.accept(MathHelper.clamp(v, min, max));
        }
        @Override public boolean click(int mx, int my, int btn) {
            if (btn == 0 && Ui.in(mx, my, lx, ly + 8, lw, 18)) { dragging = true; update(mx); return true; }
            return false;
        }
        @Override public void drag(int mx, int my) { if (dragging) update(mx); }
        @Override public void release() { dragging = false; }
    }

    public static final class ColorW extends Widget {
        private static final int[] SWATCHES = {
                0xFFFFFF, 0xFF4D4D, 0xFF9F43, 0xFFD84D, 0x4DFF88, 0x4DE1FF, 0x4D7CFF, 0x9B6BFF, 0xFF6BD6, 0x111111};
        private final String label;
        private final Supplier<String> get;
        private final Consumer<String> set;
        private boolean dragging;

        public ColorW(String label, Supplier<String> get, Consumer<String> set) {
            this.label = label; this.get = get; this.set = set;
        }
        @Override public int h() { return 46; }
        private int hueW() { return Math.min(lw, 150); }
        @Override protected void draw(DrawContext c, TextRenderer tr, int x, int y, int w, int mx, int my) {
            int cur = LyricsConfig.rgb(get.get(), 0xFFFFFF);
            c.drawText(tr, label, x, y + 1, Ui.TEXT, false);
            String hex = String.format("#%06X", cur);
            c.drawText(tr, hex, x + w - 14 - tr.getWidth(hex), y + 1, Ui.DIM, false);
            Ui.round(c, x + w - 10, y, 10, 10, 3, 0xFF000000 | cur);
            for (int i = 0; i < SWATCHES.length; i++) {
                int sx = x + i * 15;
                Ui.round(c, sx, y + 13, 12, 12, 3, 0xFF000000 | SWATCHES[i]);
                if (SWATCHES[i] == cur) c.drawBorder(sx - 1, y + 12, 14, 14, 0xFFFFFFFF);
            }
            int hw = hueW();
            for (int i = 0; i < hw; i++) {
                int col = MathHelper.hsvToRgb(i / (float) hw, 1f, 1f) & 0xFFFFFF;
                c.fill(x + i, y + 31, x + i + 1, y + 39, 0xFF000000 | col);
            }
        }
        private void pickHue(int mx) {
            double t = MathHelper.clamp((mx - lx) / (double) hueW(), 0, 0.999);
            int col = MathHelper.hsvToRgb((float) t, 1f, 1f) & 0xFFFFFF;
            set.accept(String.format("#%06X", col));
        }
        @Override public boolean click(int mx, int my, int btn) {
            if (btn != 0) return false;
            for (int i = 0; i < SWATCHES.length; i++) {
                if (Ui.in(mx, my, lx + i * 15, ly + 13, 12, 12)) { set.accept(String.format("#%06X", SWATCHES[i])); return true; }
            }
            if (Ui.in(mx, my, lx, ly + 29, hueW(), 12)) { dragging = true; pickHue(mx); return true; }
            return false;
        }
        @Override public void drag(int mx, int my) { if (dragging) pickHue(mx); }
        @Override public void release() { dragging = false; }
    }

    public static final class EnumW extends Widget {
        private final String label;
        private final Object[] values;
        private final Supplier<Object> get;
        private final Consumer<Object> set;
        public EnumW(String label, Object[] values, Supplier<Object> get, Consumer<Object> set) {
            this.label = label; this.values = values; this.get = get; this.set = set;
        }
        @Override public int h() { return 20; }
        @Override protected void draw(DrawContext c, TextRenderer tr, int x, int y, int w, int mx, int my) {
            c.drawText(tr, label, x, y + 6, Ui.TEXT, false);
            String v = String.valueOf(get.get());
            int bw = tr.getWidth(v) + 14;
            boolean hov = Ui.in(mx, my, x + w - bw, y, bw, 18);
            Ui.round(c, x + w - bw, y, bw, 18, 4, hov ? Ui.argb(Ui.accent(), 150) : Ui.CARD_HOVER);
            c.drawText(tr, v, x + w - bw + 7, y + 5, Ui.TEXT, false);
        }
        @Override public boolean click(int mx, int my, int btn) {
            if (btn != 0 || !Ui.in(mx, my, lx, ly, lw, 18)) return false;
            int idx = 0;
            for (int i = 0; i < values.length; i++) if (values[i] == get.get()) idx = i;
            set.accept(values[(idx + 1) % values.length]);
            return true;
        }
    }

    public static final class PairW extends Widget {
        private final String l1, l2;
        private final Runnable a1, a2;
        public PairW(String l1, Runnable a1, String l2, Runnable a2) {
            this.l1 = l1; this.a1 = a1; this.l2 = l2; this.a2 = a2;
        }
        @Override public int h() { return 20; }
        private static void drawBtn(DrawContext c, TextRenderer tr, int x, int y, int w, String l, int mx, int my) {
            boolean hov = Ui.in(mx, my, x, y, w, 20);
            Ui.round(c, x, y, w, 20, 4, hov ? Ui.argb(Ui.accent(), 150) : Ui.CARD_HOVER);
            c.drawCenteredTextWithShadow(tr, l, x + w / 2, y + 6, Ui.TEXT);
        }
        @Override protected void draw(DrawContext c, TextRenderer tr, int x, int y, int w, int mx, int my) {
            int half = (w - 6) / 2;
            drawBtn(c, tr, x, y, half, l1, mx, my);
            drawBtn(c, tr, x + half + 6, y, w - half - 6, l2, mx, my);
        }
        @Override public boolean click(int mx, int my, int btn) {
            if (btn != 0 || !Ui.in(mx, my, lx, ly, lw, 20)) return false;
            int half = (lw - 6) / 2;
            if (mx < lx + half) a1.run();
            else if (mx >= lx + half + 6) a2.run();
            else return false;
            return true;
        }
    }

    // ------------------------------------------------------------ module card
    public static final class ModuleCard extends Widget {
        private static final int HEAD = 32;
        private final String name, desc;
        private final BooleanSupplier getOn;
        private final Consumer<Boolean> setOn;
        private final List<Widget> kids;
        private boolean open;
        private Widget dragKid;

        public ModuleCard(String name, String desc, BooleanSupplier getOn, Consumer<Boolean> setOn, List<Widget> kids) {
            this.name = name; this.desc = desc; this.getOn = getOn; this.setOn = setOn; this.kids = kids;
        }
        @Override public int h() {
            if (!open || kids.isEmpty()) return HEAD;
            int t = HEAD + 6;
            for (Widget k : kids) t += k.h() + 5;
            return t + 3;
        }
        @Override protected void draw(DrawContext c, TextRenderer tr, int x, int y, int w, int mx, int my) {
            boolean hov = Ui.in(mx, my, x, y, w, HEAD);
            Ui.round(c, x, y, w, h(), 5, hov ? Ui.CARD_HOVER : Ui.CARD);
            c.drawText(tr, name, x + 10, desc.isEmpty() ? y + 12 : y + 7, Ui.TEXT, false);
            if (!desc.isEmpty()) c.drawText(tr, tr.trimToWidth(desc, w - 90), x + 10, y + 19, Ui.DIM, false);
            int rx = x + w - 10;
            if (getOn != null) { drawSwitch(c, rx - 24, y + 10, getOn.getAsBoolean()); rx -= 32; }
            if (!kids.isEmpty()) c.drawText(tr, open ? "v" : ">", rx - 8, y + 12, Ui.DIM, false);
            if (open) {
                int ky = y + HEAD + 6;
                for (Widget k : kids) {
                    k.drawAt(c, tr, x + 10, ky, w - 20, mx, my);
                    ky += k.h() + 5;
                }
            }
        }
        @Override public boolean click(int mx, int my, int btn) {
            if (btn != 0) return false;
            if (Ui.in(mx, my, lx, ly, lw, HEAD)) {
                if (getOn != null && Ui.in(mx, my, lx + lw - 38, ly + 6, 34, 20)) {
                    setOn.accept(!getOn.getAsBoolean());
                    return true;
                }
                if (!kids.isEmpty()) { open = !open; return true; }
                return false;
            }
            if (open) {
                int ky = ly + HEAD + 6;
                for (Widget k : kids) {
                    if (Ui.in(mx, my, lx + 10, ky, lw - 20, k.h()) && k.click(mx, my, btn)) { dragKid = k; return true; }
                    ky += k.h() + 5;
                }
            }
            return false;
        }
        @Override public void drag(int mx, int my) { if (dragKid != null) dragKid.drag(mx, my); }
        @Override public void release() { if (dragKid != null) dragKid.release(); dragKid = null; }
    }

    // ------------------------------------------------------------ music widgets
    public static final class PlayerW extends Widget {
        @Override public int h() { return 66; }
        @Override protected void draw(DrawContext c, TextRenderer tr, int x, int y, int w, int mx, int my) {
            Ui.round(c, x, y, w, 66, 5, Ui.CARD);
            LyricsPlayer p = LyricsPlayer.INSTANCE;
            if (!p.isActive()) {
                c.drawText(tr, "Nothing playing", x + 10, y + 16, Ui.TEXT, false);
                c.drawText(tr, "Pick a song from the library below.", x + 10, y + 30, Ui.DIM, false);
                return;
            }
            long pos = p.displayPositionMs();
            long dur = Math.max(1, p.durationMs());
            c.drawText(tr, "\u266A " + p.song().name(), x + 10, y + 8, Ui.TEXT, false);
            String tm = Ui.fmtTime(pos) + " / " + Ui.fmtTime(dur);
            c.drawText(tr, tm, x + w - 10 - tr.getWidth(tm), y + 8, Ui.DIM, false);
            int bx = x + 10, bw = w - 20;
            Ui.round(c, bx, y + 30, bw, 5, 2, Ui.OFF);
            int fw = (int) (bw * MathHelper.clamp(pos / (double) dur, 0, 1));
            if (fw > 0) Ui.round(c, bx, y + 30, Math.max(4, fw), 5, 2, Ui.argb(Ui.accent(), 255));
            int idx = p.indexAt(p.positionMs());
            if (idx >= 0 && p.positionMs() < p.song().ends()[idx]) {
                c.drawText(tr, tr.trimToWidth(p.song().lines().get(idx).text(), w - 20), x + 10, y + 39, Ui.DIM, false);
            }
            boolean h1 = Ui.in(mx, my, x + 10, y + 49, 56, 13);
            boolean h2 = Ui.in(mx, my, x + 72, y + 49, 40, 13);
            Ui.round(c, x + 10, y + 49, 56, 13, 4, h1 ? Ui.argb(Ui.accent(), 150) : Ui.CARD_HOVER);
            Ui.round(c, x + 72, y + 49, 40, 13, 4, h2 ? Ui.argb(Ui.accent(), 150) : Ui.CARD_HOVER);
            String pl = p.isPlaying() ? "Pause" : "Resume";
            c.drawText(tr, pl, x + 10 + (56 - tr.getWidth(pl)) / 2, y + 52, Ui.TEXT, false);
            c.drawText(tr, "Stop", x + 72 + (40 - tr.getWidth("Stop")) / 2, y + 52, Ui.TEXT, false);
            boolean h3 = Ui.in(mx, my, x + 118, y + 49, 40, 13);
            Ui.round(c, x + 118, y + 49, 40, 13, 4, h3 ? Ui.argb(Ui.accent(), 150) : Ui.CARD_HOVER);
            c.drawText(tr, "Next", x + 118 + (40 - tr.getWidth("Next")) / 2, y + 52, Ui.TEXT, false);
        }
        @Override public boolean click(int mx, int my, int btn) {
            LyricsPlayer p = LyricsPlayer.INSTANCE;
            if (btn != 0 || !p.isActive()) return false;
            if (Ui.in(mx, my, lx + 10, ly + 26, lw - 20, 13)) {
                double t = MathHelper.clamp((mx - (lx + 10)) / (double) (lw - 20), 0, 1);
                Ui.toast(p.seek((long) (t * p.durationMs())));
                return true;
            }
            if (Ui.in(mx, my, lx + 10, ly + 49, 56, 13)) {
                Ui.toast(p.isPlaying() ? p.pause() : p.resume());
                return true;
            }
            if (Ui.in(mx, my, lx + 72, ly + 49, 40, 13)) { p.stop(); return true; }
            if (Ui.in(mx, my, lx + 118, ly + 49, 40, 13)) {
                try { Ui.toast(p.next()); } catch (IllegalArgumentException e) { Ui.toast(e.getMessage()); }
                return true;
            }
            return false;
        }
    }

    public static final class SongRowW extends Widget {
        private final String name;
        private final boolean wav;
        public SongRowW(String name) { this.name = name; this.wav = LyricsPlayer.hasWav(name); }
        @Override public int h() { return 22; }
        @Override protected void draw(DrawContext c, TextRenderer tr, int x, int y, int w, int mx, int my) {
            boolean hov = Ui.in(mx, my, x, y, w, 22);
            LyricsPlayer p = LyricsPlayer.INSTANCE;
            boolean cur = p.isActive() && p.song().name().equals(name);
            Ui.round(c, x, y, w, 22, 4, hov ? Ui.CARD_HOVER : Ui.CARD);
            c.drawText(tr, tr.trimToWidth(name, w - 120), x + 10, y + 7, cur ? (0xFF000000 | Ui.accent()) : Ui.TEXT, false);
            String tag = wav ? "WAV" : "lyrics only";
            c.drawText(tr, tag, x + w - 60 - tr.getWidth(tag), y + 7, Ui.DIM, false);
            Ui.round(c, x + w - 50, y + 3, 44, 16, 4, hov ? Ui.argb(Ui.accent(), 200) : Ui.argb(Ui.accent(), 90));
            c.drawText(tr, "Play", x + w - 50 + (44 - tr.getWidth("Play")) / 2, y + 7, Ui.WHITE, false);
        }
        @Override public boolean click(int mx, int my, int btn) {
            if (btn != 0 || !Ui.in(mx, my, lx, ly, lw, 22)) return false;
            try {
                Ui.toast(LyricsPlayer.INSTANCE.play(name));
            } catch (IllegalArgumentException e) {
                Ui.toast(e.getMessage());
            }
            return true;
        }
    }

    // ------------------------------------------------------------ reflection builders
    private static boolean getB(Field f, Object o) { try { return f.getBoolean(o); } catch (Exception e) { return false; } }
    private static void setB(Field f, Object o, boolean v) { try { f.setBoolean(o, v); } catch (Exception ignored) { } }
    private static double getN(Field f, Object o) { try { return ((Number) f.get(o)).doubleValue(); } catch (Exception e) { return 0; } }
    private static void setN(Field f, Object o, double v) {
        try {
            if (f.getType() == int.class) f.setInt(o, (int) Math.round(v)); else f.setFloat(o, (float) v);
        } catch (Exception ignored) { }
    }
    private static Object getO(Field f, Object o) { try { return f.get(o); } catch (Exception e) { return null; } }
    private static void setO(Field f, Object o, Object v) { try { f.set(o, v); } catch (Exception ignored) { } }

    private static Widget make(Field f, Opt o, Object obj, Runnable onChange) {
        Class<?> t = f.getType();
        if (t == boolean.class) {
            return new ToggleW(o.value(), () -> getB(f, obj), v -> { setB(f, obj, v); onChange.run(); });
        }
        if (t == int.class) {
            return new SliderW(o.value(), o.min(), o.max(), o.step() > 0 ? o.step() : 1, true,
                    () -> getN(f, obj), v -> { setN(f, obj, v); onChange.run(); });
        }
        if (t == float.class) {
            return new SliderW(o.value(), o.min(), o.max(), o.step() > 0 ? o.step() : 0.01, false,
                    () -> getN(f, obj), v -> { setN(f, obj, v); onChange.run(); });
        }
        if (t == String.class && o.color()) {
            return new ColorW(o.value(), () -> String.valueOf(getO(f, obj)), v -> { setO(f, obj, v); onChange.run(); });
        }
        if (t.isEnum()) {
            return new EnumW(o.value(), t.getEnumConstants(), () -> getO(f, obj), v -> { setO(f, obj, v); onChange.run(); });
        }
        return null;
    }

    /** Builds widgets for every @Opt field, grouped by Opt.group (superclass fields first). */
    public static LinkedHashMap<String, List<Widget>> optionsByGroup(Object obj, Runnable onChange) {
        LinkedHashMap<String, List<Widget>> out = new LinkedHashMap<>();
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) chain.add(0, c);
        for (Class<?> c : chain) {
            for (Field f : c.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (!Modifier.isPublic(mod) || Modifier.isStatic(mod)) continue;
                Opt o = f.getAnnotation(Opt.class);
                if (o == null) continue;
                Widget w = make(f, o, obj, onChange);
                if (w != null) out.computeIfAbsent(o.group(), k -> new ArrayList<>()).add(w);
            }
        }
        return out;
    }

    public static List<Widget> options(Object obj, Runnable onChange) {
        List<Widget> all = new ArrayList<>();
        for (List<Widget> l : optionsByGroup(obj, onChange).values()) all.addAll(l);
        return all;
    }

    /** Card whose header toggle is bound to the object's public boolean field "enabled" (if it exists). */
    public static ModuleCard card(String name, String desc, Object obj, Runnable onChange) {
        BooleanSupplier g = null;
        Consumer<Boolean> s = null;
        try {
            Field en = obj.getClass().getField("enabled");
            g = () -> getB(en, obj);
            s = v -> { setB(en, obj, v); onChange.run(); };
        } catch (NoSuchFieldException ignored) { }
        return new ModuleCard(name, desc, g, s, options(obj, onChange));
    }
}
