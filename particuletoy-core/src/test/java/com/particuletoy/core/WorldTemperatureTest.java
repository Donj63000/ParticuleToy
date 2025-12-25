package com.particuletoy.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldTemperatureTest {

    @Test
    void defaultAmbient_is20C() {
        World w = new World(5, 5, 123);
        w.clear();
        assertEquals(20.0, w.temperatureCAt(2, 2), 0.25);
    }

    @Test
    void conduction_movesTemperaturesTowardEachOther() {
        World w = new World(6, 6, 123);
        w.clear();
        w.fillBorder(ElementType.BEDROCK);

        w.setCell(2, 2, ElementType.WALL);
        w.setCell(3, 2, ElementType.WALL);

        w.setTemperatureC(2, 2, 100f);
        w.setTemperatureC(3, 2, 0f);

        double tLeftBefore = w.temperatureCAt(2, 2);
        double tRightBefore = w.temperatureCAt(3, 2);

        w.step();

        double tLeftAfter = w.temperatureCAt(2, 2);
        double tRightAfter = w.temperatureCAt(3, 2);

        assertTrue(tLeftAfter < tLeftBefore, "Hot cell should cool down");
        assertTrue(tRightAfter > tRightBefore, "Cold cell should warm up");
    }

    @Test
    void water_boilsToSteam_whenHotEnough() {
        World w = new World(6, 6, 123);
        w.clear();
        w.fillBorder(ElementType.BEDROCK);

        w.setCell(2, 2, ElementType.WATER);
        w.setTemperatureC(2, 2, 150f);

        assertEquals(ElementType.STEAM.id(), w.cellIdAt(2, 2));
    }

    @Test
    void water_freezesToIce_whenBelowZero() {
        World w = new World(6, 6, 123);
        w.clear();
        w.fillBorder(ElementType.BEDROCK);

        w.setCell(2, 2, ElementType.WATER);
        w.setTemperatureC(2, 2, -10f);

        assertEquals(ElementType.ICE.id(), w.cellIdAt(2, 2));
    }
}
