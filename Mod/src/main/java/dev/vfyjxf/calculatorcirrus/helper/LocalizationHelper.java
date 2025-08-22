package dev.vfyjxf.calculatorcirrus.helper;

import dev.vfyjxf.calculatorcirrus.CirrusConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class LocalizationHelper {

    private LocalizationHelper() {
    }

    public static String key(String children) {
        return CirrusConstants.MOD_ID + "." + children;
    }

    public static MutableComponent translatable(String children, Object... args) {
        return Component.translatable(key(children), args);
    }

}
