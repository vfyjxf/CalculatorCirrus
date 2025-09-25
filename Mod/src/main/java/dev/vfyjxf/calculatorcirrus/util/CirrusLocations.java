package dev.vfyjxf.calculatorcirrus.util;

import dev.vfyjxf.calculatorcirrus.CirrusConstants;
import net.minecraft.resources.ResourceLocation;

public final class CirrusLocations {

    public static ResourceLocation ofMod(String path) {
        return ResourceLocation.fromNamespaceAndPath(CirrusConstants.MOD_ID, path);
    }

    private CirrusLocations() {}
}
