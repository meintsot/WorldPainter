package org.pepsoft.worldpainter.hytale.export;

import org.pepsoft.minecraft.Chunk;
import org.pepsoft.minecraft.Material;
import org.pepsoft.minecraft.TileEntity;
import org.pepsoft.minecraft.Entity;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.exporting.MinecraftWorld;
import org.pepsoft.worldpainter.hytale.*;
import org.pepsoft.worldpainter.hytale.chunk.HytaleChunk;
import org.pepsoft.worldpainter.hytale.chunk.HytaleSection;
import org.pepsoft.worldpainter.hytale.prefab.HytalePrefabJsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.pepsoft.minecraft.Constants.MC_LAVA;
import static org.pepsoft.minecraft.Constants.MC_WATER;

final class HytaleRegionMinecraftWorld implements MinecraftWorld {
    HytaleRegionMinecraftWorld(Map<Long, HytaleChunk> chunksByCoords, int blockOffsetX, int blockOffsetZ,
                                       int minHeight, int maxHeight) {
        this(chunksByCoords, blockOffsetX, blockOffsetZ, minHeight, maxHeight, null);
    }

    HytaleRegionMinecraftWorld(Map<Long, HytaleChunk> chunksByCoords, int blockOffsetX, int blockOffsetZ,
                                       int minHeight, int maxHeight, Dimension terrainFallbackDimension) {
        this.chunksByCoords = chunksByCoords;
        this.blockOffsetX = blockOffsetX;
        this.blockOffsetZ = blockOffsetZ;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.terrainFallbackDimension = terrainFallbackDimension;
    }

    @Override
    public boolean fluidsCoexistWithBlocks() {
        // Hytale stores blocks and fluids in separate data layers, so an
        // insubstantial block (plant/decoration) placed in a flooded voxel
        // is not washed away — both coexist. WPObjectUtils.placeBlock uses
        // this to skip the Minecraft-specific "would be washed away" gate
        // that otherwise drops Bo2 plant blocks on flooded columns
        // (TP-53 follow-up).
        return true;
    }

    void setActiveBlockMappings(java.util.Map<String, String> mappings) {
        this.activeBlockMappings = mappings;
    }

    void setPlacedBlockSupportValue(int placedBlockSupportValue) {
        this.placedBlockSupportValue = placedBlockSupportValue;
    }

    void setProtectPlacedBlocksFromFluidSeal(boolean protectPlacedBlocksFromFluidSeal) {
        this.protectPlacedBlocksFromFluidSeal = protectPlacedBlocksFromFluidSeal;
    }

    @Override
    public int getBlockTypeAt(int x, int y, int height) {
        return getMaterialAt(x, y, height).blockType;
    }

    @Override
    public int getDataAt(int x, int y, int height) {
        Material material = getMaterialAt(x, y, height);
        return (material.data >= 0) ? material.data : 0;
    }

    @Override
    public Material getMaterialAt(int x, int y, int height) {
        if ((height < minHeight) || (height >= maxHeight)) {
            return Material.AIR;
        }
        Location location = toLocation(x, y);
        if (location == null) {
            // Chunk is in an adjacent region (not loaded here). When a fallback dimension
            // is supplied (custom-object placement pass), approximate the missing chunk's
            // content as solid up to the dimension's terrain height, AIR above. Without
            // this, substrate checks for objects whose centers lie in a neighbouring
            // region but whose footprints extend into this region would fail and leave
            // visible seams at region boundaries.
            if ((terrainFallbackDimension != null) && (height <= terrainFallbackDimension.getIntHeightAt(x, y))) {
                return Material.STONE;
            }
            return Material.AIR;
        }
        HytaleBlock block = location.chunk.getHytaleBlock(location.localX, height, location.localZ);
        if ((block != null) && (! block.isEmpty())) {
            Material material = Material.get(HytaleBlockRegistry.HYTALE_NAMESPACE + ":" + block.id);
            if (block.rotation != 0) {
                material = material.withProperty(HytalePrefabJsonObject.HYTALE_ROTATION_PROPERTY, Integer.toString(block.rotation & 0x3F));
            }
            return material;
        }

        HytaleSection section = location.chunk.getSections()[height >> 5];
        int localY = height & 31;
        int fluidId = section.getFluidId(location.localX, localY, location.localZ);
        if (fluidId > 0) {
            List<String> fluidPalette = section.getFluidPalette();
            if (fluidId < fluidPalette.size()) {
                String fluidName = fluidPalette.get(fluidId);
                if (fluidName.contains("Lava")) {
                    return Material.LAVA;
                } else if (fluidName.contains("Water")) {
                    return Material.WATER;
                } else if (! fluidName.equals("Empty")) {
                    return Material.get(HytaleBlockRegistry.HYTALE_NAMESPACE + ":" + fluidName);
                }
            }
            return Material.WATER;
        }
        return Material.AIR;
    }

    @Override
    public void setBlockTypeAt(int x, int y, int height, int blockType) {
        setMaterialAt(x, y, height, Material.get(blockType));
    }

    @Override
    public void setDataAt(int x, int y, int height, int data) {
        Material existing = getMaterialAt(x, y, height);
        if (existing.blockType >= 0) {
            setMaterialAt(x, y, height, Material.get(existing.blockType, data));
        }
    }

    @Override
    public void setMaterialAt(int x, int y, int height, Material material) {
        if ((height < minHeight) || (height >= maxHeight)) {
            return;
        }
        Location location = toLocation(x, y);
        if (location == null) {
            return;
        }
        HytaleSection section = location.chunk.getSections()[height >> 5];
        int localY = height & 31;

        if ((material == null) || (material == Material.AIR)) {
            // Don't clear fluid when setting AIR — Hytale has no runtime water flow,
            // so fluid placed by the main pass must survive cave/layer carving.
            // If a cave carves through underwater terrain, the fluid should remain.
            location.chunk.setHytaleBlock(location.localX, height, location.localZ, HytaleBlock.EMPTY);
            return;
        }

        if (material.isNamed(MC_WATER)) {
            location.chunk.setHytaleBlock(location.localX, height, location.localZ, HytaleBlock.EMPTY);
            section.setFluid(location.localX, localY, location.localZ, HytaleBlockMapping.HY_WATER, 1);
            location.chunk.setSupportValue(location.localX, height, location.localZ, HytaleChunk.SUPPORT_NONE);
            location.chunk.setSealProtected(location.localX, height, location.localZ, false);
            return;
        } else if (material.isNamed(MC_LAVA)) {
            location.chunk.setHytaleBlock(location.localX, height, location.localZ, HytaleBlock.EMPTY);
            section.setFluid(location.localX, localY, location.localZ, HytaleBlockMapping.HY_LAVA, 1);
            location.chunk.setSupportValue(location.localX, height, location.localZ, HytaleChunk.SUPPORT_NONE);
            location.chunk.setSealProtected(location.localX, height, location.localZ, false);
            return;
        }

        HytaleBlock block = HytaleBlockMapping.toHytaleBlock(material, activeBlockMappings);
        if (block.isFluid()) {
            location.chunk.setHytaleBlock(location.localX, height, location.localZ, HytaleBlock.EMPTY);
            section.setFluid(location.localX, localY, location.localZ, block.id, 1);
            location.chunk.setSupportValue(location.localX, height, location.localZ, HytaleChunk.SUPPORT_NONE);
            location.chunk.setSealProtected(location.localX, height, location.localZ, false);
        } else {
            section.clearFluid(location.localX, localY, location.localZ);
            location.chunk.setHytaleBlock(location.localX, height, location.localZ, block);
            if (block.isEmpty()) {
                location.chunk.setSupportValue(location.localX, height, location.localZ, HytaleChunk.SUPPORT_NONE);
                location.chunk.setSealProtected(location.localX, height, location.localZ, false);
            } else {
                location.chunk.setSupportValue(location.localX, height, location.localZ, placedBlockSupportValue);
                location.chunk.setSealProtected(location.localX, height, location.localZ, protectPlacedBlocksFromFluidSeal);
            }
        }
    }

    @Override
    public int getMinHeight() {
        return minHeight;
    }

    @Override
    public int getMaxHeight() {
        return maxHeight;
    }

    @Override
    public void addEntity(double x, double y, double height, Entity entity) {
        Location location = toLocation((int) Math.floor(x), (int) Math.floor(y));
        if (location == null) {
            return;
        }
        String entityType = (entity != null) ? entity.getId() : "hytale:prefab_entity";
        HytaleEntity hytaleEntity = HytaleEntity.of(entityType, x + blockOffsetX, height, y + blockOffsetZ);
        if (entity != null) {
            float[] rotation = entity.getRot();
            hytaleEntity.setRotation(rotation[0], rotation[1], 0.0f);
        }
        location.chunk.addHytaleEntity(hytaleEntity);
    }

    @Override
    public void addTileEntity(int x, int y, int height, TileEntity tileEntity) {
        // Hytale has no direct equivalent for Minecraft tile entities in this exporter path.
    }

    @Override
    public int getBlockLightLevel(int x, int y, int height) {
        Location location = toLocation(x, y);
        return (location != null) ? location.chunk.getBlockLightLevel(location.localX, height, location.localZ) : 0;
    }

    @Override
    public void setBlockLightLevel(int x, int y, int height, int blockLightLevel) {
        if ((height < minHeight) || (height >= maxHeight)) {
            return;
        }
        Location location = toLocation(x, y);
        if (location != null) {
            location.chunk.setBlockLightLevel(location.localX, height, location.localZ, blockLightLevel);
        }
    }

    @Override
    public int getSkyLightLevel(int x, int y, int height) {
        Location location = toLocation(x, y);
        return (location != null) ? location.chunk.getSkyLightLevel(location.localX, height, location.localZ) : 15;
    }

    @Override
    public void setSkyLightLevel(int x, int y, int height, int skyLightLevel) {
        if ((height < minHeight) || (height >= maxHeight)) {
            return;
        }
        Location location = toLocation(x, y);
        if (location != null) {
            location.chunk.setSkyLightLevel(location.localX, height, location.localZ, skyLightLevel);
        }
    }

    @Override
    public boolean isChunkPresent(int x, int y) {
        // x and y are MC chunk coords (each 16 WP blocks). Convert to Hytale chunk
        // coords (32 centred blocks per chunk) by going via WP block → centred block
        // → Hytale chunk. The previous implementation skipped the centring offset,
        // which made callers see the wrong Hytale chunk for non-zero offsets.
        int centredBlockX = (x << 4) + blockOffsetX;
        int centredBlockZ = (y << 4) + blockOffsetZ;
        int hChunkX = Math.floorDiv(centredBlockX, HytaleChunk.CHUNK_SIZE);
        int hChunkZ = Math.floorDiv(centredBlockZ, HytaleChunk.CHUNK_SIZE);
        return chunksByCoords.containsKey(chunkKey(hChunkX, hChunkZ));
    }

    @Override
    public void addChunk(Chunk chunk) {
        // Not needed for this in-memory region view.
    }

    @Override
    public int getHighestNonAirBlock(int x, int y) {
        for (int z = maxHeight - 1; z >= minHeight; z--) {
            if (getMaterialAt(x, y, z) != Material.AIR) {
                return z;
            }
        }
        return Integer.MIN_VALUE;
    }

    @Override
    public Chunk getChunk(int x, int z) {
        return chunkViews.computeIfAbsent(chunkKey(x, z), key -> {
            int hChunkX = Math.floorDiv(x, 2);
            int hChunkZ = Math.floorDiv(z, 2);
            HytaleChunk chunk = chunksByCoords.get(chunkKey(hChunkX, hChunkZ));
            if (chunk == null) {
                return null;
            }
            int xOffset = Math.floorMod(x, 2) << 4;
            int zOffset = Math.floorMod(z, 2) << 4;
            return new HytaleChunkView(chunk, x, z, xOffset, zOffset);
        });
    }

    @Override
    public Chunk getChunkForEditing(int x, int z) {
        return getChunk(x, z);
    }

    @Override
    public void close() {
        // No resources to close.
    }

    private Location toLocation(int originalX, int originalZ) {
        int centeredX = originalX + blockOffsetX;
        int centeredZ = originalZ + blockOffsetZ;
        int hChunkX = Math.floorDiv(centeredX, HytaleChunk.CHUNK_SIZE);
        int hChunkZ = Math.floorDiv(centeredZ, HytaleChunk.CHUNK_SIZE);
        HytaleChunk chunk = chunksByCoords.get(chunkKey(hChunkX, hChunkZ));
        if (chunk == null) {
            return null;
        }
        int localX = Math.floorMod(centeredX, HytaleChunk.CHUNK_SIZE);
        int localZ = Math.floorMod(centeredZ, HytaleChunk.CHUNK_SIZE);
        return new Location(chunk, localX, localZ);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private final Map<Long, HytaleChunk> chunksByCoords;
    private final Map<Long, Chunk> chunkViews = new HashMap<>();
    private final int blockOffsetX, blockOffsetZ;
    private final int minHeight, maxHeight;
    // When non-null, getMaterialAt falls back to this dimension's terrain for chunks
    // that live in adjacent regions (i.e. not in chunksByCoords). Used during per-region
    // custom-object placement so neighbouring regions iterating the OBJECT_BORDER_MARGIN
    // overlap strip make the same substrate-check decisions as the originating region,
    // which fixes visible seams where trees crossed region boundaries.
    private final Dimension terrainFallbackDimension;
    private java.util.Map<String, String> activeBlockMappings;
    private int placedBlockSupportValue;
    private boolean protectPlacedBlocksFromFluidSeal;

    private static final class Location {
        private Location(HytaleChunk chunk, int localX, int localZ) {
            this.chunk = chunk;
            this.localX = localX;
            this.localZ = localZ;
        }

        private final HytaleChunk chunk;
        private final int localX, localZ;
    }
}
