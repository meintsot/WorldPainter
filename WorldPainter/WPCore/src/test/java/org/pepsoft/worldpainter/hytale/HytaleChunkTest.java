package org.pepsoft.worldpainter.hytale;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.RawBsonDocument;
import org.junit.Test;
import org.pepsoft.minecraft.Material;

import static org.junit.Assert.*;

/**
 * Unit tests for Hytale export support.
 */
public class HytaleChunkTest {
    
    @Test
    public void testChunkCreation() {
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);
        
        assertEquals(0, chunk.getxPos());
        assertEquals(0, chunk.getzPos());
        assertEquals(0, chunk.getMinHeight());
        assertEquals(320, chunk.getMaxHeight());
    }
    
    @Test
    public void testChunkDimensions() {
        assertEquals(32, HytaleChunk.CHUNK_SIZE);
        assertEquals(32, HytaleChunk.SECTION_HEIGHT);
        assertEquals(10, HytaleChunk.SECTION_COUNT);
        assertEquals(320, HytaleChunk.MAX_HEIGHT);
    }
    
    @Test
    public void testSetAndGetMaterial() {
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);
        
        // Default should be air
        assertEquals(Material.AIR, chunk.getMaterial(0, 0, 0));
        
        // Set a block
        chunk.setMaterial(5, 10, 15, Material.STONE);
        assertEquals(Material.STONE, chunk.getMaterial(5, 10, 15));
        
        // Set at section boundary
        chunk.setMaterial(0, 31, 0, Material.DIRT);
        assertEquals(Material.DIRT, chunk.getMaterial(0, 31, 0));
        
        // Set in second section
        chunk.setMaterial(0, 32, 0, Material.GRASS_BLOCK);
        assertEquals(Material.GRASS_BLOCK, chunk.getMaterial(0, 32, 0));
    }
    
    @Test
    public void testHeightmap() {
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);
        
        // Set some blocks and verify heightmap updates
        chunk.setMaterial(0, 0, 0, Material.STONE);
        chunk.setMaterial(0, 5, 0, Material.STONE);
        chunk.setMaterial(0, 10, 0, Material.STONE);
        
        assertEquals(10, chunk.getHeight(0, 0));
        
        // Set at different coordinates
        chunk.setMaterial(15, 50, 20, Material.STONE);
        assertEquals(50, chunk.getHeight(15, 20));
    }
    
    @Test
    public void testHighestNonAirBlock() {
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);
        
        // Empty chunk
        assertEquals(Integer.MIN_VALUE, chunk.getHighestNonAirBlock(0, 0));
        
        // Add some blocks
        chunk.setMaterial(0, 10, 0, Material.STONE);
        assertEquals(10, chunk.getHighestNonAirBlock(0, 0));
        
        chunk.setMaterial(0, 100, 0, Material.DIRT);
        assertEquals(100, chunk.getHighestNonAirBlock(0, 0));
    }
    
    @Test
    public void testBlockIdMapping() {
        // Test basic mappings - using real Hytale block IDs
        assertEquals("Empty", HytaleBlockMapping.toHytale(Material.AIR));
        assertEquals("Rock_Stone", HytaleBlockMapping.toHytale(Material.STONE));
        assertEquals("Soil_Dirt", HytaleBlockMapping.toHytale(Material.DIRT));
        assertEquals("Soil_Grass", HytaleBlockMapping.toHytale(Material.GRASS_BLOCK));
        assertEquals("Empty", HytaleBlockMapping.toHytale(Material.WATER));
        assertEquals("Rock_Bedrock", HytaleBlockMapping.toHytale(Material.BEDROCK));
    }
    
    @Test
    public void testNumericIdMapping() {
        assertEquals(0, HytaleBlockMapping.toHytaleNumericId(HytaleBlockMapping.HY_AIR));
        assertEquals(1, HytaleBlockMapping.toHytaleNumericId(HytaleBlockMapping.HY_STONE));
        assertEquals(3, HytaleBlockMapping.toHytaleNumericId(HytaleBlockMapping.HY_DIRT));
        assertEquals(2, HytaleBlockMapping.toHytaleNumericId(HytaleBlockMapping.HY_GRASS_BLOCK));
    }
    
    @Test
    public void testSectionIndexing() {
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);
        
        // Test Hytale's indexing formula: (y & 31) << 10 | (z & 31) << 5 | (x & 31)
        // Set blocks at various positions and verify they're stored correctly
        
        // Origin of section
        chunk.setMaterial(0, 0, 0, Material.STONE);
        assertEquals(Material.STONE, chunk.getMaterial(0, 0, 0));
        
        // End of section
        chunk.setMaterial(31, 31, 31, Material.DIRT);
        assertEquals(Material.DIRT, chunk.getMaterial(31, 31, 31));
        
        // Multiple sections
        for (int section = 0; section < 10; section++) {
            int y = section * 32 + 15;
            Material mat = (section % 2 == 0) ? Material.STONE : Material.DIRT;
            chunk.setMaterial(10, y, 10, mat);
            assertEquals(mat, chunk.getMaterial(10, y, 10));
        }
    }
    
    @Test
    public void testLightLevels() {
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);
        
        // Set block light
        chunk.setBlockLightLevel(5, 10, 5, 15);
        assertEquals(15, chunk.getBlockLightLevel(5, 10, 5));
        
        // Sky light should default to 15
        assertEquals(15, chunk.getSkyLightLevel(5, 10, 5));
        
        // Set sky light
        chunk.setSkyLightLevel(5, 10, 5, 8);
        assertEquals(8, chunk.getSkyLightLevel(5, 10, 5));
    }
    
    @Test
    public void testRegionFileName() {
        assertEquals("0.0.region.bin", HytaleRegionFile.getRegionFileName(0, 0));
        assertEquals("1.2.region.bin", HytaleRegionFile.getRegionFileName(1, 2));
        assertEquals("-1.-1.region.bin", HytaleRegionFile.getRegionFileName(-1, -1));
    }

    @Test
    public void testDefaultTintIsArgbGreen() {
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);
        assertEquals(0xFF7CFC00, chunk.getTint(0, 0));
    }

    @Test
    public void testMissingWaterSourceLevelDefaultsToOne() {
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);
        HytaleChunk.HytaleSection section = chunk.getSections()[0];

        section.setFluid(0, 0, 0, HytaleBlockMapping.HY_WATER, 1);
        section.getFluidLevels()[0] = 0;

        byte[] fluidData = getSerializedFluidData(chunk, 0);
        assertNotNull(fluidData);
        assertEquals(1, readFluidLevel(fluidData, 0));
    }

    @Test
    public void testMissingFlowingWaterLevelDefaultsToEight() {
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);
        HytaleChunk.HytaleSection section = chunk.getSections()[0];

        section.setFluid(1, 0, 0, "Water", 8);
        section.getFluidLevels()[1] = 0;

        byte[] fluidData = getSerializedFluidData(chunk, 0);
        assertNotNull(fluidData);
        assertEquals(8, readFluidLevel(fluidData, 1));
    }

    private static byte[] getSerializedFluidData(HytaleChunk chunk, int sectionIndex) {
        BsonDocument root = new RawBsonDocument(HytaleBsonChunkSerializer.serializeChunk(chunk));
        BsonDocument components = root.getDocument("Components");
        BsonDocument chunkColumn = components.getDocument("ChunkColumn");
        BsonArray sections = chunkColumn.getArray("Sections");
        BsonDocument sectionHolder = sections.get(sectionIndex).asDocument();
        BsonDocument sectionComponents = sectionHolder.getDocument("Components");
        BsonDocument fluidDoc = sectionComponents.getDocument("Fluid");
        return fluidDoc.getBinary("Data").getData();
    }

    private static int readFluidLevel(byte[] fluidData, int index) {
        int p = 0;
        int paletteType = fluidData[p++] & 0xFF;
        if (paletteType == 0) {
            return 0;
        }

        int entryCount = ((fluidData[p] & 0xFF) << 8) | (fluidData[p + 1] & 0xFF);
        p += 2;
        for (int i = 0; i < entryCount; i++) {
            p += 1;
            int utfLength = ((fluidData[p] & 0xFF) << 8) | (fluidData[p + 1] & 0xFF);
            p += 2;
            p += utfLength;
            p += 2;
        }

        int indexDataLength;
        switch (paletteType) {
            case 1:
                indexDataLength = 16384;
                break;
            case 2:
                indexDataLength = 32768;
                break;
            case 3:
                indexDataLength = 65536;
                break;
            default:
                fail("Unexpected fluid palette type: " + paletteType);
                return 0;
        }
        p += indexDataLength;

        boolean hasLevels = fluidData[p++] != 0;
        if (!hasLevels) {
            return 0;
        }

        int byteIndex = index >> 1;
        int levelByte = fluidData[p + byteIndex] & 0xFF;
        if ((index & 1) == 0) {
            return levelByte & 0xF;
        }
        return (levelByte >> 4) & 0xF;
    }
}
