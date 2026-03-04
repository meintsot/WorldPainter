package org.pepsoft.worldpainter.hytale;

import org.junit.Test;
import org.pepsoft.worldpainter.Terrain;

import static org.junit.Assert.*;

public class HytaleImportBlockMapperTest {

    @Test
    public void testDirectLookupReturnsKnownTerrain() {
        HytaleImportBlockMapper mapper = new HytaleImportBlockMapper();
        // "Soil_Grass" is in HytaleTerrain.BLOCK_ID_MAP → GRASS
        HytaleTerrain result = mapper.map("Soil_Grass");
        assertNotNull(result);
        assertEquals(HytaleTerrain.GRASS, result);
    }

    @Test
    public void testDirectLookupForStone() {
        HytaleImportBlockMapper mapper = new HytaleImportBlockMapper();
        HytaleTerrain result = mapper.map("Rock_Stone");
        assertNotNull(result);
        assertEquals(HytaleTerrain.STONE, result);
    }

    @Test
    public void testPrefixFallbackForSoil() {
        HytaleImportBlockMapper mapper = new HytaleImportBlockMapper();
        // Hypothetical unknown Soil block should fallback to DIRT
        HytaleTerrain result = mapper.map("Soil_UnknownVariant_99");
        assertNotNull(result);
        assertEquals(HytaleTerrain.DIRT, result);
    }

    @Test
    public void testPrefixFallbackForRock() {
        HytaleImportBlockMapper mapper = new HytaleImportBlockMapper();
        HytaleTerrain result = mapper.map("Rock_UnknownVariant_99");
        assertNotNull(result);
        assertEquals(HytaleTerrain.STONE, result);
    }

    @Test
    public void testUnknownBlockReturnsFallback() {
        HytaleImportBlockMapper mapper = new HytaleImportBlockMapper();
        HytaleTerrain result = mapper.map("Completely_Unknown_Block");
        assertNotNull(result);
        assertEquals(HytaleTerrain.STONE, result); // default fallback
    }

    @Test
    public void testEmptyBlockReturnsNull() {
        HytaleImportBlockMapper mapper = new HytaleImportBlockMapper();
        assertNull(mapper.map("Empty"));
        assertNull(mapper.map(null));
    }

    @Test
    public void testCacheReturnsSameInstance() {
        HytaleImportBlockMapper mapper = new HytaleImportBlockMapper();
        HytaleTerrain first = mapper.map("Soil_Grass");
        HytaleTerrain second = mapper.map("Soil_Grass");
        assertSame(first, second);
    }

    @Test
    public void testToMinecraftTerrainDelegation() {
        HytaleImportBlockMapper mapper = new HytaleImportBlockMapper();
        Terrain mc = mapper.toMcTerrain("Soil_Grass");
        assertNotNull(mc);
        assertEquals(Terrain.GRASS, mc);
    }

    @Test
    public void testUnmappedCountTracked() {
        HytaleImportBlockMapper mapper = new HytaleImportBlockMapper();
        mapper.map("Completely_Unknown_XYZ");
        mapper.map("Completely_Unknown_XYZ");
        mapper.map("Another_Unknown");
        assertEquals(2, mapper.getUnmappedBlockIds().size());
        assertTrue(mapper.getUnmappedBlockIds().contains("Completely_Unknown_XYZ"));
        assertTrue(mapper.getUnmappedBlockIds().contains("Another_Unknown"));
    }
}
