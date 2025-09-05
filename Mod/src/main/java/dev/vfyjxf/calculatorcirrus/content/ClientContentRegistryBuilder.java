package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.render.ContentRenderer;
import dev.vfyjxf.calculatorcirrus.api.content.render.StackRenderer;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;

import java.util.concurrent.ConcurrentHashMap;

public class ClientContentRegistryBuilder extends ContentRegistryBuilder {

    final ConcurrentHashMap<ContentType<?>, ContentRenderer<?>> contentRenderers = new ConcurrentHashMap<>();
    final ConcurrentHashMap<StackType<?, ?>, StackRenderer<?, ?>> stackRenderers = new ConcurrentHashMap<>();

    public <T, S> void registerRenderer(StackType<T, S> stackType, StackRenderer<T, S> stackRenderer) {
        if (!stackTypes.containsKey(stackType.typeId())) {
            throw new IllegalStateException("The stack type: " + stackType.typeId() + " has not been registered");
        }
        stackRenderers.put(stackType, stackRenderer);
    }

    public <T> void registerRenderer(ContentType<T> contentType, ContentRenderer<T> renderer) {
        if (!contentTypes.containsKey(contentType.typeId())) {
            throw new IllegalStateException("The content type: " + contentType.typeId() + " has not been registered");
        }
        contentRenderers.put(contentType, renderer);
    }
}
