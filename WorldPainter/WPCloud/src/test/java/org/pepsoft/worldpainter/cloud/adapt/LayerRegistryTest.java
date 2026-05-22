package org.pepsoft.worldpainter.cloud.adapt;

import org.junit.jupiter.api.Test;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.Layer;

import static org.assertj.core.api.Assertions.assertThat;

class LayerRegistryTest {

    @Test
    void builtin_layer_has_stable_id() {
        // Frost is a built-in singleton; id should be deterministic across calls
        int id1 = LayerRegistry.idOf(Frost.INSTANCE);
        int id2 = LayerRegistry.idOf(Frost.INSTANCE);
        assertThat(id1).isEqualTo(id2);
        assertThat(id1).isGreaterThan(0);
    }

    @Test
    void layer_round_trips_via_id() {
        Layer original = Frost.INSTANCE;
        int id = LayerRegistry.idOf(original);
        Layer back = LayerRegistry.layerFor(id);
        assertThat(back).isSameAs(original);
    }

    @Test
    void unknown_id_returns_null() {
        assertThat(LayerRegistry.layerFor(99999)).isNull();
    }
}
