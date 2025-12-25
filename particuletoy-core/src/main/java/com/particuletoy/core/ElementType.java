package com.particuletoy.core;

import java.util.Arrays;
import java.util.List;

/**
 * Element types supported by the simulation.
 *
 * Design goals:
 * - The simulation grid stores only the {@code id} (a byte) for performance.
 * - Rendering uses a fast ARGB color per element.
 * - Thermodynamics uses per-element properties (cp, conductivity) and per-family phase rules.
 */
public enum ElementType {

    // --- Gases / background ---
    /**
     * "Empty" cells represent air.
     *
     * We keep it as an actual thermodynamic medium so hot objects can heat nearby air, and
     * air can act as a cooling sink when coupled to an ambient temperature.
     */
    EMPTY((byte) 0, "Air", 0xFF000000,
            MaterialFamily.AIR, Phase.GAS,
            false, true,
            1.225f, 1005f, 0.024f),

    // --- Solids ---
    WALL((byte) 1, "Stone", 0xFF6B6B6B,
            MaterialFamily.ROCK, Phase.SOLID,
            true, false,
            2700f, 790f, 2.8f),

    SAND((byte) 2, "Sand", 0xFFE1C16E,
            MaterialFamily.SAND, Phase.SOLID,
            false, false,
            1600f, 830f, 0.27f),

    // --- Liquids ---
    WATER((byte) 3, "Water", 0xFF3D8BFF,
            MaterialFamily.WATER, Phase.LIQUID,
            false, false,
            1000f, 4182f, 0.6f),

    // --- Border / containment ---
    BEDROCK((byte) 4, "Bedrock", 0xFF4A4A4A,
            MaterialFamily.ROCK, Phase.SOLID,
            true, true,
            2700f, 790f, 2.8f),

    // --- Water phases ---
    ICE((byte) 5, "Ice", 0xFFD8F0FF,
            MaterialFamily.WATER, Phase.SOLID,
            false, false,
            917f, 2050f, 2.22f),

    STEAM((byte) 6, "Steam", 0xFFCCCCCC,
            MaterialFamily.WATER, Phase.GAS,
            false, false,
            0.6f, 2010f, 0.025f),

    // --- Sand / silica phases ---
    MOLTEN_SILICA((byte) 7, "Molten Silica", 0xFFFF9A2E,
            MaterialFamily.SAND, Phase.LIQUID,
            false, false,
            2200f, 1000f, 1.5f),

    SILICA_VAPOR((byte) 8, "Silica Vapor", 0xFFBFA6FF,
            MaterialFamily.SAND, Phase.GAS,
            false, false,
            1.0f, 1200f, 0.03f),

    // --- Rock phases ---
    MOLTEN_ROCK((byte) 9, "Molten Rock", 0xFFFF3B1F,
            MaterialFamily.ROCK, Phase.LIQUID,
            false, false,
            2600f, 1200f, 1.5f),

    ROCK_VAPOR((byte) 10, "Rock Vapor", 0xFFFF66CC,
            MaterialFamily.ROCK, Phase.GAS,
            false, false,
            1.2f, 1300f, 0.04f);

    private final byte id;
    private final String displayName;
    private final int argb;
    private final MaterialFamily family;
    private final Phase phase;

    /**
     * If true, the element does not move (static terrain).
     *
     * Note: It can still change phase if {@link #phaseLocked} is false.
     */
    private final boolean immobile;

    /**
     * If true, the element never changes phase, even if energy says it should.
     * Useful for borders/containment (BEDROCK) and for future gameplay tools.
     */
    private final boolean phaseLocked;

    /** Density used for buoyancy-ish heuristics and future improvements. */
    private final float densityKgM3;

    /** Specific heat capacity (J/kg/K). */
    private final float specificHeatJKgK;

    /** Thermal conductivity (W/m/K). */
    private final float thermalConductivityWMK;

    ElementType(
            byte id,
            String displayName,
            int argb,
            MaterialFamily family,
            Phase phase,
            boolean immobile,
            boolean phaseLocked,
            float densityKgM3,
            float specificHeatJKgK,
            float thermalConductivityWMK
    ) {
        this.id = id;
        this.displayName = displayName;
        this.argb = argb;
        this.family = family;
        this.phase = phase;
        this.immobile = immobile;
        this.phaseLocked = phaseLocked;
        this.densityKgM3 = densityKgM3;
        this.specificHeatJKgK = specificHeatJKgK;
        this.thermalConductivityWMK = thermalConductivityWMK;
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

    public MaterialFamily family() {
        return family;
    }

    public Phase phase() {
        return phase;
    }

    public boolean immobile() {
        return immobile;
    }

    public boolean phaseLocked() {
        return phaseLocked;
    }

    public float densityKgM3() {
        return densityKgM3;
    }

    public float specificHeatJKgK() {
        return specificHeatJKgK;
    }

    public float thermalConductivityWMK() {
        return thermalConductivityWMK;
    }

    // --- Lookup by id ---
    private static final ElementType[] BY_ID;

    static {
        int max = 0;
        for (ElementType t : values()) {
            max = Math.max(max, Byte.toUnsignedInt(t.id));
        }
        ElementType[] tmp = new ElementType[max + 1];
        for (ElementType t : values()) {
            int idx = Byte.toUnsignedInt(t.id);
            if (tmp[idx] != null) {
                throw new IllegalStateException("Duplicate element id " + idx + " for " + t + " and " + tmp[idx]);
            }
            tmp[idx] = t;
        }
        for (int i = 0; i < tmp.length; i++) {
            if (tmp[i] == null) {
                throw new IllegalStateException("Missing ElementType for id " + i);
            }
        }
        BY_ID = tmp;
    }

    public static ElementType fromId(byte id) {
        int idx = Byte.toUnsignedInt(id);
        if (idx < 0 || idx >= BY_ID.length) return EMPTY;
        return BY_ID[idx];
    }

    /**
     * What the player can paint directly.
     *
     * (Phases like ICE/STEAM are created by temperature changes.)
     */
    public static List<ElementType> palette() {
        return Arrays.asList(WALL, SAND, WATER);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
