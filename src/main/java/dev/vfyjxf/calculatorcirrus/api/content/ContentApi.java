package dev.vfyjxf.calculatorcirrus.api.content;

import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
import dev.vfyjxf.calculatorcirrus.utils.Singletons;
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

    boolean isRegistered(ResourceLocation typeId);

    <T> ContentType<T> getContentType(ResourceLocation typeId);

    default @Nullable <T> ContentType<T> getContentType(String typeId) {
        if (ResourceLocation.isValidPath(typeId)) {
            return getContentType(ResourceLocation.fromNamespaceAndPath(ContentType.namespace, typeId));
        }
        return getContentType(ResourceLocation.parse(typeId));
    }
    //endregion

    //region stack type

    <T> StackContentType<T> getStackType(ResourceLocation typeId);

    default @Nullable <T> StackContentType<T> getStackType(String typeId) {
        if (ResourceLocation.isValidPath(typeId)) {
            return getStackType(ResourceLocation.fromNamespaceAndPath(ContentType.namespace, typeId));
        }
        return getStackType(ResourceLocation.parse(typeId));
    }
    //endregion

    //region atomic type
//
//    <T> AtomicContentType<T> getAtomicType(ResourceLocation typeId);
//
//    default @Nullable <T> AtomicContentType<T> getAtomicType(String typeId) {
//        if (ResourceLocation.isValidPath(typeId)) {
//            return getAtomicType(ResourceLocation.fromNamespaceAndPath(ContentType.namespace, typeId));
//        }
//        return getAtomicType(ResourceLocation.parse(typeId));
//    }

    //endregion

    //region tag type

    boolean isTagRegistered(ResourceLocation typeId);

    <STACK, VALUE> TagType<VALUE, STACK> getTagType(ResourceLocation typeId);

    <STACK, VALUE> TagType<VALUE, STACK> getTagType(ContentType<STACK> stackType);

    //endregion

    @UnmodifiableView
    RichIterable<? extends ContentType<?>> allContentTypes();

    @UnmodifiableView
    RichIterable<? extends TagType<?, ?>> allTagTypes();

}
