package dev.vfyjxf.cirrus.internal.integration.cloud;

import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.plugin.CloudLibPlugin;
import dev.vfyjxf.cloudlib.api.plugin.PluginMarker;
import dev.vfyjxf.cloudlib.api.util.Namespace;

@PluginMarker
public class CirrusCloudLibPlugin implements CloudLibPlugin {

    public static final Namespace id = CirrusNamespaces.ofMod("common");

    @Override
    public Namespace pluginId() {
        return id;
    }
}
