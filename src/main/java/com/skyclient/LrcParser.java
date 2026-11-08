package com.skyclient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal .lrc parser: [mm:ss.xx]text, several timestamps per line, [offset:ms]; enhanced word tags are stripped. */
public final class LrcParser {
    public record Line(long timeMs, String text) {}

    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern WORD_TAG = Pattern.compile("<\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?>");
    private static final Pattern OFFSET = Pattern.compile("^\\[offset:\\s*([+-]?\\d+)\\s*\\]", Pattern.CASE_INSENSITIVE);

    private LrcParser() {}

    public static List<Line> parse(List<String> raw) {
        List<Line> out = new ArrayList<>();
        long offset = 0;
        for (String rawLine : raw) {
            String s = rawLine.replace("\uFEFF", "").strip();
            if (s.isEmpty()) continue;

            Matcher om = OFFSET.matcher(s);
            if (om.find()) {
                offset = Long.parseLong(om.group(1));
                continue;
            }

            List<Long> times = new ArrayList<>();
            int pos = 0;
            while (true) {
                Matcher m = TIME.matcher(s);
                m.region(pos, s.length());
                if (!m.lookingAt()) break;
                long min = Long.parseLong(m.group(1));
                long sec = Long.parseLong(m.group(2));
                long ms = 0;
                String frac = m.group(3);
                if (frac != null) {
                    ms = Long.parseLong(frac) * (frac.length() == 1 ? 100 : frac.length() == 2 ? 10 : 1);
                }
                times.add(min * 60_000 + sec * 1000 + ms);
                pos = m.end();
            }
            if (times.isEmpty()) continue;

            String text = WORD_TAG.matcher(s.substring(pos)).replaceAll("").strip();
            for (long t : times) out.add(new Line(Math.max(0, t - offset), text));
        }
        out.sort(Comparator.comparingLong(Line::timeMs));
        return out;
    }
}
