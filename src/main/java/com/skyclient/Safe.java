package com.skyclient;

import java.util.HashMap;
import java.util.Map;

/** Runs a feature and turns exceptions into log lines instead of a game crash. After 3 failures the feature is switched off until restart. */
public final class Safe {
    private Safe() {}

    private static final Map<String, Integer> ERRORS = new HashMap<>();

    public static String report() {
        StringBuilder sb = new StringBuilder();
        ERRORS.forEach((k, v) -> sb.append(k).append(" x").append(v).append(v >= 3 ? " (disabled)" : "").append("; "));
        return sb.length() == 0 ? "No feature errors so far." : "Errors: " + sb;
    }

    public static void run(String id, Runnable r) {
        if (ERRORS.getOrDefault(id, 0) >= 3) return;
        try {
            r.run();
        } catch (Throwable t) {
            int n = ERRORS.merge(id, 1, Integer::sum);
            SkyClientMod.LOGGER.error("Sky Client: '{}' failed ({}/3)", id, n, t);
            if (n >= 3) SkyClientMod.LOGGER.error("Sky Client: '{}' disabled until restart", id);
        }
    }
}
