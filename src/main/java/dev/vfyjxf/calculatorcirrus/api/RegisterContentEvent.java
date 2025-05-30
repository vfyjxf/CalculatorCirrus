package dev.vfyjxf.calculatorcirrus.api;

import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import dev.vfyjxf.calculatorcirrus.content.ContentRegistryBuilder;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

public class RegisterContentEvent extends Event implements IModBusEvent {

    protected final ContentRegistryBuilder registry;

    public RegisterContentEvent(ContentRegistryBuilder registry) {
        this.registry = registry;
    }

    public <T> void registerContent(
            ContentType<T> contentType
    ) {
        registry.registerContent(contentType);
    }

    public <STACK, VALUE> void registerContent(
            ContentType<STACK> contentType,
            TagType<VALUE, STACK> tagType
    ) {
        registerContent(contentType);
        registerTag(contentType, tagType);
    }

    public <STACK, VALUE> void registerTag(ContentType<STACK> contentType, TagType<VALUE, STACK> tagType) {
        registry.registerTag(contentType, tagType);
    }

}
