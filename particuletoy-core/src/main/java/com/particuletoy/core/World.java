package com.particuletoy.core;

import java.util.Objects;
import java.util.SplittableRandom;

/**
 * A 2D cellular world storing element ids in a flat array (row-major).
 *
 * Coordinates:
 * - (0,0) is top-left
 * - x increases to the right
 * - y increases downward (gravity naturally pulls toward +y)
 */
public final class World {

    private static final byte ID_EMPTY = ElementType.EMPTY.id();
    private static final byte ID_WALL = ElementType.WALL.id();
    private static final byte ID_SAND = ElementType.SAND.id();
    private static final byte ID_WATER = ElementType.WATER.id();

    private final int width;
    private final int height;

    // Grid storage
    private final byte[] cells;

    // Stamp technique to avoid double-moves within a single tick
    private final int[] movedStamp;
    private int tickId = 1;

    // Determinism: seedable RNG (used only to break symmetry / choose directions)
    private SplittableRandom rng;

    public World(int width, int height) {
        this(width, height, System.nanoTime());
    }

    public World(int width, int height, long seed) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be > 0");
        }
        this.width = width;
        this.height = height;
        this.cells = new byte[width * height];
        this.movedStamp = new int[cells.length];
        this.rng = new SplittableRandom(seed);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public long cellCount() {
        return (long) width * (long) height;
    }

    public void reseed(long seed) {
        this.rng = new SplittableRandom(seed);
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public int index(int x, int y) {
        return x + y * width;
    }

    public byte cellIdAt(int x, int y) {
        if (!inBounds(x, y)) return ID_WALL; // treat OOB as solid
        return cells[index(x, y)];
    }

    public ElementType cellTypeAt(int x, int y) {
        return ElementType.fromId(cellIdAt(x, y));
    }

    public void setCell(int x, int y, ElementType type) {
        Objects.requireNonNull(type, "type");
        if (!inBounds(x, y)) return;
        cells[index(x, y)] = type.id();
    }

    public void clear() {
        for (int i = 0; i < cells.length; i++) {
            cells[i] = ID_EMPTY;
        }
    }

    /**
     * Fill the world border with the given type (commonly WALL).
     */
    public void fillBorder(ElementType type) {
        Objects.requireNonNull(type, "type");
        byte id = type.id();

        // top and bottom
        for (int x = 0; x < width; x++) {
            cells[index(x, 0)] = id;
            cells[index(x, height - 1)] = id;
        }
        // left and right
        for (int y = 0; y < height; y++) {
            cells[index(0, y)] = id;
            cells[index(width - 1, y)] = id;
        }
    }

    /**
     * Paint a filled circle (brush) in the grid. Out-of-bounds pixels are ignored.
     */
    public void paintCircle(int cx, int cy, int radius, ElementType type) {
        Objects.requireNonNull(type, "type");
        if (radius < 0) return;

        int r = radius;
        int r2 = r * r;

        int minX = MathUtil.clamp(cx - r, 0, width - 1);
        int maxX = MathUtil.clamp(cx + r, 0, width - 1);
        int minY = MathUtil.clamp(cy - r, 0, height - 1);
        int maxY = MathUtil.clamp(cy + r, 0, height - 1);

        byte id = type.id();

        for (int y = minY; y <= maxY; y++) {
            int dy = y - cy;
            int dy2 = dy * dy;
            int rowBase = y * width;
            for (int x = minX; x <= maxX; x++) {
                int dx = x - cx;
                if (dx * dx + dy2 <= r2) {
                    cells[rowBase + x] = id;
                }
            }
        }
    }

    /**
     * Render the world into an ARGB pixel buffer.
     * The buffer length must be >= width*height.
     */
    public void renderTo(int[] argbOut) {
        if (argbOut.length < cells.length) {
            throw new IllegalArgumentException("argbOut length must be >= width*height");
        }
        for (int i = 0; i < cells.length; i++) {
            argbOut[i] = ElementType.fromId(cells[i]).argb();
        }
    }

    /**
     * Run a single simulation tick.
     *
     * MVP rules:
     * - WALL: immobile
     * - SAND: falls down if empty/water, otherwise tries diagonals
     * - WATER: falls down if empty, otherwise tries diagonals, otherwise spreads sideways
     */
    public void step() {
        tickId++;
        // Random starting direction each tick; alternate each row to reduce bias
        boolean leftToRight = rng.nextBoolean();

        for (int y = height - 2; y >= 1; y--) { // keep border row intact by default (y>=1)
            if (leftToRight) {
                for (int x = 1; x < width - 1; x++) { // keep border col intact by default
                    updateCell(x, y);
                }
            } else {
                for (int x = width - 2; x >= 1; x--) {
                    updateCell(x, y);
                }
            }
            leftToRight = !leftToRight;
        }
    }

    private void updateCell(int x, int y) {
        int idx = index(x, y);
        if (movedStamp[idx] == tickId) return;

        byte id = cells[idx];
        if (id == ID_SAND) {
            updateSand(x, y, idx);
        } else if (id == ID_WATER) {
            updateWater(x, y, idx);
        }
        // else: EMPTY/WALL => nothing
    }

    private void updateSand(int x, int y, int idx) {
        int belowIdx = idx + width;
        byte below = cells[belowIdx];

        if (canSandMoveInto(below)) {
            swap(idx, belowIdx);
            return;
        }

        int downLeftIdx = belowIdx - 1;
        int downRightIdx = belowIdx + 1;

        boolean leftFirst = rng.nextBoolean();
        if (leftFirst) {
            if (canSandMoveInto(cells[downLeftIdx])) {
                swap(idx, downLeftIdx);
            } else if (canSandMoveInto(cells[downRightIdx])) {
                swap(idx, downRightIdx);
            }
        } else {
            if (canSandMoveInto(cells[downRightIdx])) {
                swap(idx, downRightIdx);
            } else if (canSandMoveInto(cells[downLeftIdx])) {
                swap(idx, downLeftIdx);
            }
        }
    }

    private boolean canSandMoveInto(byte target) {
        return target == ID_EMPTY || target == ID_WATER;
    }

    private void updateWater(int x, int y, int idx) {
        int aboveIdx = idx - width;
        if (cells[aboveIdx] == ID_SAND) {
            return;
        }

        int belowIdx = idx + width;
        byte below = cells[belowIdx];

        if (below == ID_EMPTY) {
            swap(idx, belowIdx);
            return;
        }

        int downLeftIdx = belowIdx - 1;
        int downRightIdx = belowIdx + 1;

        boolean leftFirst = rng.nextBoolean();
        if (leftFirst) {
            if (cells[downLeftIdx] == ID_EMPTY) {
                swap(idx, downLeftIdx);
                return;
            }
            if (cells[downRightIdx] == ID_EMPTY) {
                swap(idx, downRightIdx);
                return;
            }
        } else {
            if (cells[downRightIdx] == ID_EMPTY) {
                swap(idx, downRightIdx);
                return;
            }
            if (cells[downLeftIdx] == ID_EMPTY) {
                swap(idx, downLeftIdx);
                return;
            }
        }

        // Spread sideways (1 cell per tick)
        int leftIdx = idx - 1;
        int rightIdx = idx + 1;

        boolean goLeftFirst = rng.nextBoolean();
        if (goLeftFirst) {
            if (cells[leftIdx] == ID_EMPTY) {
                swap(idx, leftIdx);
            } else if (cells[rightIdx] == ID_EMPTY) {
                swap(idx, rightIdx);
            }
        } else {
            if (cells[rightIdx] == ID_EMPTY) {
                swap(idx, rightIdx);
            } else if (cells[leftIdx] == ID_EMPTY) {
                swap(idx, leftIdx);
            }
        }
    }

    private void swap(int a, int b) {
        byte tmp = cells[a];
        cells[a] = cells[b];
        cells[b] = tmp;

        movedStamp[a] = tickId;
        movedStamp[b] = tickId;
    }
}
