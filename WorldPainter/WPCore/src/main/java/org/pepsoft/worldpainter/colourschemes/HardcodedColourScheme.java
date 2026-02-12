package org.pepsoft.worldpainter.colourschemes;

import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.hytale.HytaleBlockRegistry;

import static org.pepsoft.worldpainter.Constants.UNKNOWN_MATERIAL_COLOUR;

public class HardcodedColourScheme implements ColourScheme {
    @Override
    public int getColour(Material material) {
        if (material.colour == UNKNOWN_MATERIAL_COLOUR
                && HytaleBlockRegistry.HYTALE_NAMESPACE.equals(material.namespace)) {
            Integer htColour = HytaleBlockRegistry.getBlockColour(material.simpleName);
            if (htColour != null) {
                return htColour;
            }
        }
        return material.colour;
    }
}