package dev.vfyjxf.cirrus.content;

import dev.vfyjxf.cirrus.api.content.render.ContentRenderer;
import dev.vfyjxf.cirrus.api.content.render.StackRenderer;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntryRenderer;
import dev.vfyjxf.cloudlib.util.Checks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.Map;

@ApiStatus.Internal
public final class ClientContentApiImpl extends ContentApiImpl {

    //region instance

    public static ClientContentApiImpl instance() {
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            throw new IllegalStateException("ClientContentApiImpl should only be used on client side!");
        }
        ClientContentApiImpl instance = (ClientContentApiImpl) ContentApiImpl.instance();
        if (instance == null) {
            throw new IllegalStateException("ContentApi is not initialized!");
        }
        return instance;
    }

    //endregion

    //region internal

    private final Map<ContentType<?>, ContentRenderer<?>> contentRenderers;
    private final Map<StackType<?, ?>, StackRenderer<?, ?>> stackRenderers;
    private final Map<Class<? extends MachineEntry>, MachineEntryRenderer<?>> handlerEntryRenderers;

    public ClientContentApiImpl(ClientContentRegistryBuilder builder) {
        super(builder);
        this.contentRenderers = new HashMap<>(builder.contentRenderers);
        this.stackRenderers = new HashMap<>(builder.stackRenderers);
        this.handlerEntryRenderers = new HashMap<>(builder.handlerRenderers);
    }

    //endregion

    @ApiStatus.Internal
    @SuppressWarnings("unchecked")
    public <T, S> StackRenderer<T, S> getStackRenderer(StackType<T, S> contentType) {
        Checks.checkNotNull(contentType, "contentType");
        Checks.checkArgument(FMLEnvironment.dist.isClient(), "ContentRenderer should only be used on client side!");
        StackRenderer<T, S> contentRenderer = (StackRenderer<T, S>) stackRenderers.get(contentType);
        if (contentRenderer == null) {
            throw new IllegalStateException("No renderer found for content type: " + contentType);
        }
        return contentRenderer;
    }

    @ApiStatus.Internal
    @SuppressWarnings("unchecked")
    public <T> ContentRenderer<T> getContentRenderer(ContentType<T> contentType) {
        Checks.checkNotNull(contentType, "contentType");
        Checks.checkArgument(FMLEnvironment.dist.isClient(), "ContentRenderer should only be used on client side!");
        ContentRenderer<T> contentRenderer = (ContentRenderer<T>) contentRenderers.get(contentType);
        if (contentRenderer == null) {
            throw new IllegalStateException("No renderer found for content type: " + contentType);
        }
        return contentRenderer;
    }

    @ApiStatus.Internal
    @SuppressWarnings("unchecked")
    public <T extends MachineEntry> MachineEntryRenderer<T> getHandlerEntryRenderer(Class<T> type) {
        Checks.checkNotNull(type, "type");
        Checks.checkArgument(
            FMLEnvironment.dist.isClient(),
            "HandlerEntryRenderer should only be used on client side!"
        );
        MachineEntryRenderer<T> handlerEntryRenderer = (MachineEntryRenderer<T>) handlerEntryRenderers.get(type);
        if (handlerEntryRenderer == null) {
            throw new IllegalStateException("No renderer found for handler entry type: " + type);
        }
        return handlerEntryRenderer;
    }
}
