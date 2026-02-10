package org.pepsoft.worldpainter.hytale;

import org.junit.Test;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.objects.WPObject;

import javax.vecmath.Point3i;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.Assert.*;

public class HytalePrefabJsonObjectTest {
    @Test
    public void testLoadPrefabJsonMapsBlocksAndOffset() throws IOException {
        File file = File.createTempFile("wp-hytale-prefab-", ".prefab.json");
        file.deleteOnExit();

        try (FileWriter writer = new FileWriter(file)) {
            writer.write("{\n" +
                    "  \"version\": 8,\n" +
                    "  \"anchorX\": 10,\n" +
                    "  \"anchorY\": 20,\n" +
                    "  \"anchorZ\": 30,\n" +
                    "  \"blocks\": [\n" +
                    "    {\"x\": 10, \"y\": 20, \"z\": 30, \"name\": \"Soil_Grass\", \"rotation\": 5},\n" +
                    "    {\"x\": 11, \"y\": 20, \"z\": 30, \"name\": \"Rock_Stone\"}\n" +
                    "  ],\n" +
                    "  \"fluids\": [\n" +
                    "    {\"x\": 10, \"y\": 19, \"z\": 30, \"name\": \"Water_Source\"}\n" +
                    "  ],\n" +
                    "  \"entities\": [\n" +
                    "    {\"Components\": {\"Transform\": {\"Position\": {\"X\": 10.0, \"Y\": 20.0, \"Z\": 30.0}}}}\n" +
                    "  ]\n" +
                    "}\n");
        }

        WPObject object = HytalePrefabJsonObject.load(file);

        assertTrue(object.getName().startsWith("wp-hytale-prefab-"));
        assertEquals(new Point3i(2, 1, 2), object.getDimensions());
        assertEquals(new Point3i(0, 0, -1), object.getOffset());

        Material topMaterial = object.getMaterial(0, 0, 1);
        assertEquals("hytale:Soil_Grass", topMaterial.name);
        assertEquals("5", topMaterial.getProperty(HytalePrefabJsonObject.HYTALE_ROTATION_PROPERTY));
        assertEquals("hytale:Rock_Stone", object.getMaterial(1, 0, 1).name);
        assertEquals("hytale:Water_Source", object.getMaterial(0, 0, 0).name);
        assertFalse(object.getMask(1, 0, 0));
        assertNotNull(object.getEntities());
        assertEquals(1, object.getEntities().size());
    }

    @Test
    public void testExplicitHytaleRotationIsPreservedInBlockMapping() {
        Material material = Material.get("hytale:Wood_Oak_Branch_Long")
                .withProperty(HytalePrefabJsonObject.HYTALE_ROTATION_PROPERTY, "23");

        HytaleBlock block = HytaleBlockMapping.toHytaleBlock(material);
        assertEquals(23, block.rotation & 0xFF);
    }

}
