package dev.vfyjxf.cirrus.util;

import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cloudlib.api.util.Namespace;

public final class CirrusNamespaces {

    public static final String internal = "cirrus";

    public static Namespace ofMod(String path) {
        return Namespace.of(CirrusConstants.modId, path);
    }

    public static Namespace ofInternal(String path) {
        return Namespace.of(internal, path);
    }

    private CirrusNamespaces() {}
}
