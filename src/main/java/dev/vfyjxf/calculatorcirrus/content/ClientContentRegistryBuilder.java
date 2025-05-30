package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.type.ContentRenderer;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;

import java.util.concurrent.ConcurrentHashMap;

public class ClientContentRegistryBuilder extends ContentRegistryBuilder {

    final ConcurrentHashMap<ContentType<?>, ContentRenderer<?>> contentRenderers = new ConcurrentHashMap<>();

    public <T> void registerRenderer(ContentType<T> contentType, ContentRenderer<T> contentRenderer) {
        if (!contentTypes.containsKey(contentType.typeId())) {
            throw new IllegalStateException("The content type: " + contentType.typeId() + " has not been registered");
        }
        contentRenderers.put(contentType, contentRenderer);
    }
}
