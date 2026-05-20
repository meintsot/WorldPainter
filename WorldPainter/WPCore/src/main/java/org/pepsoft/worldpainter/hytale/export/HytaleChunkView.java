package org.pepsoft.worldpainter.hytale.export;

import org.pepsoft.minecraft.Chunk;
import org.pepsoft.minecraft.Entity;
import org.pepsoft.minecraft.Material;
import org.pepsoft.minecraft.MinecraftCoords;
import org.pepsoft.minecraft.TileEntity;
import org.pepsoft.worldpainter.hytale.*;
import org.pepsoft.worldpainter.hytale.chunk.HytaleChunk;
import org.pepsoft.worldpainter.hytale.chunk.HytaleSection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.pepsoft.minecraft.Constants.MC_LAVA;
import static org.pepsoft.minecraft.Constants.MC_WATER;

final class HytaleChunkView implements Chunk {
    HytaleChunkView(HytaleChunk delegate, int mcChunkX, int mcChunkZ, int xOffset, int zOffset) {
        this.delegate = delegate;
        this.mcChunkX = mcChunkX;
        this.mcChunkZ = mcChunkZ;
        this.xOffset = xOffset;
        this.zOffset = zOffset;
    }

    @Override
    public int getBlockLightLevel(int x, int y, int z) {
        return delegate.getBlockLightLevel(x + xOffset, y, z + zOffset);
    }

    @Override
    public void setBlockLightLevel(int x, int y, int z, int blockLightLevel) {
        delegate.setBlockLightLevel(x + xOffset, y, z + zOffset, blockLightLevel);
    }

    @Override
    @Deprecated
    public int getBlockType(int x, int y, int z) {
        return delegate.getBlockType(x + xOffset, y, z + zOffset);
    }

    @Override
    @Deprecated
    public void setBlockType(int x, int y, int z, int blockType) {
        delegate.setBlockType(x + xOffset, y, z + zOffset, blockType);
    }

    @Override
    @Deprecated
    public int getDataValue(int x, int y, int z) {
        return delegate.getDataValue(x + xOffset, y, z + zOffset);
    }

    @Override
    @Deprecated
    public void setDataValue(int x, int y, int z, int dataValue) {
        delegate.setDataValue(x + xOffset, y, z + zOffset, dataValue);
    }

    @Override
    public int getHeight(int x, int z) {
        return delegate.getHeight(x + xOffset, z + zOffset);
    }

    @Override
    public void setHeight(int x, int z, int height) {
        delegate.setHeight(x + xOffset, z + zOffset, height);
    }

    @Override
    public int getSkyLightLevel(int x, int y, int z) {
        return delegate.getSkyLightLevel(x + xOffset, y, z + zOffset);
    }

    @Override
    public void setSkyLightLevel(int x, int y, int z, int skyLightLevel) {
        delegate.setSkyLightLevel(x + xOffset, y, z + zOffset, skyLightLevel);
    }

    @Override
    public int getxPos() {
        return mcChunkX;
    }

    @Override
    public int getzPos() {
        return mcChunkZ;
    }

    @Override
    public MinecraftCoords getCoords() {
        return new MinecraftCoords(mcChunkX, mcChunkZ);
    }

    @Override
    public boolean isTerrainPopulated() {
        return delegate.isTerrainPopulated();
    }

    @Override
    public void setTerrainPopulated(boolean terrainPopulated) {
        delegate.setTerrainPopulated(terrainPopulated);
    }

    @Override
    public Material getMaterial(int x, int y, int z) {
        return delegate.getMaterial(x + xOffset, y, z + zOffset);
    }

    @Override
    public void setMaterial(int x, int y, int z, Material material) {
        int dx = x + xOffset;
        int dz = z + zOffset;
        if (y < delegate.getMinHeight() || y >= delegate.getMaxHeight()) {
            return;
        }
        if ((material == null) || (material == Material.AIR)) {
            delegate.setHytaleBlock(dx, y, dz, HytaleBlock.EMPTY);
            // Don't clear fluid when setting AIR — Hytale has no runtime water flow,
            // so fluid placed by the main pass must survive cave/layer carving.
            // If a cave carves through underwater terrain, the fluid should remain.
            return;
        }
        HytaleBlock block = HytaleBlockMapping.toHytaleBlock(material);
        if (block.isFluid()) {
            delegate.setHytaleBlock(dx, y, dz, HytaleBlock.EMPTY);
            delegate.getSections()[y >> 5].setFluid(dx, y & 31, dz, block.id, 1);
        } else if (material.isNamed(MC_WATER)) {
            delegate.setHytaleBlock(dx, y, dz, HytaleBlock.EMPTY);
            delegate.getSections()[y >> 5].setFluid(dx, y & 31, dz, HytaleBlockMapping.HY_WATER, 1);
        } else if (material.isNamed(MC_LAVA)) {
            delegate.setHytaleBlock(dx, y, dz, HytaleBlock.EMPTY);
            delegate.getSections()[y >> 5].setFluid(dx, y & 31, dz, HytaleBlockMapping.HY_LAVA, 1);
        } else {
            delegate.getSections()[y >> 5].clearFluid(dx, y & 31, dz);
            delegate.setHytaleBlock(dx, y, dz, block);
        }
    }

    @Override
    public List<Entity> getEntities() {
        return delegate.getEntities();
    }

    @Override
    public List<TileEntity> getTileEntities() {
        return delegate.getTileEntities();
    }

    @Override
    public int getMinHeight() {
        return delegate.getMinHeight();
    }

    @Override
    public int getMaxHeight() {
        return delegate.getMaxHeight();
    }

    @Override
    public boolean isReadOnly() {
        return delegate.isReadOnly();
    }

    @Override
    public boolean isLightPopulated() {
        return delegate.isLightPopulated();
    }

    @Override
    public void setLightPopulated(boolean lightPopulated) {
        delegate.setLightPopulated(lightPopulated);
    }

    @Override
    public long getInhabitedTime() {
        return delegate.getInhabitedTime();
    }

    @Override
    public void setInhabitedTime(long inhabitedTime) {
        delegate.setInhabitedTime(inhabitedTime);
    }

    @Override
    public int getHighestNonAirBlock(int x, int z) {
        return delegate.getHighestNonAirBlock(x + xOffset, z + zOffset);
    }

    @Override
    public int getHighestNonAirBlock() {
        int highest = Integer.MIN_VALUE;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                highest = Math.max(highest, getHighestNonAirBlock(x, z));
            }
        }
        return highest;
    }

    private final HytaleChunk delegate;
    private final int mcChunkX, mcChunkZ;
    private final int xOffset, zOffset;
}
