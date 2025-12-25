package com.particuletoy.core;

/**
 * Thermodynamics helper functions.
 *
 * We simulate heat transfer by storing an energy value per cell (Joules).
 * Temperature is derived from energy using a piecewise-linear enthalpy model that includes
 * latent heat plateaus for melting and boiling.
 *
 * Important simplifications (intentional for a cellular sandbox):
 * - No pressure model (phase boundaries are fixed).
 * - No mass/volume expansion when vaporizing (a cell stays one cell).
 * - Specific heat and conductivity are treated as constants per phase.
 */
public final class Thermo {

    private Thermo() {}

    // -----------------------------
    // Family phase-change parameters
    // -----------------------------

    // Water (values are typical at 1 atm)
    private static final float WATER_MELT_C = 0.0f;
    private static final float WATER_BOIL_C = 100.0f;
    private static final float WATER_LATENT_FUSION_JKG = 333_550f;
    private static final float WATER_LATENT_VAPOR_JKG = 2_256_000f;

    // Sand / silica (game approximation)
    private static final float SAND_MELT_C = 1550.0f;
    private static final float SAND_BOIL_C = 2230.0f;
    private static final float SAND_LATENT_FUSION_JKG = 156_000f;
    private static final float SAND_LATENT_VAPOR_JKG = 10_000_000f;

    // Rock / granite-like (game approximation)
    private static final float ROCK_MELT_C = 1250.0f;
    private static final float ROCK_BOIL_C = 3000.0f;
    private static final float ROCK_LATENT_FUSION_JKG = 400_000f;
    private static final float ROCK_LATENT_VAPOR_JKG = 5_000_000f;

    public static float clampTempC(float tempC) {
        return MathUtil.clamp(tempC, ThermoConstants.MIN_TEMP_C, ThermoConstants.MAX_TEMP_C);
    }

    /**
     * Reference density used to compute the cell mass (kg) for a family.
     * We intentionally keep a fixed mass-per-cell per family (no expansion).
     */
    public static float referenceDensityKgM3(MaterialFamily family) {
        switch (family) {
            case AIR:
                return ElementType.EMPTY.densityKgM3();
            case WATER:
                return ElementType.WATER.densityKgM3();
            case SAND:
                return ElementType.SAND.densityKgM3();
            case ROCK:
                return ElementType.WALL.densityKgM3();
            default:
                return ElementType.EMPTY.densityKgM3();
        }
    }

    public static float massKgPerCell(MaterialFamily family) {
        return referenceDensityKgM3(family) * ThermoConstants.CELL_VOLUME_M3;
    }

    /**
     * Convert temperature to energy for the current element.
     *
     * If temp is exactly at a phase boundary, we bias energy to match the current phase:
     * - at melting point: SOLID uses start of melt plateau, LIQUID/GAS uses end of plateau
     * - at boiling point: LIQUID/SOLID uses start of boil plateau, GAS uses end of plateau
     */
    public static float energyForTemperature(ElementType currentType, float tempC) {
        tempC = clampTempC(tempC);
        MaterialFamily family = currentType.family();

        if (family == MaterialFamily.AIR) {
            float m = massKgPerCell(MaterialFamily.AIR);
            float cp = ElementType.EMPTY.specificHeatJKgK();
            return m * cp * tempC;
        }

        float m = massKgPerCell(family);

        ElementType solid = solidType(family);
        ElementType liquid = liquidType(family);
        ElementType gas = gasType(family);

        float cpSolid = solid.specificHeatJKgK();
        float cpLiquid = liquid.specificHeatJKgK();
        float cpGas = gas.specificHeatJKgK();

        float tm = meltPointC(family);
        float tb = boilPointC(family);
        float lf = latentFusionJkg(family);
        float lv = latentVaporJkg(family);

        float eSolidAtMelt = m * cpSolid * tm;
        float eAfterMelt = eSolidAtMelt + m * lf;
        float eLiquidAtBoil = eAfterMelt + m * cpLiquid * (tb - tm);
        float eAfterBoil = eLiquidAtBoil + m * lv;

        if (tempC < tm) {
            return m * cpSolid * tempC;
        }
        if (tempC > tb) {
            return eAfterBoil + m * cpGas * (tempC - tb);
        }
        if (tempC > tm && tempC < tb) {
            return eAfterMelt + m * cpLiquid * (tempC - tm);
        }

        if (tempC == tm) {
            if (currentType.phase() == Phase.SOLID) return eSolidAtMelt;
            return eAfterMelt;
        }
        if (currentType.phase() == Phase.GAS) return eAfterBoil;
        return eLiquidAtBoil;
    }

    /**
     * Convert energy to temperature for a given element (phase bias only matters on plateaus).
     */
    public static float temperatureC(ElementType currentType, float energyJ) {
        MaterialFamily family = currentType.family();

        if (family == MaterialFamily.AIR) {
            float m = massKgPerCell(MaterialFamily.AIR);
            float cp = ElementType.EMPTY.specificHeatJKgK();
            if (m * cp == 0f) return 0f;
            return clampTempC(energyJ / (m * cp));
        }

        float m = massKgPerCell(family);

        ElementType solid = solidType(family);
        ElementType liquid = liquidType(family);
        ElementType gas = gasType(family);

        float cpSolid = solid.specificHeatJKgK();
        float cpLiquid = liquid.specificHeatJKgK();
        float cpGas = gas.specificHeatJKgK();

        float tm = meltPointC(family);
        float tb = boilPointC(family);
        float lf = latentFusionJkg(family);
        float lv = latentVaporJkg(family);

        float eSolidAtMelt = m * cpSolid * tm;
        float eAfterMelt = eSolidAtMelt + m * lf;
        float eLiquidAtBoil = eAfterMelt + m * cpLiquid * (tb - tm);
        float eAfterBoil = eLiquidAtBoil + m * lv;

        float t;
        if (energyJ < eSolidAtMelt) {
            float denom = m * cpSolid;
            if (denom == 0f) t = 0f;
            else t = energyJ / denom;
        } else if (energyJ < eAfterMelt) {
            t = tm;
        } else if (energyJ < eLiquidAtBoil) {
            float denom = m * cpLiquid;
            if (denom == 0f) t = tm;
            else t = tm + (energyJ - eAfterMelt) / denom;
        } else if (energyJ < eAfterBoil) {
            t = tb;
        } else {
            float denom = m * cpGas;
            if (denom == 0f) t = tb;
            else t = tb + (energyJ - eAfterBoil) / denom;
        }

        return clampTempC(t);
    }

    /**
     * Update element phase according to family thresholds and current energy.
     *
     * Plateau behavior:
     * - In melting plateau: SOLID stays SOLID until end, LIQUID stays LIQUID until start
     * - In boiling plateau: LIQUID stays LIQUID until end, GAS stays GAS until start
     */
    public static ElementType updatePhase(ElementType currentType, float energyJ) {
        if (currentType.phaseLocked()) return currentType;

        MaterialFamily family = currentType.family();
        if (family == MaterialFamily.AIR) return currentType;

        float m = massKgPerCell(family);

        ElementType solid = solidType(family);
        ElementType liquid = liquidType(family);
        ElementType gas = gasType(family);

        float cpSolid = solid.specificHeatJKgK();
        float cpLiquid = liquid.specificHeatJKgK();

        float tm = meltPointC(family);
        float tb = boilPointC(family);
        float lf = latentFusionJkg(family);
        float lv = latentVaporJkg(family);

        float eSolidAtMelt = m * cpSolid * tm;
        float eAfterMelt = eSolidAtMelt + m * lf;
        float eLiquidAtBoil = eAfterMelt + m * cpLiquid * (tb - tm);
        float eAfterBoil = eLiquidAtBoil + m * lv;

        if (energyJ <= eSolidAtMelt) return solid;
        if (energyJ >= eAfterBoil) return gas;
        if (energyJ >= eAfterMelt && energyJ <= eLiquidAtBoil) return liquid;

        if (energyJ < eAfterMelt) {
            if (currentType == solid || currentType == liquid) return currentType;
            float mid = (eSolidAtMelt + eAfterMelt) * 0.5f;
            return (energyJ < mid) ? solid : liquid;
        }

        if (currentType == liquid || currentType == gas) return currentType;
        float mid = (eLiquidAtBoil + eAfterBoil) * 0.5f;
        return (energyJ < mid) ? liquid : gas;
    }

    // -----------------------------
    // Family mapping and constants
    // -----------------------------

    private static float meltPointC(MaterialFamily family) {
        switch (family) {
            case WATER:
                return WATER_MELT_C;
            case SAND:
                return SAND_MELT_C;
            case ROCK:
                return ROCK_MELT_C;
            case AIR:
                return Float.POSITIVE_INFINITY;
            default:
                return Float.POSITIVE_INFINITY;
        }
    }

    private static float boilPointC(MaterialFamily family) {
        switch (family) {
            case WATER:
                return WATER_BOIL_C;
            case SAND:
                return SAND_BOIL_C;
            case ROCK:
                return ROCK_BOIL_C;
            case AIR:
                return Float.POSITIVE_INFINITY;
            default:
                return Float.POSITIVE_INFINITY;
        }
    }

    private static float latentFusionJkg(MaterialFamily family) {
        switch (family) {
            case WATER:
                return WATER_LATENT_FUSION_JKG;
            case SAND:
                return SAND_LATENT_FUSION_JKG;
            case ROCK:
                return ROCK_LATENT_FUSION_JKG;
            case AIR:
                return 0f;
            default:
                return 0f;
        }
    }

    private static float latentVaporJkg(MaterialFamily family) {
        switch (family) {
            case WATER:
                return WATER_LATENT_VAPOR_JKG;
            case SAND:
                return SAND_LATENT_VAPOR_JKG;
            case ROCK:
                return ROCK_LATENT_VAPOR_JKG;
            case AIR:
                return 0f;
            default:
                return 0f;
        }
    }

    private static ElementType solidType(MaterialFamily family) {
        switch (family) {
            case AIR:
                return ElementType.EMPTY;
            case WATER:
                return ElementType.ICE;
            case SAND:
                return ElementType.SAND;
            case ROCK:
                return ElementType.WALL;
            default:
                return ElementType.EMPTY;
        }
    }

    private static ElementType liquidType(MaterialFamily family) {
        switch (family) {
            case AIR:
                return ElementType.EMPTY;
            case WATER:
                return ElementType.WATER;
            case SAND:
                return ElementType.MOLTEN_SILICA;
            case ROCK:
                return ElementType.MOLTEN_ROCK;
            default:
                return ElementType.EMPTY;
        }
    }

    private static ElementType gasType(MaterialFamily family) {
        switch (family) {
            case AIR:
                return ElementType.EMPTY;
            case WATER:
                return ElementType.STEAM;
            case SAND:
                return ElementType.SILICA_VAPOR;
            case ROCK:
                return ElementType.ROCK_VAPOR;
            default:
                return ElementType.EMPTY;
        }
    }
}
