package org.pepsoft.worldpainter.hytale.prefab;

import org.junit.Test;
import static org.junit.Assert.*;

public class PrefabTransferableTest {
    @Test
    public void carriesPathAndName() throws Exception {
        PrefabTransferable t = new PrefabTransferable("Prefabs/Trees/Oak.prefab.json", "Oak");
        assertTrue(t.isDataFlavorSupported(PrefabTransferable.PREFAB_FLAVOR));
        Object data = t.getTransferData(PrefabTransferable.PREFAB_FLAVOR);
        assertTrue(data instanceof PrefabTransferable.Payload);
        PrefabTransferable.Payload p = (PrefabTransferable.Payload) data;
        assertEquals("Prefabs/Trees/Oak.prefab.json", p.path);
        assertEquals("Oak", p.name);
    }
}
