package com.particuletoy.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldStepTest {

    @Test
    void sandFallsDownIntoEmpty() {
        World w = new World(5, 5, 42);
        w.clear();
        w.fillBorder(ElementType.WALL);

        w.setCell(2, 1, ElementType.SAND);
        w.step();

        assertEquals(ElementType.EMPTY.id(), w.cellIdAt(2, 1));
        assertEquals(ElementType.SAND.id(), w.cellIdAt(2, 2));
    }

    @Test
    void waterFallsDownIntoEmpty() {
        World w = new World(5, 5, 42);
        w.clear();
        w.fillBorder(ElementType.WALL);

        w.setCell(2, 1, ElementType.WATER);
        w.step();

        assertEquals(ElementType.EMPTY.id(), w.cellIdAt(2, 1));
        assertEquals(ElementType.WATER.id(), w.cellIdAt(2, 2));
    }

    @Test
    void sandSwapsWithWater_becauseSandSinks() {
        World w = new World(5, 5, 42);
        w.clear();
        w.fillBorder(ElementType.WALL);

        w.setCell(2, 1, ElementType.SAND);
        w.setCell(2, 2, ElementType.WATER);

        w.step();

        assertEquals(ElementType.WATER.id(), w.cellIdAt(2, 1));
        assertEquals(ElementType.SAND.id(), w.cellIdAt(2, 2));
    }
}
