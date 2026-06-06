package org.pepsoft.worldpainter.hytale.prefab;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

/** Drag payload for a prefab dragged from the palette onto the map. */
public final class PrefabTransferable implements Transferable {

    public static final DataFlavor PREFAB_FLAVOR =
            new DataFlavor(Payload.class, "Hytale prefab placement");

    public static final class Payload {
        public final String path;
        public final String name;

        public Payload(String path, String name) {
            this.path = path;
            this.name = name;
        }
    }

    private final Payload payload;

    public PrefabTransferable(String path, String name) {
        this.payload = new Payload(path, name);
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{PREFAB_FLAVOR};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return PREFAB_FLAVOR.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (!PREFAB_FLAVOR.equals(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }
        return payload;
    }
}
