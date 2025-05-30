package dev.vfyjxf.calculatorcirrus.api;

import dev.vfyjxf.calculatorcirrus.api.content.type.ContentRenderer;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.content.ClientContentRegistryBuilder;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

public class ClientRegisterContentEvent extends Event implements IModBusEvent {

    private final ClientContentRegistryBuilder contentRegistry;

    public ClientRegisterContentEvent(ClientContentRegistryBuilder contentRegistry) {this.contentRegistry = contentRegistry;}

    public <T> void registerRenderer(ContentType<T> contentType, ContentRenderer<T> contentRenderer) {
        contentRegistry.registerRenderer(contentType, contentRenderer);
    }

}
