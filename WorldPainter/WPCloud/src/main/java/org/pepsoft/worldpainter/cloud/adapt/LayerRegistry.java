package org.pepsoft.worldpainter.cloud.adapt;

import org.pepsoft.worldpainter.layers.Layer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bidirectional mapping between {@link Layer} instances and int ids used in CRDT ops.
 *
 * <p>Built-in layers receive stable hard-coded ids (see the {@code BUILTIN} table below).
 * Custom plugin layers receive runtime ids on first encounter; this is best-effort and not
 * cross-session-stable (TD-035).
 *
 * <p><strong>Invariant:</strong> two clients editing the same cloud world MUST agree on
 * built-in layer ids. The hard-coded table is the contract.
 */
public final class LayerRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(LayerRegistry.class);

    private static final Map<String, Integer> BUILTIN_NAME_TO_ID = new ConcurrentHashMap<>();
    private static final Map<Integer, Layer> ID_TO_LAYER = new ConcurrentHashMap<>();

    private static final AtomicInteger NEXT_DYNAMIC_ID = new AtomicInteger(1000);

    static {
        // Hard-coded table for built-in WPCore layers. Order is the contract.
        // Each register call is tolerant of ClassNotFoundException — adjust the list to match
        // the actual WPCore tree (some FQNs in the plan may not be in this fork).
        register(1, "org.pepsoft.worldpainter.layers.Frost");
        register(2, "org.pepsoft.worldpainter.layers.Resources");
        register(3, "org.pepsoft.worldpainter.layers.Caverns");
        register(4, "org.pepsoft.worldpainter.layers.Chasms");
        register(5, "org.pepsoft.worldpainter.layers.Biome");
        register(6, "org.pepsoft.worldpainter.layers.Annotations");
        register(7, "org.pepsoft.worldpainter.layers.ReadOnly");
        register(8, "org.pepsoft.worldpainter.layers.Void");
        register(9, "org.pepsoft.worldpainter.layers.FloodWithLava");
        register(10, "org.pepsoft.worldpainter.layers.NotPresent");
        // Dynamic ids start at 1000 to leave room for built-ins.
    }

    private static void register(int id, String fqn) {
        BUILTIN_NAME_TO_ID.put(fqn, id);
        try {
            Class<?> clazz = Class.forName(fqn);
            // Try Layer.INSTANCE first (the common singleton pattern), then no-arg ctor
            Layer instance;
            try {
                instance = (Layer) clazz.getField("INSTANCE").get(null);
            } catch (NoSuchFieldException nf) {
                instance = (Layer) clazz.getDeclaredConstructor().newInstance();
            }
            ID_TO_LAYER.put(id, instance);
        } catch (ClassNotFoundException e) {
            LOG.debug("Built-in layer not on classpath: {}", fqn);
        } catch (Exception e) {
            LOG.warn("Failed to register built-in layer {}: {}", fqn, e.getMessage());
        }
    }

    private LayerRegistry() {}

    public static int idOf(Layer layer) {
        if (layer == null) throw new IllegalArgumentException("layer");
        String fqn = layer.getClass().getName();
        Integer builtin = BUILTIN_NAME_TO_ID.get(fqn);
        if (builtin != null) return builtin;
        // Dynamic registration for custom plugin layers
        int dynamicId = NEXT_DYNAMIC_ID.getAndIncrement();
        BUILTIN_NAME_TO_ID.put(fqn, dynamicId);  // memoize
        ID_TO_LAYER.put(dynamicId, layer);
        LOG.info("Registered custom layer {} with runtime id {}", fqn, dynamicId);
        return dynamicId;
    }

    public static Layer layerFor(int id) {
        return ID_TO_LAYER.get(id);
    }
}
