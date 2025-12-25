package com.particuletoy.core;

import java.util.Objects;
import java.util.SplittableRandom;

/**
 * A 2D cellular world storing element ids in a flat array (row-major).
 *
 * Coordinates:
 * - (0,0) is top-left
 * - x increases to the right
 * - y increases downward (gravity pulls toward +y)
 *
 * Temperature:
 * - Each cell stores an energy value (Joules).
 * - Temperature is derived via {@link Thermo}.
 * - Heat conduction exchanges energy between neighboring cells each tick.
 */
public final class World {

    // --- Element ids (cached) ---
    private static final byte ID_AIR = ElementType.EMPTY.id();
    private static final byte ID_WALL = ElementType.WALL.id();
    private static final byte ID_SAND = ElementType.SAND.id();
    private static final byte ID_WATER = ElementType.WATER.id();
    private static final byte ID_BEDROCK = ElementType.BEDROCK.id();
    private static final byte ID_ICE = ElementType.ICE.id();
    private static final byte ID_STEAM = ElementType.STEAM.id();
    private static final byte ID_MOLTEN_SILICA = ElementType.MOLTEN_SILICA.id();
    private static final byte ID_SILICA_VAPOR = ElementType.SILICA_VAPOR.id();
    private static final byte ID_MOLTEN_ROCK = ElementType.MOLTEN_ROCK.id();
    private static final byte ID_ROCK_VAPOR = ElementType.ROCK_VAPOR.id();

    // --- Simulation constants ---
    /** World simulation step assumes 60 ticks/s (desktop loop). */
    private static final float DT_SECONDS = 1.0f / 60.0f;

    /** How strongly AIR is coupled to the ambient temperature (1/s). */
    private static final float AIR_TO_AMBIENT_RATE = 2.0f;

    private final int width;
    private final int height;

    // Grid storage
    private final byte[] cells;

    // Thermo storage (per-cell energy)
    private float[] energyJ;
    private float[] energyJBuf;

    // Stamp technique to avoid double-moves within a single tick
    private final int[] movedStamp;
    private int tickId = 1;

    // Determinism: seedable RNG (used only to break symmetry / choose directions)
    private SplittableRandom rng;

    // Ambient temperature (universe default)
    private float ambientTempC = 20.0f;

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
        this.energyJ = new float[cells.length];
        this.energyJBuf = new float[cells.length];
        this.movedStamp = new int[cells.length];
        this.rng = new SplittableRandom(seed);

        clear();
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

    /**
     * Out-of-bounds are treated as BEDROCK (solid) so the simulation is contained.
     */
    public byte cellIdAt(int x, int y) {
        if (!inBounds(x, y)) return ID_BEDROCK;
        return cells[index(x, y)];
    }

    public ElementType cellTypeAt(int x, int y) {
        return ElementType.fromId(cellIdAt(x, y));
    }

    public void setCell(int x, int y, ElementType type) {
        Objects.requireNonNull(type, "type");
        if (!inBounds(x, y)) return;
        int idx = index(x, y);
        cells[idx] = type.id();
        energyJ[idx] = Thermo.energyForTemperature(type, ambientTempC);
    }

    /**
     * Clears the grid to AIR and resets temperatures to the ambient value.
     */
    public void clear() {
        float airEnergy = Thermo.energyForTemperature(ElementType.EMPTY, ambientTempC);
        for (int i = 0; i < cells.length; i++) {
            cells[i] = ID_AIR;
            energyJ[i] = airEnergy;
        }
    }

    /**
     * Fill the world border with the given type (commonly BEDROCK).
     */
    public void fillBorder(ElementType type) {
        Objects.requireNonNull(type, "type");
        byte id = type.id();
        float e = Thermo.energyForTemperature(type, ambientTempC);

        // top and bottom
        for (int x = 0; x < width; x++) {
            int top = index(x, 0);
            int bot = index(x, height - 1);
            cells[top] = id;
            cells[bot] = id;
            energyJ[top] = e;
            energyJ[bot] = e;
        }
        // left and right
        for (int y = 0; y < height; y++) {
            int left = index(0, y);
            int right = index(width - 1, y);
            cells[left] = id;
            cells[right] = id;
            energyJ[left] = e;
            energyJ[right] = e;
        }
    }

    /**
     * Paint a filled circle (brush) in the grid. Out-of-bounds pixels are ignored.
     *
     * Painting an element also sets its temperature to the ambient temperature.
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
        float e = Thermo.energyForTemperature(type, ambientTempC);

        for (int y = minY; y <= maxY; y++) {
            int dy = y - cy;
            int dy2 = dy * dy;
            int rowBase = y * width;
            for (int x = minX; x <= maxX; x++) {
                int dx = x - cx;
                if (dx * dx + dy2 <= r2) {
                    int idx = rowBase + x;
                    cells[idx] = id;
                    energyJ[idx] = e;
                }
            }
        }
    }

    /**
     * Paint temperature in a circle without changing the element type.
     */
    public void paintTemperatureCircle(int cx, int cy, int radius, float tempC) {
        if (radius < 0) return;

        int r = radius;
        int r2 = r * r;

        int minX = MathUtil.clamp(cx - r, 0, width - 1);
        int maxX = MathUtil.clamp(cx + r, 0, width - 1);
        int minY = MathUtil.clamp(cy - r, 0, height - 1);
        int maxY = MathUtil.clamp(cy + r, 0, height - 1);

        for (int y = minY; y <= maxY; y++) {
            int dy = y - cy;
            int dy2 = dy * dy;
            int rowBase = y * width;
            for (int x = minX; x <= maxX; x++) {
                int dx = x - cx;
                if (dx * dx + dy2 <= r2) {
                    setTemperatureC(x, y, tempC);
                }
            }
        }
    }

    public float ambientTemperatureC() {
        return ambientTempC;
    }

    public void setAmbientTemperatureC(float tempC) {
        this.ambientTempC = Thermo.clampTempC(tempC);
    }

    public float temperatureCAt(int x, int y) {
        if (!inBounds(x, y)) return ambientTempC;
        int idx = index(x, y);
        ElementType type = ElementType.fromId(cells[idx]);
        return Thermo.temperatureC(type, energyJ[idx]);
    }

    /**
     * Sets the temperature of a single cell (and updates its phase immediately).
     */
    public void setTemperatureC(int x, int y, float tempC) {
        if (!inBounds(x, y)) return;
        int idx = index(x, y);
        ElementType type = ElementType.fromId(cells[idx]);
        energyJ[idx] = Thermo.energyForTemperature(type, tempC);

        ElementType updated = Thermo.updatePhase(type, energyJ[idx]);
        cells[idx] = updated.id();
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
     * Render a temperature heatmap into an ARGB pixel buffer.
     */
    public void renderTemperatureTo(int[] argbOut) {
        if (argbOut.length < cells.length) {
            throw new IllegalArgumentException("argbOut length must be >= width*height");
        }
        for (int i = 0; i < cells.length; i++) {
            ElementType type = ElementType.fromId(cells[i]);
            float t = Thermo.temperatureC(type, energyJ[i]);
            argbOut[i] = temperatureToColor(t);
        }
    }

    /**
     * Run a single simulation tick.
     *
     * Movement rules:
     * - WALL/BEDROCK: immobile
     * - SAND: falls down through gases and liquids, otherwise diagonals
     * - WATER: falls down through gases, otherwise diagonals, otherwise spreads sideways
     * - STEAM / vapors: rise through AIR, otherwise diagonals, otherwise spreads sideways
     * - ICE: falls in air, but floats upward through liquids
     *
     * Temperature rules:
     * - Conduction between neighbors
     * - AIR tends toward ambient temperature (simple open-world cooling)
     * - Phase changes based on cell energy (latent heat supported)
     */
    public void step() {
        tickId++;
        boolean leftToRight = rng.nextBoolean();

        for (int y = height - 2; y >= 1; y--) {
            if (leftToRight) {
                for (int x = 1; x < width - 1; x++) {
                    updateCell(x, y);
                }
            } else {
                for (int x = width - 2; x >= 1; x--) {
                    updateCell(x, y);
                }
            }
            leftToRight = !leftToRight;
        }

        stepThermo();
    }

    private void updateCell(int x, int y) {
        int idx = index(x, y);
        if (movedStamp[idx] == tickId) return;

        byte id = cells[idx];

        if (id == ID_WALL || id == ID_BEDROCK) {
            return;
        }

        if (id == ID_SAND) {
            updateSand(x, y, idx);
        } else if (id == ID_WATER || id == ID_MOLTEN_ROCK || id == ID_MOLTEN_SILICA) {
            updateLiquid(x, y, idx);
        } else if (id == ID_STEAM || id == ID_SILICA_VAPOR || id == ID_ROCK_VAPOR) {
            updateGas(x, y, idx);
        } else if (id == ID_ICE) {
            updateIce(x, y, idx);
        }
    }

    private boolean isGas(byte id) {
        return id == ID_AIR || id == ID_STEAM || id == ID_SILICA_VAPOR || id == ID_ROCK_VAPOR;
    }

    private boolean isLiquid(byte id) {
        return id == ID_WATER || id == ID_MOLTEN_SILICA || id == ID_MOLTEN_ROCK;
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
        return isGas(target) || isLiquid(target);
    }

    private void updateLiquid(int x, int y, int idx) {
        int aboveIdx = idx - width;
        if (cells[aboveIdx] == ID_SAND) {
            return;
        }

        int belowIdx = idx + width;
        byte below = cells[belowIdx];

        if (isGas(below)) {
            swap(idx, belowIdx);
            return;
        }

        int downLeftIdx = belowIdx - 1;
        int downRightIdx = belowIdx + 1;

        boolean leftFirst = rng.nextBoolean();
        if (leftFirst) {
            if (isGas(cells[downLeftIdx])) {
                swap(idx, downLeftIdx);
                return;
            }
            if (isGas(cells[downRightIdx])) {
                swap(idx, downRightIdx);
                return;
            }
        } else {
            if (isGas(cells[downRightIdx])) {
                swap(idx, downRightIdx);
                return;
            }
            if (isGas(cells[downLeftIdx])) {
                swap(idx, downLeftIdx);
                return;
            }
        }

        int leftIdx = idx - 1;
        int rightIdx = idx + 1;

        boolean goLeftFirst = rng.nextBoolean();
        if (goLeftFirst) {
            if (isGas(cells[leftIdx])) {
                swap(idx, leftIdx);
            } else if (isGas(cells[rightIdx])) {
                swap(idx, rightIdx);
            }
        } else {
            if (isGas(cells[rightIdx])) {
                swap(idx, rightIdx);
            } else if (isGas(cells[leftIdx])) {
                swap(idx, leftIdx);
            }
        }
    }

    private void updateGas(int x, int y, int idx) {
        int aboveIdx = idx - width;
        byte above = cells[aboveIdx];
        if (above == ID_AIR) {
            swap(idx, aboveIdx);
            return;
        }

        int upLeftIdx = aboveIdx - 1;
        int upRightIdx = aboveIdx + 1;

        boolean leftFirst = rng.nextBoolean();
        if (leftFirst) {
            if (cells[upLeftIdx] == ID_AIR) {
                swap(idx, upLeftIdx);
                return;
            }
            if (cells[upRightIdx] == ID_AIR) {
                swap(idx, upRightIdx);
                return;
            }
        } else {
            if (cells[upRightIdx] == ID_AIR) {
                swap(idx, upRightIdx);
                return;
            }
            if (cells[upLeftIdx] == ID_AIR) {
                swap(idx, upLeftIdx);
                return;
            }
        }

        int leftIdx = idx - 1;
        int rightIdx = idx + 1;
        boolean goLeftFirst = rng.nextBoolean();
        if (goLeftFirst) {
            if (cells[leftIdx] == ID_AIR) {
                swap(idx, leftIdx);
            } else if (cells[rightIdx] == ID_AIR) {
                swap(idx, rightIdx);
            }
        } else {
            if (cells[rightIdx] == ID_AIR) {
                swap(idx, rightIdx);
            } else if (cells[leftIdx] == ID_AIR) {
                swap(idx, leftIdx);
            }
        }
    }

    private void updateIce(int x, int y, int idx) {
        int aboveIdx = idx - width;
        byte above = cells[aboveIdx];
        if (isLiquid(above)) {
            float rhoLiquid = ElementType.fromId(above).densityKgM3();
            float rhoIce = ElementType.ICE.densityKgM3();
            if (rhoLiquid > rhoIce) {
                swap(idx, aboveIdx);
                return;
            }
        }

        int belowIdx = idx + width;
        if (isGas(cells[belowIdx])) {
            swap(idx, belowIdx);
            return;
        }

        int downLeftIdx = belowIdx - 1;
        int downRightIdx = belowIdx + 1;
        boolean leftFirst = rng.nextBoolean();
        if (leftFirst) {
            if (isGas(cells[downLeftIdx])) {
                swap(idx, downLeftIdx);
            } else if (isGas(cells[downRightIdx])) {
                swap(idx, downRightIdx);
            }
        } else {
            if (isGas(cells[downRightIdx])) {
                swap(idx, downRightIdx);
            } else if (isGas(cells[downLeftIdx])) {
                swap(idx, downLeftIdx);
            }
        }
    }

    private void swap(int a, int b) {
        byte tmp = cells[a];
        cells[a] = cells[b];
        cells[b] = tmp;

        float e = energyJ[a];
        energyJ[a] = energyJ[b];
        energyJ[b] = e;

        movedStamp[a] = tickId;
        movedStamp[b] = tickId;
    }

    private void stepThermo() {
        System.arraycopy(energyJ, 0, energyJBuf, 0, energyJ.length);

        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width - 1; x++) {
                int a = row + x;
                int b = a + 1;
                conductEdge(a, b);
            }
        }

        for (int y = 0; y < height - 1; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int a = row + x;
                int b = a + width;
                conductEdge(a, b);
            }
        }

        float[] tmp = energyJ;
        energyJ = energyJBuf;
        energyJBuf = tmp;

        float targetAirEnergy = Thermo.energyForTemperature(ElementType.EMPTY, ambientTempC);
        float alpha = MathUtil.clamp(AIR_TO_AMBIENT_RATE * DT_SECONDS, 0f, 1f);
        if (alpha > 0f) {
            for (int i = 0; i < cells.length; i++) {
                if (cells[i] == ID_AIR) {
                    energyJ[i] += (targetAirEnergy - energyJ[i]) * alpha;
                }
            }
        }

        for (int i = 0; i < cells.length; i++) {
            ElementType type = ElementType.fromId(cells[i]);
            if (type.phaseLocked()) continue;
            ElementType updated = Thermo.updatePhase(type, energyJ[i]);
            if (updated != type) {
                cells[i] = updated.id();
            }
        }
    }

    private void conductEdge(int a, int b) {
        ElementType ta = ElementType.fromId(cells[a]);
        ElementType tb = ElementType.fromId(cells[b]);

        float ka = ta.thermalConductivityWMK();
        float kb = tb.thermalConductivityWMK();
        if (ka <= 0f && kb <= 0f) return;

        float denom = ka + kb;
        if (denom <= 0f) return;
        float kEff = 2.0f * ka * kb / denom;
        if (kEff <= 0f) return;

        float tA = Thermo.temperatureC(ta, energyJ[a]);
        float tB = Thermo.temperatureC(tb, energyJ[b]);
        float dT = tB - tA;
        if (dT == 0f) return;

        // Q = k * A/dx * dT * dt (A = dx^2, so A/dx = dx)
        float q = kEff * ThermoConstants.CELL_SIZE_M * dT * DT_SECONDS;

        energyJBuf[a] += q;
        energyJBuf[b] -= q;
    }

    // --- Temperature visualization ---

    private static int temperatureToColor(float tempC) {
        tempC = Thermo.clampTempC(tempC);

        if (tempC <= -273f) return 0xFF000032;
        if (tempC >= 10_000f) return 0xFFFFFFFF;

        return lerpColorByAnchors(tempC);
    }

    private static int lerpColorByAnchors(float t) {
        float[] xs = {-273f, 0f, 100f, 500f, 1000f, 3000f, 10_000f};
        int[] cs = {
                0xFF000032,
                0xFF00A0FF,
                0xFF00FF96,
                0xFFFFFF00,
                0xFFFF5000,
                0xFFFF0000,
                0xFFFFFFFF
        };

        int seg = 0;
        while (seg < xs.length - 2 && t > xs[seg + 1]) {
            seg++;
        }

        float x0 = xs[seg];
        float x1 = xs[seg + 1];
        int c0 = cs[seg];
        int c1 = cs[seg + 1];
        float a = (t - x0) / (x1 - x0);
        a = MathUtil.clamp(a, 0f, 1f);
        return lerpArgb(c0, c1, a);
    }

    private static int lerpArgb(int c0, int c1, float a) {
        int a0 = (c0 >>> 24) & 0xFF;
        int r0 = (c0 >>> 16) & 0xFF;
        int g0 = (c0 >>> 8) & 0xFF;
        int b0 = c0 & 0xFF;

        int a1 = (c1 >>> 24) & 0xFF;
        int r1 = (c1 >>> 16) & 0xFF;
        int g1 = (c1 >>> 8) & 0xFF;
        int b1 = c1 & 0xFF;

        int ao = (int) Math.round(a0 + (a1 - a0) * a);
        int ro = (int) Math.round(r0 + (r1 - r0) * a);
        int go = (int) Math.round(g0 + (g1 - g0) * a);
        int bo = (int) Math.round(b0 + (b1 - b0) * a);

        return (ao << 24) | (ro << 16) | (go << 8) | bo;
    }
}
