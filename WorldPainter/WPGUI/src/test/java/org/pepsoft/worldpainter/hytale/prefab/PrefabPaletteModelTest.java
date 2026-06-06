package org.pepsoft.worldpainter.hytale.prefab;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class PrefabPaletteModelTest {
    @Test
    public void includesBuiltInsGroupedByCategory() {
        PrefabPaletteModel model = PrefabPaletteModel.builtInsOnly();
        assertFalse(model.getCategories().isEmpty());
        String cat = model.getCategories().get(0);
        List<PrefabPaletteModel.PrefabItem> items = model.itemsInCategory(cat);
        assertFalse(items.isEmpty());
        assertNotNull(items.get(0).path);
        assertEquals(cat, items.get(0).category);
    }

    @Test
    public void searchFiltersByNameCategoryOrPath() {
        PrefabPaletteModel model = PrefabPaletteModel.builtInsOnly();
        assertEquals(model.allItems().size(), model.search("").size());
        assertTrue(model.search("zzzz_no_such_prefab_zzzz").isEmpty());
    }
}
