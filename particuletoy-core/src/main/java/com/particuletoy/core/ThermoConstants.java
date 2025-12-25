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

    /** Cell face area used for pressure and radiation (m^2). */
    public static final float CELL_FACE_AREA_M2 = CELL_SIZE_M * CELL_SIZE_M;

    /** Default ambient conditions (for UI + initialization). */
    public static final float DEFAULT_AMBIENT_TEMP_C = 20.0f;
    public static final float DEFAULT_AMBIENT_PRESSURE_PA = 101_325.0f;

    /** Gravity for hydrostatic pressure. */
    public static final float GRAVITY_M_S2 = 9.81f;

    /** Stefan–Boltzmann constant (W/m^2/K^4). */
    public static final float STEFAN_BOLTZMANN = 5.670374419e-8f;

    /** Start of visible red heat (approx). */
    public static final float RADIATION_VISIBLE_START_C = 700.0f;

    /**
     * Radiation tuning. 1.0 is physically "raw" magnitude for the chosen cell size,
     * but in a game you often tune this.
     */
    public static final float RADIATION_SCALE = 1.0f;

    /**
     * Gameplay scaling for hydrostatic pressure.
     * With CELL_SIZE_M=1mm and world height ~200, real hydrostatic delta is small.
     * Set to ~20..100 to make Tb(P) visibly different with depth.
     * Set to 1.0 for strictly physical scaling.
     */
    public static final float PRESSURE_SCALE = 50.0f;
}
