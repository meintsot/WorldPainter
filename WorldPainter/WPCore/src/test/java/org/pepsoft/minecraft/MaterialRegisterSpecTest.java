package org.pepsoft.minecraft;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class MaterialRegisterSpecTest {

    @Test
    public void registeredSpecSetsVeryInsubstantial() {
        String name = "test_ns:test_insubstantial_block_" + System.nanoTime();
        Map<String, Object> spec = createSurfaceOnlySpec();
        Material.registerSpec(name, spec);
        Material mat = Material.get(name);
        assertTrue("Material created after registerSpec should be veryInsubstantial",
                mat.veryInsubstantial);
        assertFalse("veryInsubstantial material should not be solid", mat.solid);
    }

    @Test
    public void unregisteredBlockDefaultsToSolid() {
        String name = "test_ns:test_solid_block_" + System.nanoTime();
        Material mat = Material.get(name);
        assertFalse("Material without spec should not be veryInsubstantial",
                mat.veryInsubstantial);
        assertTrue("Material without spec should be solid", mat.solid);
    }

    private static Map<String, Object> createSurfaceOnlySpec() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("opacity", 0);
        spec.put("receivesLight", true);
        spec.put("terrain", false);
        spec.put("insubstantial", true);
        spec.put("veryInsubstantial", true);
        spec.put("resource", false);
        spec.put("tileEntity", false);
        spec.put("treeRelated", false);
        spec.put("vegetation", true);
        spec.put("blockLight", 0);
        spec.put("natural", true);
        spec.put("watery", false);
        return spec;
    }
}
