package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.render.StackRenderer;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.cloudlib.util.Checks;
import net.neoforged.fml.loading.FMLEnvironment;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.ImmutableMap;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class ClientContentApiImpl extends ContentApiImpl {

    private final ImmutableMap<StackType<?, ?>, StackRenderer<?, ?>> stackRenderers;

    public ClientContentApiImpl(ClientContentRegistryBuilder builder) {
        super(builder);
        this.stackRenderers = Maps.immutable.ofMap(builder.stackRenderers);
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
}
