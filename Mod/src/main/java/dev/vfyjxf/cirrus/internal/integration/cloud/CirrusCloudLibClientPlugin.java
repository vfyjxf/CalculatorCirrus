package dev.vfyjxf.cirrus.internal.integration.cloud;

import dev.vfyjxf.cirrus.ui.execution.ExecutionOverlay;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.plugin.CloudLibClientPlugin;
import dev.vfyjxf.cloudlib.api.plugin.PluginMarker;
import dev.vfyjxf.cloudlib.api.register.ui.OverlayRegister;
import dev.vfyjxf.cloudlib.api.util.Namespace;

@PluginMarker
public class CirrusCloudLibClientPlugin implements CloudLibClientPlugin {

	public static final Namespace id = CirrusNamespaces.ofMod("client");

	@Override
	public Namespace pluginId() {
		return id;
	}

	@Override
	public void registerOverlay(OverlayRegister register) {
		register.register(ExecutionOverlay.entry);
	}
}
