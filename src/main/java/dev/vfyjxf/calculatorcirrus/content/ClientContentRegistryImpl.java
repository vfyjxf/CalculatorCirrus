package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.ContentRenderer;
import dev.vfyjxf.calculatorcirrus.api.content.ContentType;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class ClientContentRegistryImpl extends ContentRegistryImpl {

    private final MutableMap<ContentType<?>, ContentRenderer<?>> contentRenderers = Maps.mutable.empty();

    @ApiStatus.Internal
    @Override
    public <T> void registerContent(ContentType<T> contentType) {
        super.registerContent(contentType);
    }

    @ApiStatus.Internal
    public <T> void registerContent(ContentType<T> contentType, ContentRenderer<T> contentRenderer) {
        this.registerContent(contentType);
        contentRenderers.put(contentType, contentRenderer);
    }

    public <T> void registerRenderer(ContentType<T> contentType, ContentRenderer<T> contentRenderer) {
        if (!super.isRegistered(contentType)) {
            throw new IllegalStateException("The content type: " + contentType.getId() + " has not been registered");
        }
        contentRenderers.put(contentType, contentRenderer);
    }

    @ApiStatus.Internal
    @SuppressWarnings("unchecked")
    public <T> ContentRenderer<T> getRenderer(ContentType<T> contentType) {
        return (ContentRenderer<T>) contentRenderers.get(contentType);
    }

}
