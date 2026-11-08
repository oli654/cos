package com.skyclient;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;

/** Polls the mouse buttons to compute clicks per second. */
public final class InputTracker {
    private InputTracker() {}

    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>();
    private static final ArrayDeque<Long> RIGHT = new ArrayDeque<>();
    private static boolean wasL, wasR;
    public static boolean leftDown, rightDown;

    public static void poll(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        long handle = mc.getWindow().getHandle();
        boolean inGame = mc.currentScreen == null;
        boolean l = inGame && GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean r = inGame && GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (l && !wasL) LEFT.addLast(now);
        if (r && !wasR) RIGHT.addLast(now);
        wasL = l;
        wasR = r;
        leftDown = l;
        rightDown = r;
        prune(LEFT, now);
        prune(RIGHT, now);
    }

    private static void prune(ArrayDeque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
    }

    public static int left() { return LEFT.size(); }
    public static int right() { return RIGHT.size(); }
}
