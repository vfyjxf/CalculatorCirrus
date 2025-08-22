package dev.vfyjxf.calculatorcirrus.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

//@EventBusSubscriber(modid = CirrusConstants.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class GameInstances {

    //region server

    //    @Nullable
//    public static MinecraftServer getServer() {
//        return server;
//    }
//
//    private static RecipeManager recipeManager;
//
//    @SubscribeEvent
//    private static void onServerStarting(ServerStartingEvent event) {
//        server = event.getServer();
//    }
//
//    @SubscribeEvent
//    private static void onServerStopped(ServerStoppedEvent event) {
//        server = null;
//    }
    //endregion


    //region recipe manager

    public static RecipeManager getRecipeManager() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            if (!FMLEnvironment.dist.isClient())
                throw new IllegalStateException("Cannot get recipe manager when server is null");
            Minecraft instance = Minecraft.getInstance();
            if (instance.level == null) {
                throw new IllegalStateException("Cannot get recipe manager when client level is null");
            }
            return instance.level.getRecipeManager();
        }
        return server.getRecipeManager();
    }

    public static void setRecipeManager(RecipeManager manager) {
        recipeManager = manager;
    }

    //endregion

}
