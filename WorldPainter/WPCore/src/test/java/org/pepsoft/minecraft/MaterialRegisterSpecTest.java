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

    /**
     * Regression: before the fix, {@link Material#registerSpec} only evicted the
     * {@code Identity(name, null)} entry from the cache. Property-bearing variants
     * (e.g. a block that had already been materialised with {@code hytale_rotation=5})
     * kept their stale physical flags. Exercise that path: materialise a variant,
     * register the spec, then re-fetch and verify the variant now reflects the spec.
     */
    @Test
    public void propertyVariantIsRebuiltAfterRegisterSpec() {
        String name = "test_ns:test_rotated_block_" + System.nanoTime();
        Map<String, String> props = new HashMap<>();
        props.put("hytale_rotation", "5");

        Material stale = Material.get(name, props);
        assertFalse("Before registerSpec the variant has default flags", stale.veryInsubstantial);
        assertTrue(stale.solid);

        Material.registerSpec(name, createSurfaceOnlySpec());

        Material fresh = Material.get(name, props);
        assertNotSame("registerSpec must evict the stale property-bearing variant", stale, fresh);
        assertTrue("Re-fetched variant picks up the registered spec", fresh.veryInsubstantial);
        assertFalse(fresh.solid);
        assertEquals("Property is preserved on the rebuilt Material", "5",
                fresh.getProperty("hytale_rotation"));
    }

    /**
     * registerSpec must also evict any cached prototype so getPrototype returns a
     * fresh instance that reflects the newly-registered spec.
     */
    @Test
    public void prototypeIsRebuiltAfterRegisterSpec() {
        String name = "test_ns:test_prototype_block_" + System.nanoTime();

        Material stalePrototype = Material.getPrototype(name);
        assertFalse(stalePrototype.veryInsubstantial);

        Material.registerSpec(name, createSurfaceOnlySpec());

        Material freshPrototype = Material.getPrototype(name);
        assertNotSame("registerSpec must evict the cached prototype", stalePrototype, freshPrototype);
        assertTrue(freshPrototype.veryInsubstantial);
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
