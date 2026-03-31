package dev.vfyjxf.cirrus.mixin;

import net.neoforged.neoforge.client.ItemDecoratorHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ItemDecoratorHandler.class)
public interface ItemDecoratorHandlerAccessor {

    @Accessor("itemDecorators")
    List<?> cirrus$getItemDecorators();
}
