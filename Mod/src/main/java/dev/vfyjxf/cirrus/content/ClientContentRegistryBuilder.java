package dev.vfyjxf.cirrus.content;

import dev.vfyjxf.cirrus.api.content.render.ContentRenderer;
import dev.vfyjxf.cirrus.api.content.render.StackRenderer;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntryRenderer;
import dev.vfyjxf.cirrus.api.register.ContentRendererRegister;
import dev.vfyjxf.cirrus.api.register.MachineEntryRendererRegister;

import java.util.concurrent.ConcurrentHashMap;

public class ClientContentRegistryBuilder extends ContentRegistryBuilder
        implements
            ContentRendererRegister,
            MachineEntryRendererRegister {

    final ConcurrentHashMap<ContentType<?>, ContentRenderer<?>> contentRenderers = new ConcurrentHashMap<>();
    final ConcurrentHashMap<StackType<?, ?>, StackRenderer<?, ?>> stackRenderers = new ConcurrentHashMap<>();
    final ConcurrentHashMap<Class<? extends MachineEntry>, MachineEntryRenderer<?>> handlerRenderers = new ConcurrentHashMap<>();

    @Override
    public <T, S> void registerRenderer(StackType<T, S> stackType, StackRenderer<T, S> stackRenderer) {
        if (!stackTypesById.containsKey(stackType.typeId())) {
            throw new IllegalStateException("The stack type: " + stackType.typeId() + " has not been registered");
        }
        if (stackRenderers.containsKey(stackType)) {
            throw new IllegalStateException(
                "The stack renderer of stack type: " + stackType.typeId() + " has been registered"
            );
        }
        stackRenderers.put(stackType, stackRenderer);
    }

    @Override
    public <T> void registerRenderer(ContentType<T> contentType, ContentRenderer<T> renderer) {
        if (!contentTypes.containsKey(contentType.typeId())) {
            throw new IllegalStateException("The content type: " + contentType.typeId() + " has not been registered");
        }
        if (contentRenderers.containsKey(contentType)) {
            throw new IllegalStateException(
                "The renderer of content type: " + contentType.typeId() + " has been registered"
            );
        }
        contentRenderers.put(contentType, renderer);
    }

    @Override
    public <T extends MachineEntry> void registerHandlerRenderer(Class<T> type, MachineEntryRenderer<T> renderer) {
        if (handlerRenderers.containsKey(type)) {
            throw new IllegalStateException("The renderer of handler type: " + type + " has been registered");
        }
        handlerRenderers.put(type, renderer);
    }
}
