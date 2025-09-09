package dev.vfyjxf.calculatorcirrus.integration.gtm;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import dev.vfyjxf.calculatorcirrus.api.RegisterContentEvent;
import dev.vfyjxf.calculatorcirrus.api.RegisterAdapterPlugin;
import dev.vfyjxf.calculatorcirrus.api.RegisterRecipeSystemEvent;
import dev.vfyjxf.calculatorcirrus.integration.CirrusAdapter;
import dev.vfyjxf.calculatorcirrus.integration.gtm.converter.FluidConverter;
import dev.vfyjxf.calculatorcirrus.integration.gtm.converter.ItemConverter;
import dev.vfyjxf.calculatorcirrus.integration.gtm.systems.GTMRecipeSystem;
import net.minecraft.core.registries.BuiltInRegistries;

public class PluginRegister {

    @RegisterAdapterPlugin
    public static class Adapter implements CirrusAdapter {

        @Override
        public void registerContent(RegisterContentEvent event) {

        }

        @Override
        public void registerRecipeSystem(RegisterRecipeSystemEvent event) {
            BuiltInRegistries.RECIPE_TYPE.stream()
                    .filter(GTRecipeType.class::isInstance)
                    .map(GTRecipeType.class::cast)
                    .map(GTMRecipeSystem::new)
                    .forEach(event::registerSystem);
            GTMRecipeSystem.registerConverter(ItemRecipeCapability.CAP, new ItemConverter());
            GTMRecipeSystem.registerConverter(FluidRecipeCapability.CAP, new FluidConverter());
        }
    }
}
