package org.pepsoft.worldpainter.hytale;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * TP-44: regression test for duplicate {@link HytaleTerrain} entries that
 * shared the same block ID.
 *
 * <p>Before the fix, eleven terrain entries (e.g. AZURE_FERN, BLOOD_ROSE,
 * STORM_THISTLE) shared block IDs with their non-tinted counterparts. Hytale
 * handles biome colour variation via per-column vegetation tinting, so these
 * entries exported the exact same block as their primary. They also broke
 * the material selector via {@code putIfAbsent} stealing display names — for
 * example "Azure Fern" overrode "Forest Fern" for {@code Plant_Fern_Forest}.
 *
 * <p>This test asserts {@link HytaleTerrain#PICK_LIST} cannot regrow such
 * duplicates: every terrain in the picker must map to a distinct primary
 * block ID. Failures should print the offending pair so the duplicate is
 * easy to spot.
 */
public class HytaleTerrainEnumIntegrityTest {

    @Test
    public void pickListHasNoDuplicatePrimaryBlockIds() {
        Map<String, HytaleTerrain> seen = new LinkedHashMap<>();
        Map<String, HytaleTerrain> duplicates = new LinkedHashMap<>();

        for (HytaleTerrain terrain : HytaleTerrain.PICK_LIST) {
            HytaleBlock block = terrain.getBlock();
            assertNotNull("HytaleTerrain " + terrain.getName() + " must have a block",
                    block);
            String id = block.id;
            if (seen.containsKey(id)) {
                duplicates.put(id, terrain);
            } else {
                seen.put(id, terrain);
            }
        }

        if (!duplicates.isEmpty()) {
            StringBuilder msg = new StringBuilder("Duplicate primary block IDs in HytaleTerrain.PICK_LIST:\n");
            for (Map.Entry<String, HytaleTerrain> entry : duplicates.entrySet()) {
                msg.append("  block id '").append(entry.getKey()).append("' shared by '")
                        .append(seen.get(entry.getKey()).getName()).append("' and '")
                        .append(entry.getValue().getName()).append("'\n");
            }
            fail(msg.toString());
        }
    }

    @Test
    public void pickListHasNoDuplicateNames() {
        // Display names must also be unique. The duplicate-id bug surfaced as
        // a name conflict in the material selector when putIfAbsent decided
        // which terrain "owned" a block ID — the test ensures we don't
        // reintroduce two terrains advertising the same name either.
        Set<String> seen = new HashSet<>();
        Map<String, Integer> duplicateCounts = new HashMap<>();
        for (HytaleTerrain terrain : HytaleTerrain.PICK_LIST) {
            String name = terrain.getName();
            if (!seen.add(name)) {
                duplicateCounts.merge(name, 1, Integer::sum);
            }
        }
        assertFalse("Duplicate display names in PICK_LIST: " + duplicateCounts,
                !duplicateCounts.isEmpty());
    }

    @Test
    public void removedDuplicateTerrainsStayRemoved() {
        // Make the regression machine-checkable: the eleven specific terrains
        // listed in commit 42cd936d must not return as static fields.
        // If someone reintroduces one of these, this test fails with a
        // pointer to the commit explaining why they were removed.
        String[] removedFieldNames = {
                "CRACKED_SLATE", "FIR_LEAVES_TIP", "SNOWY_FIR_LEAVES_TIP",
                "BLOOD_ROSE", "BLOOD_CAP_MUSHROOM", "BLOOD_LEAF",
                "AZURE_FERN", "AZURE_CAP_MUSHROOM", "AZURE_KELP",
                "STORM_THISTLE", "STORM_CAP_MUSHROOM",
        };

        List<String> reintroduced = new java.util.ArrayList<>();
        for (String fieldName : removedFieldNames) {
            try {
                HytaleTerrain.class.getField(fieldName);
                reintroduced.add(fieldName);
            } catch (NoSuchFieldException expected) {
                // good — the duplicate stays removed
            }
        }

        assertFalse("Duplicate terrain entries reintroduced (see commit 42cd936d): " + reintroduced,
                !reintroduced.isEmpty());
    }
}
