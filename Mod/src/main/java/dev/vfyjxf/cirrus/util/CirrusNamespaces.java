package dev.vfyjxf.cirrus.util;

import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cloudlib.api.util.Namespace;

public final class CirrusNamespaces {

    public static Namespace ofMod(String path) {
        return Namespace.of(CirrusConstants.MOD_ID, path);
    }

    private CirrusNamespaces() {}
}
