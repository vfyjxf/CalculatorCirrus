package dev.vfyjxf.calculatorcirrus.mixins;

import dev.vfyjxf.calculatorcirrus.api.ClientRegisterContentEvent;
import dev.vfyjxf.calculatorcirrus.api.UnloadingContentEvent;
import dev.vfyjxf.calculatorcirrus.content.ClientContentApiImpl;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Inject(method = "reloadResourcePacks(ZLnet/minecraft/client/Minecraft$GameLoadCookie;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("RETURN"))
    private void injectReloadResourcePacks(boolean error, @Coerce Object gameLoadCookie, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        CompletableFuture<Void> future = cir.getReturnValue();
        if (future != null) {
            future.thenRunAsync(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.level != null) {
                    ClientContentApiImpl contentRegistry = new ClientContentApiImpl();
                    ClientRegisterContentEvent event = new ClientRegisterContentEvent(contentRegistry);
                    ModLoader.postEventWrapContainerInModOrder(event);
                }
            }, Executors.newFixedThreadPool(1));
        }
    }

    @Inject(at = @At("HEAD"), method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;)V")
    private void injectDisconnect(CallbackInfo info) {
        ModLoader.postEventWrapContainerInModOrder(new UnloadingContentEvent());
    }

}
