package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.type.ContentRenderer;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.utils.Checks;
import net.neoforged.fml.loading.FMLEnvironment;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.ImmutableMap;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class ClientContentApiImpl extends ContentApiImpl {

    private final ImmutableMap<ContentType<?>, ContentRenderer<?>> contentRenderers;

    public ClientContentApiImpl(ClientContentRegistryBuilder builder) {
        super(builder);
        this.contentRenderers = Maps.immutable.ofMap(builder.contentRenderers);
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
