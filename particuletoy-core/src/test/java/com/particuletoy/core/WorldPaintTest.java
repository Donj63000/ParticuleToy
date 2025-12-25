package com.particuletoy.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldPaintTest {

    @Test
    void paintCircle_radius0_setsExactlyOneCell() {
        World w = new World(10, 10, 123);
        w.clear();
        w.paintCircle(4, 7, 0, ElementType.SAND);

        int nonEmpty = 0;
        for (int y = 0; y < w.height(); y++) {
            for (int x = 0; x < w.width(); x++) {
                if (w.cellIdAt(x, y) != ElementType.EMPTY.id()) {
                    nonEmpty++;
                }
            }
        }

        assertEquals(1, nonEmpty);
        assertEquals(ElementType.SAND.id(), w.cellIdAt(4, 7));
    }

    @Test
    void paintCircle_nearBorder_doesNotThrow_andClamps() {
        World w = new World(5, 5, 123);
        w.clear();

        assertDoesNotThrow(() -> w.paintCircle(0, 0, 3, ElementType.WATER));
        // At least the corner should be painted
        assertEquals(ElementType.WATER.id(), w.cellIdAt(0, 0));
    }

    @Test
    void fillBorder_setsAllEdges() {
        World w = new World(6, 4, 123);
        w.clear();
        w.fillBorder(ElementType.WALL);

        for (int x = 0; x < w.width(); x++) {
            assertEquals(ElementType.WALL.id(), w.cellIdAt(x, 0));
            assertEquals(ElementType.WALL.id(), w.cellIdAt(x, w.height() - 1));
        }
        for (int y = 0; y < w.height(); y++) {
            assertEquals(ElementType.WALL.id(), w.cellIdAt(0, y));
            assertEquals(ElementType.WALL.id(), w.cellIdAt(w.width() - 1, y));
        }
    }
}
