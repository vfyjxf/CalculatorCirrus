package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.render.ContentRenderer;
import dev.vfyjxf.calculatorcirrus.api.content.render.StackRenderer;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.cloudlib.util.Checks;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.Map;

@ApiStatus.Internal
public final class ClientContentApiImpl extends ContentApiImpl {

    private final Map<ContentType<?>, ContentRenderer<?>> contentRenderers;
    private final Map<StackType<?, ?>, StackRenderer<?, ?>> stackRenderers;

    public ClientContentApiImpl(ClientContentRegistryBuilder builder) {
        super(builder);
        this.contentRenderers = new HashMap<>(builder.contentRenderers);
        this.stackRenderers = new HashMap<>(builder.stackRenderers);
    }

    @ApiStatus.Internal
    @SuppressWarnings("unchecked")
    public <T, S> StackRenderer<T, S> getRenderer(StackType<T, S> contentType) {
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
    public <T> ContentRenderer<T> getRenderer(ContentType<T> contentType) {
        Checks.checkNotNull(contentType, "contentType");
        Checks.checkArgument(FMLEnvironment.dist.isClient(), "ContentRenderer should only be used on client side!");
        ContentRenderer<T> contentRenderer = (ContentRenderer<T>) contentRenderers.get(contentType);
        if (contentRenderer == null) {
            throw new IllegalStateException("No renderer found for content type: " + contentType);
        }
        return contentRenderer;
    }
}
