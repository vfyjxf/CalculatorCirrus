package dev.vfyjxf.calculatorcirrus.api;

import dev.vfyjxf.calculatorcirrus.api.content.ContentRenderer;
import dev.vfyjxf.calculatorcirrus.api.content.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.TagType;
import dev.vfyjxf.calculatorcirrus.content.ClientContentRegistryImpl;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

public class ClientRegisterContentEvent extends Event implements IModBusEvent {

    private final ClientContentRegistryImpl contentRegistry;

    public ClientRegisterContentEvent(ClientContentRegistryImpl contentRegistry) {
        this.contentRegistry = contentRegistry;
    }

    public <T> void registerContent(ContentType<T> contentType, ContentRenderer<T> contentRenderer) {
        contentRegistry.registerContent(contentType, contentRenderer);
    }

    public <STACK, VALUE> void registerTag(ContentType<STACK> stackType, TagType<STACK, VALUE> tagType) {
        contentRegistry.registerTag(stackType, tagType);
    }

    public <STACK, VALUE> void registerContent(ContentType<STACK> stackType, TagType<STACK, VALUE> tagType, ContentRenderer<STACK> contentRenderer) {
        registerContent(stackType, contentRenderer);
        registerTag(stackType, tagType);
    }

    public <T> void registerRenderer(ContentType<T> contentType, ContentRenderer<T> contentRenderer) {
        contentRegistry.registerRenderer(contentType, contentRenderer);
    }

}
