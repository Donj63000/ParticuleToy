package com.particuletoy.core;

import java.util.Arrays;
import java.util.List;

/**
 * Supported element types for the MVP simulation.
 *
 * Notes:
 * - The simulation grid stores only the {@code id} (a byte) for performance.
 * - Colors are provided as ARGB ints (0xAARRGGBB) for fast rendering.
 */
public enum ElementType {
    EMPTY((byte) 0, "Empty", 0xFF000000),
    WALL((byte) 1, "Wall", 0xFF6B6B6B),
    SAND((byte) 2, "Sand", 0xFFE1C16E),
    WATER((byte) 3, "Water", 0xFF3D8BFF);

    private final byte id;
    private final String displayName;
    private final int argb;

    ElementType(byte id, String displayName, int argb) {
        this.id = id;
        this.displayName = displayName;
        this.argb = argb;
    }

    public byte id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public int argb() {
        return argb;
    }

    @Override
    public String toString() {
        return displayName;
    }

    private static final ElementType[] BY_ID = new ElementType[256];

    static {
        Arrays.fill(BY_ID, EMPTY);
        for (ElementType t : values()) {
            BY_ID[Byte.toUnsignedInt(t.id)] = t;
        }
    }

    public static ElementType fromId(byte id) {
        return BY_ID[Byte.toUnsignedInt(id)];
    }

    /**
     * Elements offered in the UI palette (we intentionally omit EMPTY, which is used as "erase").
     */
    public static List<ElementType> palette() {
        return List.of(WALL, SAND, WATER);
    }
}
