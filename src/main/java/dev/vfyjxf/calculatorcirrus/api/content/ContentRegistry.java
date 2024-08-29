package dev.vfyjxf.calculatorcirrus.api.content;

import dev.vfyjxf.calculatorcirrus.utils.Singletons;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

@ApiStatus.NonExtendable
public interface ContentRegistry {

    static ContentRegistry getInstance() {
        return Singletons.get(ContentRegistry.class);
    }

    <T> ContentType<T> getContentType(ResourceLocation uniqueId);

    default @Nullable <T> ContentType<T> getContentType(String uniqueId) {
        if (ResourceLocation.isValidPath(uniqueId)) {
            return getContentType(ResourceLocation.fromNamespaceAndPath(ContentType.namespace, uniqueId));
        }
        return getContentType(ResourceLocation.parse(uniqueId));
    }

    <STACK, VALUE> TagType<STACK, VALUE> getTagType(ContentType<STACK> stackType);

    @UnmodifiableView
    RichIterable<? extends ContentType<?>> allContentTypes();

    @UnmodifiableView
    RichIterable<? extends TagType<?, ?>> allTagTypes();

}
