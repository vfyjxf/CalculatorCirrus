package dev.vfyjxf.calculatorcirrus.api;

import dev.vfyjxf.calculatorcirrus.api.content.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.TagType;
import dev.vfyjxf.calculatorcirrus.content.ContentApiImpl;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

public class RegisterContentEvent extends Event implements IModBusEvent {
    private final ContentApiImpl registry;

    public RegisterContentEvent(ContentApiImpl registry) {
        this.registry = registry;
    }

    public <T> void registerContent(ContentType<T> contentType) {
        registry.registerContent(contentType);
    }

    public <STACK, VALUE> void registerContent(ContentType<STACK> stackType, TagType<STACK, VALUE> tagType) {
        registerContent(stackType);
        registerTag(stackType, tagType);
    }

    public <STACK, VALUE> void registerTag(ContentType<STACK> stackType, TagType<STACK, VALUE> tagType) {
        registry.registerTag(stackType, tagType);
    }
}
