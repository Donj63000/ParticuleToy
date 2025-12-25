package com.particuletoy.core;

/**
 * Thermodynamics helper functions.
 *
 * We simulate heat transfer by storing an energy value per cell (Joules).
 * Temperature is derived from energy using a piecewise-linear enthalpy model that includes
 * latent heat plateaus for melting and boiling.
 *
 * Important simplifications (intentional for a cellular sandbox):
 * - Pressure is a local approximation (hydrostatic + ideal gas).
 * - Condensed phases use a fixed per-cell mass; gases can vary by mass.
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
    public static final float WATER_LATENT_FUSION_JKG = 333_550f;
    public static final float WATER_LATENT_VAPOR_JKG = 2_256_000f;

    private static final float WATER_BOIL_REF_C = 100.0f;
    private static final float WATER_BOIL_REF_K = 273.15f + WATER_BOIL_REF_C;
    private static final float WATER_PRESSURE_REF_PA = 101_325.0f;
    private static final float R_WATER_VAPOR = 461.52f;

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

    public static float waterBoilingPointC(float pressurePa) {
        float p = Math.max(1.0f, pressurePa);

        // 1/T = 1/T0 - (R/Lv) ln(P/P0)
        float invT = (1.0f / WATER_BOIL_REF_K)
                - (R_WATER_VAPOR / WATER_LATENT_VAPOR_JKG) * (float) Math.log(p / WATER_PRESSURE_REF_PA);

        if (invT <= 0.0f) {
            return ThermoConstants.MAX_TEMP_C;
        }
        float tbK = 1.0f / invT;
        return clampTempC(tbK - 273.15f);
    }

    public static float idealGasMassKg(ElementType gasType, float pressurePa, float tempC) {
        if (gasType.phase() != Phase.GAS) return 0f;
        float r = gasType.gasConstantJkgK();
        if (r <= 0f) return 0f;

        float tk = Math.max(1f, tempC + 273.15f);
        float p = Math.max(1f, pressurePa);
        return (p * ThermoConstants.CELL_VOLUME_M3) / (r * tk);
    }

    public static float idealGasPressurePa(ElementType gasType, float massKg, float tempC) {
        if (gasType.phase() != Phase.GAS) return 0f;
        float r = gasType.gasConstantJkgK();
        if (r <= 0f || massKg <= 0f) return 0f;

        float tk = Math.max(1f, tempC + 273.15f);
        return (massKg * r * tk) / ThermoConstants.CELL_VOLUME_M3;
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
     */
    public static float energyForTemperature(ElementType currentType, float tempC) {
        float massKg = massKgPerCell(currentType.family());
        return energyForTemperature(currentType, tempC, massKg, ThermoConstants.DEFAULT_AMBIENT_PRESSURE_PA);
    }

    /**
     * Convert temperature to energy for the current element using mass and pressure.
     */
    public static float energyForTemperature(ElementType currentType, float tempC, float massKg, float pressurePa) {
        tempC = clampTempC(tempC);
        if (massKg <= 0f) return 0f;

        MaterialFamily family = currentType.family();

        if (family == MaterialFamily.AIR) {
            return massKg * ElementType.EMPTY.specificHeatJKgK() * tempC;
        }

        float m = massKg;

        ElementType solid = solidType(family);
        ElementType liquid = liquidType(family);
        ElementType gas = gasType(family);

        float cpSolid = solid.specificHeatJKgK();
        float cpLiquid = liquid.specificHeatJKgK();
        float cpGas = gas.specificHeatJKgK();

        float tm = meltPointC(family);
        float tb = (family == MaterialFamily.WATER) ? waterBoilingPointC(pressurePa) : boilPointC(family);

        float lf = latentFusionJkg(family);
        float lv = latentVaporJkg(family);

        float eSolidAtMelt = m * cpSolid * tm;
        float eAfterMelt = eSolidAtMelt + m * lf;
        float eLiquidAtBoil = eAfterMelt + m * cpLiquid * (tb - tm);
        float eAfterBoil = eLiquidAtBoil + m * lv;

        if (tempC < tm) {
            return m * cpSolid * tempC;
        }
        if (tempC < tb) {
            return eAfterMelt + m * cpLiquid * (tempC - tm);
        }
        return eAfterBoil + m * cpGas * (tempC - tb);
    }

    /**
     * Convert energy to temperature for a given element.
     */
    public static float temperatureC(ElementType currentType, float energyJ) {
        float massKg = massKgPerCell(currentType.family());
        return temperatureC(currentType, energyJ, massKg, ThermoConstants.DEFAULT_AMBIENT_PRESSURE_PA);
    }

    /**
     * Convert energy to temperature for a given element using mass and pressure.
     */
    public static float temperatureC(ElementType currentType, float energyJ, float massKg, float pressurePa) {
        if (massKg <= 0f) return ThermoConstants.DEFAULT_AMBIENT_TEMP_C;

        MaterialFamily family = currentType.family();

        if (family == MaterialFamily.AIR) {
            float denom = massKg * ElementType.EMPTY.specificHeatJKgK();
            if (denom == 0f) return 0f;
            return clampTempC(energyJ / denom);
        }

        float m = massKg;

        ElementType solid = solidType(family);
        ElementType liquid = liquidType(family);
        ElementType gas = gasType(family);

        float cpSolid = solid.specificHeatJKgK();
        float cpLiquid = liquid.specificHeatJKgK();
        float cpGas = gas.specificHeatJKgK();

        float tm = meltPointC(family);
        float tb = (family == MaterialFamily.WATER) ? waterBoilingPointC(pressurePa) : boilPointC(family);

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
     */
    public static ElementType updatePhase(ElementType currentType, float energyJ) {
        float massKg = massKgPerCell(currentType.family());
        return updatePhase(currentType, energyJ, massKg, ThermoConstants.DEFAULT_AMBIENT_PRESSURE_PA);
    }

    /**
     * Update element phase according to family thresholds and current energy (with pressure).
     */
    public static ElementType updatePhase(ElementType currentType, float energyJ, float massKg, float pressurePa) {
        if (currentType.phaseLocked()) return currentType;
        if (massKg <= 0f) return ElementType.EMPTY;

        MaterialFamily family = currentType.family();
        if (family == MaterialFamily.AIR) return ElementType.EMPTY;

        float m = massKg;

        ElementType solid = solidType(family);
        ElementType liquid = liquidType(family);
        ElementType gas = gasType(family);

        float cpSolid = solid.specificHeatJKgK();
        float cpLiquid = liquid.specificHeatJKgK();

        float tm = meltPointC(family);
        float tb = (family == MaterialFamily.WATER) ? waterBoilingPointC(pressurePa) : boilPointC(family);

        float lf = latentFusionJkg(family);
        float lv = latentVaporJkg(family);

        float eSolidAtMelt = m * cpSolid * tm;
        float eAfterMelt = eSolidAtMelt + m * lf;
        float eLiquidAtBoil = eAfterMelt + m * cpLiquid * (tb - tm);
        float eAfterBoil = eLiquidAtBoil + m * lv;

        if (family == MaterialFamily.WATER) {
            if (energyJ < eSolidAtMelt) return ElementType.ICE;
            if (energyJ < eAfterMelt) return ElementType.SLUSH;
            if (energyJ < eLiquidAtBoil) return ElementType.WATER;
            if (energyJ < eAfterBoil) return ElementType.BOILING_WATER;
            return ElementType.STEAM;
        }

        if (energyJ <= eSolidAtMelt) return solid;
        if (energyJ >= eAfterBoil) return gas;
        return liquid;
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
