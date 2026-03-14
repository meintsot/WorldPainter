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

        // 2. Prefix-based fallback (reasonable match — not reported as unmapped)
        terrain = prefixFallback(blockId);
        if (terrain != null) {
            return terrain;
        }

        // 3. Default fallback
        unmappedBlockIds.add(blockId);
        return HytaleTerrain.STONE;
    }

    private static HytaleTerrain prefixFallback(String blockId) {
        // Block state variants are prefixed with '*' — strip it before matching
        final String id = blockId.startsWith("*") ? blockId.substring(1) : blockId;

        if (id.startsWith("Soil_"))  return HytaleTerrain.DIRT;
        if (id.startsWith("Rock_"))  return HytaleTerrain.STONE;
        if (id.startsWith("Sand_"))  return HytaleTerrain.SAND;
        if (id.startsWith("Snow_"))  return HytaleTerrain.SNOW;
        if (id.startsWith("Ice_"))   return HytaleTerrain.ICE;
        if (id.startsWith("Ore_"))   return HytaleTerrain.STONE;
        if (id.startsWith("Leaf_") || id.startsWith("Leaves_") || id.startsWith("Plant_")) return HytaleTerrain.GRASS;
        if (id.startsWith("Wood_") || id.startsWith("Log_"))     return HytaleTerrain.GRASS;
        if (id.startsWith("Cloth_"))                              return HytaleTerrain.GRASS;
        if (id.startsWith("Furniture_") || id.startsWith("Deco_") || id.startsWith("Bench_")) return HytaleTerrain.STONE;
        if (id.startsWith("Survival_Trap_"))                      return HytaleTerrain.STONE;
        if (id.startsWith("Rail_"))                               return HytaleTerrain.STONE;
        if (id.startsWith("Ingredient_") || id.startsWith("Container_")) return HytaleTerrain.DIRT;
        if (id.startsWith("Potion_") || id.startsWith("Alchemy_") || id.startsWith("Recipe_Book_")) return HytaleTerrain.STONE;
        if (id.startsWith("Block_"))                              return HytaleTerrain.STONE;
        return null;
    }
}
