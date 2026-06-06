package org.pepsoft.worldpainter.hytale;

import java.io.Serializable;

/**
 * An exact, user-authored placement of a single Hytale prefab at a specific
 * map location, with explicit (or surface-snapped) height and free yaw
 * rotation. Stored on the {@code Dimension} and realized at export by
 * {@code HytaleWorldExporter}. Immutable; edits produce copies via the
 * {@code with*} helpers.
 *
 * <p>Coordinates are in WorldPainter terms: {@code x} (east-west) and
 * {@code y} (north-south) are horizontal; {@code height} is vertical.</p>
 */
public final class HytalePrefabPlacement implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long id;
    private final String prefabPath;
    private final String prefabName;
    private final int x;
    private final int y;
    private final Integer height;        // explicit vertical; may be null only when snapToSurface
    private final boolean snapToSurface; // if true, height resolved from terrain at export
    private final double rotationDegrees; // 0..360, yaw about the vertical axis

    public HytalePrefabPlacement(long id, String prefabPath, String prefabName,
                                 int x, int y, Integer height, boolean snapToSurface,
                                 double rotationDegrees) {
        this.id = id;
        this.prefabPath = prefabPath;
        this.prefabName = prefabName;
        this.x = x;
        this.y = y;
        this.height = height;
        this.snapToSurface = snapToSurface;
        this.rotationDegrees = normalizeDegrees(rotationDegrees);
    }

    public long getId() { return id; }
    public String getPrefabPath() { return prefabPath; }
    public String getPrefabName() { return prefabName; }
    public int getX() { return x; }
    public int getY() { return y; }
    public Integer getHeight() { return height; }
    public boolean isSnapToSurface() { return snapToSurface; }
    public double getRotationDegrees() { return rotationDegrees; }

    public HytalePrefabPlacement withPosition(int newX, int newY) {
        return new HytalePrefabPlacement(id, prefabPath, prefabName, newX, newY, height, snapToSurface, rotationDegrees);
    }

    public HytalePrefabPlacement withHeight(Integer newHeight, boolean newSnapToSurface) {
        return new HytalePrefabPlacement(id, prefabPath, prefabName, x, y, newHeight, newSnapToSurface, rotationDegrees);
    }

    public HytalePrefabPlacement withRotation(double newDegrees) {
        return new HytalePrefabPlacement(id, prefabPath, prefabName, x, y, height, snapToSurface, newDegrees);
    }

    private static double normalizeDegrees(double d) {
        double r = d % 360.0;
        if (r < 0.0) {
            r += 360.0;
        }
        return r;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (! (o instanceof HytalePrefabPlacement)) {
            return false;
        }
        return id == ((HytalePrefabPlacement) o).id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "HytalePrefabPlacement{id=" + id + ", prefab='" + prefabName + "', x=" + x + ", y=" + y
                + ", height=" + height + ", snap=" + snapToSurface + ", rot=" + rotationDegrees + '}';
    }
}
