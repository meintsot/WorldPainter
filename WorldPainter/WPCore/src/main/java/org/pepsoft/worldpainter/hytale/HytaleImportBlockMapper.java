package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.Terrain;

import java.util.*;

/**
 * Maps Hytale block IDs (from imported chunks) to {@link HytaleTerrain} instances.
 * <ol>
 *   <li>Direct lookup via {@link HytaleTerrain#getByBlockId(String)}</li>
 *   <li>Prefix-based fallback ({@code Soil_*} → DIRT, {@code Rock_*} → STONE, etc.)</li>
 *   <li>Default fallback → {@link HytaleTerrain#STONE}</li>
 * </ol>
 * Results are cached for speed. Unknown block IDs are tracked for reporting.
 */
public final class HytaleImportBlockMapper {

    private final Map<String, HytaleTerrain> cache = new HashMap<>();
    private final Set<String> unmappedBlockIds = new LinkedHashSet<>();

    /**
     * Map a Hytale block ID to a HytaleTerrain.
     *
     * @param blockId The block ID string (e.g. "Soil_Grass", "Rock_Stone")
     * @return The mapped HytaleTerrain, or {@code null} if the block is empty/air
     */
    public HytaleTerrain map(String blockId) {
        if (blockId == null || blockId.equals("Empty")) {
            return null;
        }
        return cache.computeIfAbsent(blockId, this::resolve);
    }

    /**
     * Map a block ID to its closest Minecraft Terrain (for WP's internal model).
     *
     * @param blockId The block ID string
     * @return The Minecraft Terrain, or {@code null} if empty/air
     */
    public Terrain toMcTerrain(String blockId) {
        HytaleTerrain ht = map(blockId);
        if (ht == null) {
            return null;
        }
        return HytaleTerrainHelper.toMinecraftTerrain(ht);
    }

    /**
     * @return Set of block IDs that could not be directly mapped and used fallback.
     */
    public Set<String> getUnmappedBlockIds() {
        return Collections.unmodifiableSet(unmappedBlockIds);
    }

    private HytaleTerrain resolve(String blockId) {
        // 1. Direct lookup
        HytaleTerrain terrain = HytaleTerrain.getByBlockId(blockId);
        if (terrain != null) {
            return terrain;
        }

        // 2. Prefix-based fallback
        terrain = prefixFallback(blockId);
        if (terrain != null) {
            unmappedBlockIds.add(blockId);
            return terrain;
        }

        // 3. Default fallback
        unmappedBlockIds.add(blockId);
        return HytaleTerrain.STONE;
    }

    private static HytaleTerrain prefixFallback(String blockId) {
        if (blockId.startsWith("Soil_")) return HytaleTerrain.DIRT;
        if (blockId.startsWith("Rock_")) return HytaleTerrain.STONE;
        if (blockId.startsWith("Sand_")) return HytaleTerrain.SAND;
        if (blockId.startsWith("Snow_")) return HytaleTerrain.SNOW;
        if (blockId.startsWith("Ice_"))  return HytaleTerrain.ICE;
        if (blockId.startsWith("Ore_"))  return HytaleTerrain.STONE;
        if (blockId.startsWith("Leaf_") || blockId.startsWith("Leaves_")) return HytaleTerrain.GRASS;
        if (blockId.startsWith("Wood_") || blockId.startsWith("Log_")) return HytaleTerrain.GRASS;
        return null;
    }
}
