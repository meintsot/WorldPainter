package org.pepsoft.worldpainter;

import org.junit.Assume;
import org.junit.Test;
import org.pepsoft.worldpainter.plugins.WPPluginManager;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;

/**
 * TP-122: the heightmap import dialog gained per-axis scale controls (Scale X / Y spinners + "uniform" checkbox).
 * The GroupLayout in {@code initComponents()} is hand-maintained; an inconsistent layout throws at construction
 * time, so simply constructing the dialog (which also calls {@code pack()}) verifies the layout.
 */
public class Tp122ImportHeightMapDialogLayoutTest {

    @Test
    public void dialogConstructsAndPacksWithPerAxisScaleControls() throws Exception {
        Assume.assumeFalse("Requires a display", GraphicsEnvironment.isHeadless());
        final Configuration previousConfiguration = Configuration.getInstance();
        Configuration.setInstance(new Configuration());
        if (WPPluginManager.getInstance() == null) {
            WPPluginManager.initialise(null, WPContext.INSTANCE);
        }
        try {
            SwingUtilities.invokeAndWait(() -> {
                final ImportHeightMapDialog dialog = new ImportHeightMapDialog(
                        null, ColourScheme.DEFAULT, false, 10, TileRenderer.LightOrigin.NORTHWEST, null);
                dialog.dispose();
            });
        } finally {
            Configuration.setInstance(previousConfiguration);
        }
    }
}
