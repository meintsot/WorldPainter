package org.pepsoft.worldpainter.hytale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.pepsoft.minecraft.Entity;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.objects.GenericObject;
import org.pepsoft.worldpainter.objects.WPObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.vecmath.Point3i;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loader for Hytale prefab JSON files ({@code *.prefab.json}) that converts them to a {@link WPObject}.
 */
public final class HytalePrefabJsonObject {
    private HytalePrefabJsonObject() {
        // Utility class
    }

    public static WPObject load(File file) throws IOException {
        HytaleBlockRegistry.ensureMaterialsRegistered();
        final JsonObject root = readRoot(file);
        final JsonArray blocks = getArray(root, "blocks");
        final JsonArray fluids = getArray(root, "fluids");

        final Bounds bounds = new Bounds();
        includeBounds(blocks, bounds);
        includeBounds(fluids, bounds);
        if (! bounds.hasBlocks()) {
            bounds.include(0, 0, 0);
        }

        final int dimX = bounds.maxX - bounds.minX + 1;
        final int dimY = bounds.maxZ - bounds.minZ + 1;
        final int dimZ = bounds.maxY - bounds.minY + 1;

        final Material[] data = new Material[dimX * dimY * dimZ];
        placeBlocks(fluids, data, bounds, dimX, dimY, dimZ, false);
        placeBlocks(blocks, data, bounds, dimX, dimY, dimZ, true);

        final List<Entity> entities = parseEntities(root, bounds);
        final Map<String, Serializable> attributes = new HashMap<>();
        attributes.put(WPObject.ATTRIBUTE_FILE.key, file);

        final Point3i offset = calculateOffset(root, bounds);
        if ((offset.x != 0) || (offset.y != 0) || (offset.z != 0)) {
            attributes.put(WPObject.ATTRIBUTE_OFFSET.key, offset);
        }

        return new GenericObject(deriveName(file.getName()), dimX, dimY, dimZ, data, entities, null, attributes);
    }

    private static JsonObject readRoot(File file) throws IOException {
        try (Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            JsonElement element = JsonParser.parseReader(reader);
            if ((element == null) || (! element.isJsonObject())) {
                throw new IOException("Invalid Hytale prefab JSON: root must be an object");
            }
            return element.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new IOException("Could not parse Hytale prefab JSON file " + file.getAbsolutePath(), e);
        }
    }

    private static void includeBounds(JsonArray entries, Bounds bounds) {
        if (entries == null) {
            return;
        }
        for (JsonElement element: entries) {
            if (! element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            Integer x = getInt(entry, "x");
            Integer y = getInt(entry, "y");
            Integer z = getInt(entry, "z");
            if ((x != null) && (y != null) && (z != null)) {
                bounds.include(x, y, z);
            }
        }
    }

    private static void placeBlocks(JsonArray entries, Material[] data, Bounds bounds, int dimX, int dimY, int dimZ, boolean override) {
        if (entries == null) {
            return;
        }
        for (JsonElement element: entries) {
            if (! element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            Integer x = getInt(entry, "x");
            Integer y = getInt(entry, "y");
            Integer z = getInt(entry, "z");
            String blockName = getString(entry, "name");
            if ((x == null) || (y == null) || (z == null) || (blockName == null)) {
                continue;
            }
            Material material = toMaterial(blockName);
            if (material == null) {
                continue;
            }
            Integer rotation = getInt(entry, "rotation");
            if (rotation != null) {
                material = material.withProperty(HYTALE_ROTATION_PROPERTY, Integer.toString(rotation));
            }
            int objectX = x - bounds.minX;
            int objectY = z - bounds.minZ;
            int objectZ = y - bounds.minY;
            if ((objectX < 0) || (objectX >= dimX) || (objectY < 0) || (objectY >= dimY) || (objectZ < 0) || (objectZ >= dimZ)) {
                logger.warn("Ignoring out-of-range prefab block at {},{},{} in entry {}", x, y, z, entry);
                continue;
            }
            int index = objectX + objectY * dimX + objectZ * dimX * dimY;
            if (override || (data[index] == null)) {
                data[index] = material;
            }
        }
    }

    private static List<Entity> parseEntities(JsonObject root, Bounds bounds) {
        JsonArray entitiesArray = getArray(root, "entities");
        if ((entitiesArray == null) || (entitiesArray.size() == 0)) {
            return null;
        }
        List<Entity> entities = new ArrayList<>(entitiesArray.size());
        for (JsonElement element: entitiesArray) {
            if (! element.isJsonObject()) {
                continue;
            }
            JsonObject entityObject = element.getAsJsonObject();
            double[] position = getEntityPosition(entityObject);
            if (position == null) {
                continue;
            }
            Entity entity = new Entity(getEntityId(entityObject));
            entity.setRelPos(new double[] {
                    position[0] - bounds.minX,
                    position[1] - bounds.minY,
                    position[2] - bounds.minZ
            });

            float[] rotation = getEntityRotation(entityObject);
            if (rotation != null) {
                entity.setRot(rotation);
            }

            entities.add(entity);
        }
        return entities.isEmpty() ? null : entities;
    }

    private static double[] getEntityPosition(JsonObject entityObject) {
        JsonObject position = getObject(entityObject, "Position");
        if (position != null) {
            return readVector(position);
        }

        JsonObject components = getObject(entityObject, "Components");
        if (components != null) {
            JsonObject transform = getObject(components, "Transform");
            if (transform != null) {
                JsonObject transformPosition = getObject(transform, "Position");
                if (transformPosition != null) {
                    return readVector(transformPosition);
                }
            }
        }
        return null;
    }

    private static float[] getEntityRotation(JsonObject entityObject) {
        JsonObject components = getObject(entityObject, "Components");
        if (components == null) {
            return null;
        }
        JsonObject transform = getObject(components, "Transform");
        if (transform == null) {
            return null;
        }
        JsonObject rotation = getObject(transform, "Rotation");
        if (rotation == null) {
            return null;
        }
        Double yaw = getDouble(rotation, "Yaw");
        Double pitch = getDouble(rotation, "Pitch");
        if ((yaw == null) && (pitch == null)) {
            return null;
        }
        return new float[] {
                (yaw != null) ? yaw.floatValue() : 0.0f,
                (pitch != null) ? pitch.floatValue() : 0.0f
        };
    }

    private static String getEntityId(JsonObject entityObject) {
        String entityType = getString(entityObject, "EntityType");
        if (entityType != null) {
            return normaliseEntityId(entityType);
        }

        JsonObject components = getObject(entityObject, "Components");
        if (components != null) {
            JsonObject spawnMarkerComponent = getObject(components, "SpawnMarkerComponent");
            if (spawnMarkerComponent != null) {
                String spawnMarker = getString(spawnMarkerComponent, "SpawnMarker");
                if (spawnMarker != null) {
                    return normaliseEntityId(spawnMarker);
                }
            }

            JsonObject nameplate = getObject(components, "Nameplate");
            if (nameplate != null) {
                String text = getString(nameplate, "Text");
                if (text != null) {
                    return normaliseEntityId(text);
                }
            }
        }
        return DEFAULT_ENTITY_ID;
    }

    private static String normaliseEntityId(String id) {
        String sanitised = id.trim().replaceAll("[^A-Za-z0-9_:/.-]", "_");
        if (sanitised.isEmpty()) {
            return DEFAULT_ENTITY_ID;
        }
        if (sanitised.indexOf(':') == -1) {
            sanitised = HytaleBlockRegistry.HYTALE_NAMESPACE + ":" + sanitised;
        }
        return sanitised;
    }

    private static double[] readVector(JsonObject vectorObject) {
        Double x = getDouble(vectorObject, "X");
        Double y = getDouble(vectorObject, "Y");
        Double z = getDouble(vectorObject, "Z");
        if ((x == null) || (y == null) || (z == null)) {
            return null;
        }
        return new double[] {x, y, z};
    }

    private static Material toMaterial(String blockName) {
        blockName = blockName.trim();
        if (blockName.isEmpty() || blockName.equals(HytaleBlockMapping.HY_AIR) || blockName.equals("Editor_Empty")) {
            return null;
        }
        return Material.get(HytaleBlockRegistry.HYTALE_NAMESPACE + ':' + blockName);
    }

    private static Point3i calculateOffset(JsonObject root, Bounds bounds) {
        int anchorX = getInt(root, "anchorX", bounds.minX);
        int anchorY = getInt(root, "anchorY", bounds.minY);
        int anchorZ = getInt(root, "anchorZ", bounds.minZ);
        return new Point3i(
                -(anchorX - bounds.minX),
                -(anchorZ - bounds.minZ),
                -(anchorY - bounds.minY));
    }

    private static String deriveName(String fileName) {
        String lowerCaseName = fileName.toLowerCase(Locale.ROOT);
        if (lowerCaseName.endsWith(PREFAB_JSON_EXTENSION)) {
            return fileName.substring(0, fileName.length() - PREFAB_JSON_EXTENSION.length());
        }
        int extensionIndex = fileName.lastIndexOf('.');
        return (extensionIndex != -1) ? fileName.substring(0, extensionIndex) : fileName;
    }

    private static JsonArray getArray(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return ((element != null) && element.isJsonArray()) ? element.getAsJsonArray() : null;
    }

    private static JsonObject getObject(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return ((element != null) && element.isJsonObject()) ? element.getAsJsonObject() : null;
    }

    private static Integer getInt(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if ((element == null) || (! element.isJsonPrimitive())) {
            return null;
        }
        try {
            return element.getAsInt();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int getInt(JsonObject object, String key, int defaultValue) {
        Integer value = getInt(object, key);
        return (value != null) ? value : defaultValue;
    }

    private static Double getDouble(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if ((element == null) || (! element.isJsonPrimitive())) {
            return null;
        }
        try {
            return element.getAsDouble();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String getString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if ((element == null) || (! element.isJsonPrimitive())) {
            return null;
        }
        try {
            return element.getAsString();
        } catch (UnsupportedOperationException e) {
            return null;
        }
    }

    private static final class Bounds {
        void include(int x, int y, int z) {
            hasBlocks = true;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        boolean hasBlocks() {
            return hasBlocks;
        }

        private boolean hasBlocks;
        private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
    }

    private static final String PREFAB_JSON_EXTENSION = ".prefab.json";
    private static final String DEFAULT_ENTITY_ID = HytaleBlockRegistry.HYTALE_NAMESPACE + ":prefab_entity";
    static final String HYTALE_ROTATION_PROPERTY = "hytale_rotation";

    private static final Logger logger = LoggerFactory.getLogger(HytalePrefabJsonObject.class);
}
