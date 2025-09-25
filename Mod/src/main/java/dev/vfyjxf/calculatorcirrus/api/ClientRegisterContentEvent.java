package dev.vfyjxf.calculatorcirrus.api;

import dev.vfyjxf.calculatorcirrus.api.content.render.ContentRenderer;
import dev.vfyjxf.calculatorcirrus.api.content.render.StackRenderer;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.calculatorcirrus.content.ClientContentRegistryBuilder;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

public class ClientRegisterContentEvent extends Event implements IModBusEvent {

    private final ClientContentRegistryBuilder contentRegistry;

    public ClientRegisterContentEvent(ClientContentRegistryBuilder contentRegistry) {this.contentRegistry = contentRegistry;}

    public <T> void registerRenderer(ContentType<T> contentType, ContentRenderer<T> renderer) {
        contentRegistry.registerRenderer(contentType, renderer);
    }

    public <T, S> void registerRenderer(StackType<T, S> stackType, StackRenderer<T, S> renderer) {
        contentRegistry.registerRenderer(stackType, renderer);
    }

}
