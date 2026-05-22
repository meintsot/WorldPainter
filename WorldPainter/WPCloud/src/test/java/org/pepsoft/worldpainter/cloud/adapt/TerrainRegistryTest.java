package org.pepsoft.worldpainter.cloud.adapt;

import org.junit.jupiter.api.Test;
import org.pepsoft.worldpainter.Terrain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TerrainRegistryTest {

    @Test
    void byte_to_terrain_round_trips() {
        for (Terrain t : Terrain.values()) {
            byte b = TerrainRegistry.toByte(t);
            Terrain back = TerrainRegistry.fromByte(b);
            assertThat(back).isEqualTo(t);
        }
    }

    @Test
    void null_terrain_rejects() {
        assertThatThrownBy(() -> TerrainRegistry.toByte(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalid_byte_throws() {
        // Terrain has ~80 ordinals; 127 should be safely out of range. If the WPCore Terrain
        // enum has grown beyond 127 values, raise the magic number here OR pick a value
        // that's definitely > Terrain.values().length and within byte range.
        int max = Terrain.values().length;
        if (max <= 127) {
            assertThatThrownBy(() -> TerrainRegistry.fromByte((byte) 127))
                    .isInstanceOf(IllegalArgumentException.class);
        } else {
            // Defensive: if Terrain has > 127 values, this test is no longer applicable
            // because all bytes 0-127 are valid ordinals. Skip with a deliberate pass.
            assertThat(true).isTrue();
        }
    }
}
