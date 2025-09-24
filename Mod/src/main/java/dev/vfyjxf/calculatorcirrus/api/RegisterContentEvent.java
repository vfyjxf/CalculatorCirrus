package dev.vfyjxf.calculatorcirrus.api;

import dev.vfyjxf.calculatorcirrus.api.content.CirrusTypes;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import dev.vfyjxf.calculatorcirrus.api.content.type.VariantType;
import dev.vfyjxf.calculatorcirrus.content.ContentRegistryBuilder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

public class RegisterContentEvent extends Event implements IModBusEvent {

    private final ContentRegistryBuilder registry;

    public RegisterContentEvent(ContentRegistryBuilder registry) {
        this.registry = registry;
    }

    public <T> void registerContent(
        ContentType<T> contentType
    ) {
        registry.registerContentType(contentType);
    }

    public <T> void registerContent(ContentType<T> contentType, TagType<T> tagType) {
        registerContent(contentType);
        registerTag(contentType, tagType);
    }

    public <T> void registerTag(ContentType<T> contentType, TagType<T> tagType) {
        registry.registerTagType(contentType, tagType);
    }

    public <T, S> void registerStack(ContentType<T> contentType, StackType<T, S> stackType) {
        registry.registerStackType(contentType, stackType);
    }

    public <T, S> void registerVariantType(StackType<T, S> stackType, VariantType<T, S> variantType) {
        registry.registerVariantType(stackType, variantType);
    }

    public void registerItemVariantType(VariantType<Item, ItemStack> variantType) {
        registerVariantType(CirrusTypes.ITEM_STACK, variantType);
    }

    public <T, S> void registerVariant(T content, VariantType<T, S> variantType) {
        registry.registerVariantForContent(content, variantType);
    }


}
