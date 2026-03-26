package org.pepsoft.worldpainter.hytale;

import org.junit.BeforeClass;
import org.junit.Test;
import org.pepsoft.minecraft.Material;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Verifies that after {@link HytaleBlockRegistry#ensureMaterialsRegistered()},
 * surface-only blocks produce Materials with {@code veryInsubstantial = true}
 * so that custom object layers can place objects on top of them.
 */
public class HytaleBlockRegistryMaterialTest {

    @BeforeClass
    public static void init() {
        HytaleBlockRegistry.initialize(null);
        HytaleBlockRegistry.ensureMaterialsRegistered();
    }

    @Test
    public void surfaceOnlyBlocksAreVeryInsubstantial() {
        for (HytaleBlockRegistry.Category cat : HytaleBlockRegistry.getCategoryValues()) {
            if (!cat.isSurfaceOnly()) {
                continue;
            }
            List<String> blocks = HytaleBlockRegistry.getBlockNames(cat);
            if (blocks.isEmpty()) {
                continue;
            }
            for (String blockName : blocks) {
                Material mat = Material.get(HytaleBlockRegistry.HYTALE_NAMESPACE + ":" + blockName);
                assertTrue("Surface-only block " + blockName + " (category " + cat
                                + ") should be veryInsubstantial",
                        mat.veryInsubstantial);
                assertFalse("Surface-only block " + blockName + " should not be solid",
                        mat.solid);
            }
        }
    }

    @Test
    public void solidBlocksAreNotVeryInsubstantial() {
        List<String> rockBlocks = HytaleBlockRegistry.getBlockNames(
                HytaleBlockRegistry.Category.ROCK);
        if (!rockBlocks.isEmpty()) {
            String blockName = rockBlocks.get(0);
            Material mat = Material.get(HytaleBlockRegistry.HYTALE_NAMESPACE + ":" + blockName);
            assertFalse("Rock block " + blockName + " should NOT be veryInsubstantial",
                    mat.veryInsubstantial);
        }
    }
}
