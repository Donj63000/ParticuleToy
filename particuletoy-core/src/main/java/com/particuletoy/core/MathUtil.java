package com.particuletoy.core;

/**
 * Small math helpers (avoid pulling extra dependencies).
 */
public final class MathUtil {

    private MathUtil() {}

    public static int clamp(int v, int min, int max) {
        if (v < min) return min;
        return Math.min(v, max);
    }
}
