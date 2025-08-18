package dev.vfyjxf.calculatorcirrus.integration;

import dev.vfyjxf.calculatorcirrus.api.RegisterContentEvent;
import dev.vfyjxf.calculatorcirrus.api.RegisterRecipeSystemEvent;
import dev.vfyjxf.cloudlib.api.util.ServiceLoading;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import org.eclipse.collections.api.list.MutableList;

public abstract class CirrusIntegration {

    protected MutableList<CirrusAdapter> plugins;

    public CirrusIntegration(IEventBus modBus, ModContainer container) {
        modBus.addListener(this::registerContent);
        modBus.addListener(this::registerRecipeSystem);
    }

    private void registerContent(RegisterContentEvent event) {
        if (this.plugins == null) {
            this.plugins = ServiceLoading.load(CirrusAdapter.class);
        }
        for (CirrusAdapter plugin : plugins) {
            plugin.registerContent(event);
        }
    }

    private void registerRecipeSystem(RegisterRecipeSystemEvent event) {
        if (this.plugins == null) {
            this.plugins = ServiceLoading.load(CirrusAdapter.class);
        }
        for (CirrusAdapter plugin : plugins) {
            plugin.registerRecipeSystem(event);
        }
    }

}
