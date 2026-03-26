package org.pepsoft.worldpainter.layers;

import org.junit.Test;
import org.pepsoft.worldpainter.AbstractRegressionIT;
import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.biomeschemes.CustomBiomeManager;
import org.pepsoft.worldpainter.layers.bo2.Bo2LayerEditor;
import org.pepsoft.worldpainter.layers.bo2.Bo2ObjectTube;
import org.pepsoft.worldpainter.objects.WPObject;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RegressionIT extends AbstractRegressionIT {
    /**
     * Test whether a custom layer saved to a file in version 2.5.1 can still be
     * loaded.
     */
    @Test
    public void test2_5_1Layer() throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new GZIPInputStream(org.pepsoft.worldpainter.tools.scripts.RegressionIT.class.getResourceAsStream("/testset/Forest.layer")))) {
            Bo2Layer layer = (Bo2Layer) in.readObject();
            assertEquals("Forest", layer.getName());
            assertEquals(new Color(0x009900), layer.getPaint());
            assertEquals(20, layer.getDensity());
            List<WPObject> allObjects = layer.getObjectProvider().getAllObjects();
            assertEquals(67, allObjects.size());
            for (WPObject object: allObjects) {
                scanObject(object);
            }
        }
    }

    @Test
    public void testBo2LayerEditorRestoresNoPhysicsWhenUiIsCreatedAfterLayerLoad() throws Exception {
        Bo2Layer layer = new Bo2Layer(new Bo2ObjectTube("Test Objects", Collections.emptyList()), "Custom objects", Color.ORANGE);
        layer.setNoPhysics(true);

        AtomicBoolean checkBoxSelected = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> {
            Bo2LayerEditor editor = new Bo2LayerEditor();
            editor.setContext(new LayerEditor.LayerEditorContext() {
                @Override
                public ColourScheme getColourScheme() {
                    return null;
                }

                @Override
                public boolean isExtendedBlockIds() {
                    return false;
                }

                @Override
                public CustomBiomeManager getCustomBiomeManager() {
                    return null;
                }

                @Override
                public List<Layer> getAllLayers() {
                    return Collections.emptyList();
                }

                @Override
                public void settingsChanged() {
                }

                @Override
                public Dimension getDimension() {
                    return null;
                }
            });
            editor.setLayer(layer);

            JCheckBox checkBox = findCheckBox(editor.getComponent(),
                    "Disable physics (force-place all blocks regardless of terrain)");
            assertNotNull(checkBox);
            checkBoxSelected.set(checkBox.isSelected());
        });

        assertTrue(checkBoxSelected.get());
    }

    private static JCheckBox findCheckBox(Component component, String text) {
        if (component instanceof JCheckBox) {
            JCheckBox checkBox = (JCheckBox) component;
            if (text.equals(checkBox.getText())) {
                return checkBox;
            }
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JCheckBox match = findCheckBox(child, text);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }
}
