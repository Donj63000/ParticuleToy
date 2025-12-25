package com.particuletoy.core;

/**
 * Global constants for the temperature and heat simulation.
 */
public final class ThermoConstants {

    private ThermoConstants() {}

    /** Minimum supported temperature in Celsius (approx. absolute zero). */
    public static final float MIN_TEMP_C = -273.0f;

    /** Maximum supported temperature in Celsius (game design constraint). */
    public static final float MAX_TEMP_C = 10_000.0f;

    /**
     * Cell size used by the heat model (meters).
     *
     * A smaller cell makes temperature equalization faster (because mass scales with dx^3
     * while conduction contact scales with dx).
     */
    public static final float CELL_SIZE_M = 0.001f; // 1 mm

    public static final float CELL_VOLUME_M3 = CELL_SIZE_M * CELL_SIZE_M * CELL_SIZE_M;
}
