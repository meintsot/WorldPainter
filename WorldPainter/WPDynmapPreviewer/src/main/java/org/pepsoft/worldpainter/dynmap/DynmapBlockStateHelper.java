package org.pepsoft.worldpainter.dynmap;

import com.google.common.collect.ImmutableMap;
import org.dynmap.renderer.DynmapBlockState;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.hytale.HytaleBlockRegistry;

import java.util.*;

import static java.util.stream.Collectors.joining;
import static org.pepsoft.minecraft.Constants.MC_AIR;
import static org.pepsoft.minecraft.Constants.MC_CAVE_AIR;
import static org.pepsoft.minecraft.Material.MINECRAFT;

public class DynmapBlockStateHelper {
    /**
     * Initialise the Dynmap {@link DynmapBlockState}s for all currently loaded {@link Material}s. This can be done
     * only once, after which Dynmap will only be able to render the materials known at that time.
     */
    public static void initialise() {
        // Do nothing (loading the class has done the initialisation)
    }

    static DynmapBlockState getDynmapBlockState(Material material) {
        DynmapBlockState state = IDENTITY_TO_BLOCK_STATE.get(material.identity);
        if (state != null) {
            return state;
        }
        // For Hytale blocks, map to a visually similar Minecraft block for 3D preview rendering
        if (material.namespace.equals(HytaleBlockRegistry.HYTALE_NAMESPACE)) {
            return hytaleBlockStateCache.computeIfAbsent(material.identity, k -> findHytaleFallbackBlockState(material));
        }
        return null;
    }

    /**
     * Find a Minecraft DynmapBlockState to use as a visual stand-in for a Hytale block
     * in the 3D preview. Maps by category or name heuristics.
     */
    private static DynmapBlockState findHytaleFallbackBlockState(Material material) {
        HytaleBlockRegistry.Category category = HytaleBlockRegistry.getCategoryForBlock(material.simpleName);
        String mcName = mapHytaleCategoryToMinecraft(category, material.simpleName);
        Material mcMaterial = Material.get(MINECRAFT + ':' + mcName);
        DynmapBlockState mcState = IDENTITY_TO_BLOCK_STATE.get(mcMaterial.identity);
        return (mcState != null) ? mcState : IDENTITY_TO_BLOCK_STATE.get(Material.STONE.identity);
    }

    private static String mapHytaleCategoryToMinecraft(HytaleBlockRegistry.Category category, String simpleName) {
        // Name-based matching first for better accuracy
        String lower = simpleName.toLowerCase();
        if (lower.contains("trunk") || lower.contains("log") || lower.contains("bark")) {
            return "oak_log";
        } else if (lower.contains("plank")) {
            return "oak_planks";
        } else if (lower.contains("leaf") || lower.contains("leaves")) {
            return "oak_leaves";
        } else if (lower.contains("grass_block") || lower.equals("grass")) {
            return "grass_block";
        } else if (lower.contains("dirt") || lower.contains("soil")) {
            return "dirt";
        } else if (lower.contains("sand") && !lower.contains("stone")) {
            return "sand";
        } else if (lower.contains("snow")) {
            return "snow_block";
        } else if (lower.contains("ice")) {
            return "ice";
        } else if (lower.contains("clay")) {
            return "clay";
        } else if (lower.contains("gravel") || lower.contains("pebble")) {
            return "gravel";
        } else if (lower.contains("cobble")) {
            return "cobblestone";
        } else if (lower.contains("brick")) {
            return "bricks";
        } else if (lower.contains("water") || lower.contains("fluid")) {
            return "water";
        } else if (lower.contains("flower") || lower.contains("rose") || lower.contains("tulip")) {
            return "poppy";
        } else if (lower.contains("fern")) {
            return "fern";
        } else if (lower.contains("mushroom")) {
            return "brown_mushroom_block";
        } else if (lower.contains("cactus")) {
            return "cactus";
        } else if (lower.contains("wool") || lower.contains("cloth")) {
            return "white_wool";
        }
        // Fall back to category
        if (category != null) {
            switch (category) {
                case SOIL:              return "dirt";
                case SAND:              return "sand";
                case CLAY:              return "clay";
                case SNOW_ICE:          return "snow_block";
                case GRAVEL:            return "gravel";
                case ROCK:              return "stone";
                case ROCK_CONSTRUCTION: return "stone_bricks";
                case ORE:               return "iron_ore";
                case CRYSTAL_GEM:       return "diamond_block";
                case WOOD_NATURAL:      return "oak_log";
                case WOOD_PLANKS:       return "oak_planks";
                case LEAVES:            return "oak_leaves";
                case GRASS_PLANTS:      return "grass";
                case FLOWERS:           return "poppy";
                case FERNS:             return "fern";
                case BUSHES:            return "oak_leaves";
                case CACTUS:            return "cactus";
                case MOSS_VINES:        return "vine";
                case MUSHROOMS:         return "brown_mushroom_block";
                case CROPS:             return "wheat";
                case CORAL:             return "brain_coral_block";
                case SEAWEED:           return "seagrass";
                case SAPLINGS_FRUITS:   return "oak_sapling";
                case RUBBLE:            return "cobblestone";
                case DECORATION:        return "oak_planks";
                case CLOTH:             return "white_wool";
                case HIVE:              return "honeycomb_block";
                case RUNIC:             return "lapis_block";
                case FLUID:             return "water";
                case SPECIAL:           return "bedrock";
            }
        }
        return "stone";
    }

    private static final Map<Material.Identity, DynmapBlockState> hytaleBlockStateCache = new HashMap<>();

    private static final Map<Material.Identity, DynmapBlockState> IDENTITY_TO_BLOCK_STATE;

    static {
        final Map<Material.Identity, DynmapBlockState> identityToBlockState = new HashMap<>();
        final Map<String, Set<DynmapBlockState>> blockStatesByName = new HashMap<>();
        final Map<String, DynmapBlockState> blockStatesBasesByName = new HashMap<>();
        for (String simpleName: Material.getAllSimpleNamesForNamespace(MINECRAFT)) {
            final Material prototype = Material.getPrototype(MINECRAFT + ':' + simpleName);
            if ((prototype.propertyDescriptors == null) || prototype.propertyDescriptors.isEmpty()) {
                // Simple material
                final DynmapBlockState blockState = new DynmapBlockState(null,
                        0,
                        prototype.name,
                        null,
                        prototype.simpleName,
                        prototype.blockType);
                setFlags(blockState, prototype);
                blockStatesBasesByName.put(prototype.name, blockState);
                blockStatesByName.computeIfAbsent(prototype.name, k -> new HashSet<>()).add(blockState);
                identityToBlockState.put(prototype.identity, blockState);
            } else {
                // Create all possible permutations of properties
                // Material.propertyDescriptors is sorted by property name, which is important to Dynmap as it expects
                // both the block variants and the properties in the statename to be in an exact order for the rendering
                // logic to work properly
                List<Map<String, String>> permutations = new ArrayList<>();
                permutations.add(new HashMap<>());
                for (Map.Entry<String, Material.PropertyDescriptor> entry: prototype.propertyDescriptors.entrySet()) {
                    final String name = entry.getKey();
                    final Material.PropertyDescriptor descriptor = entry.getValue();
                    List<Map<String, String>> newPermutations = new ArrayList<>(permutations.size() * 5);
                    for (Map<String, String> partialPermutation: permutations) {
                        switch (descriptor.type) {
                            case BOOLEAN:
                                newPermutations.add(copyAndAdd(partialPermutation, name, "true"));
                                newPermutations.add(copyAndAdd(partialPermutation, name, "false"));
                                break;
                            case ENUM:
                                for (String value: descriptor.enumValues) {
                                    newPermutations.add(copyAndAdd(partialPermutation, name, value));
                                }
                                break;
                            case INTEGER:
                                for (int value = descriptor.minValue; value <= descriptor.maxValue; value++) {
                                    newPermutations.add(copyAndAdd(partialPermutation, name, Integer.toString(value)));
                                }
                                break;
                        }
                        // Also include a permutation _without_ each property
                        newPermutations.add(partialPermutation);
                    }
                    permutations = newPermutations;
                }

                // Make sure the permutations with all properties set are at the start of the list, to ensure those are
                // at the indices that Dynmap expects in its rendering logic. We only add the variants with missing
                // properties to cover blocks that we might load from custom objects, etc., and accept that Dynmap might
                // not be able to render those
                final Set<Map<String, String>> permutationsWithMissingProperties = new HashSet<>(permutations.size());
                for (Iterator<Map<String, String>> i = permutations.iterator(); i.hasNext(); ) {
                    final Map<String, String> permutation = i.next();
                    if (permutation.size() < prototype.propertyDescriptors.size()) {
                        i.remove();
                        permutationsWithMissingProperties.add(permutation);
                    }
                }
                // Add them back at the end of the list
                permutations.addAll(permutationsWithMissingProperties);

                for (Map<String, String> properties: permutations) {
                    final Material material = Material.get(prototype.name, properties);
                    if (identityToBlockState.containsKey(material.identity)) {
                        continue;
                    }
                    final String stateName = properties.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .collect(joining(","));
                    final DynmapBlockState blockState;
                    if (blockStatesByName.containsKey(material.name)) {
                        // Variation
                        blockState = new DynmapBlockState(blockStatesBasesByName.get(material.name),
                                blockStatesByName.get(material.name).size(),
                                material.name,
                                stateName,
                                material.simpleName,
                                material.blockType);
                    } else {
                        // Base block
                        blockState = new DynmapBlockState(null,
                                0,
                                material.name,
                                stateName,
                                material.simpleName,
                                material.blockType);
                        blockStatesBasesByName.put(material.name, blockState);
                    }
                    setFlags(blockState, material);
                    blockStatesByName.computeIfAbsent(material.name, k -> new HashSet<>()).add(blockState);
                    identityToBlockState.put(material.identity, blockState);
                }
            }
        }
        IDENTITY_TO_BLOCK_STATE = ImmutableMap.copyOf(identityToBlockState);
        DynmapBlockState.finalizeBlockStates();
    }

    private static <K, V> Map<K, V> copyAndAdd(Map<K, V> map, K key, V value) {
        final Map<K, V> copy = new HashMap<>(map);
        copy.put(key, value);
        return copy;
    }

    private static void setFlags(DynmapBlockState blockState, Material material) {
        if (material.isNamedOneOf(MC_AIR, MC_CAVE_AIR)) {
            blockState.setAir();
        }
        if (material.sustainsLeaves) {
            blockState.setLog();
        }
        if (material.leafBlock) {
            blockState.setLeaves();
        }
        if (material.solid) {
            blockState.setSolid();
        }
        if (material.containsWater()) {
            blockState.setWaterlogged();
        }
    }
}