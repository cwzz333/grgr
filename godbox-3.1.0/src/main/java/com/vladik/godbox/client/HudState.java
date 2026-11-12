package com.vladik.godbox.client;

/** v3.0: последнее состояние времени и эпохи, присланное сервером. */
public final class HudState {
    public static float rate = 20f;
    public static boolean frozen;
    public static int age;
    public static int ageLeft;
    public static boolean agesOn = true;
    public static long updated;

    private HudState() {}

    public static void set(float r, boolean f, int a, int left, boolean on) {
        rate = r; frozen = f; age = a; ageLeft = left; agesOn = on;
        updated = System.currentTimeMillis();
    }
}
