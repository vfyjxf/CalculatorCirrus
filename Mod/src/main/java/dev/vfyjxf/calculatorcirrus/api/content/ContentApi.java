package dev.vfyjxf.calculatorcirrus.api.content;

import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import dev.vfyjxf.calculatorcirrus.util.Singletons;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

@ApiStatus.NonExtendable
public interface ContentApi {

    static ContentApi getInstance() {
        return Singletons.get(ContentApi.class);
    }

    //region content type

    boolean hasContentType(ResourceLocation typeId);

    <T> ContentType<T> getContentType(ResourceLocation typeId);

    default @Nullable <T> ContentType<T> getContentType(String typeId) {
        if (ResourceLocation.isValidPath(typeId)) {
            return getContentType(ResourceLocation.fromNamespaceAndPath(ContentType.namespace, typeId));
        }
        return getContentType(ResourceLocation.parse(typeId));
    }
    //endregion

    //region stack type

    boolean hasStackType(ResourceLocation typeId);

    <T, S> StackType<T, S> getStackType(ResourceLocation typeId);

    <T, S> StackType<T, S> getStackType(ContentType<T> contentType);

    default @Nullable <T, S> StackType<T, S> getStackType(String typeId) {
        if (ResourceLocation.isValidPath(typeId)) {
            return getStackType(ResourceLocation.fromNamespaceAndPath(ContentType.namespace, typeId));
        }
        return getStackType(ResourceLocation.parse(typeId));
    }
    //endregion

    //region tag type

    boolean hasTagType(ResourceLocation typeId);

    <T> TagType<T> getTagType(ResourceLocation typeId);

    <T> TagType<T> getTagType(ContentType<T> contentType);

    //endregion

    @UnmodifiableView
    RichIterable<? extends ContentType<?>> contentTypes();

    @UnmodifiableView
    RichIterable<? extends StackType<?, ?>> stackTypes();

    @UnmodifiableView
    RichIterable<? extends TagType<?>> tagTypes();

}
