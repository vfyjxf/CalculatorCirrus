package dev.vfyjxf.calculatorcirrus.api.content.type;

import net.minecraft.resources.ResourceLocation;

public interface ContentProperty<T> {

    ResourceLocation id();

    Class<T> type();


    interface WithDefaults<T> extends ContentProperty<T> {

        /**
         * @return the default value of this property
         */
        T defaultValue();

        /**
         * @return whether this property is required to be set
         */
        boolean isRequired();

        /**
         * @return whether this property can be overridden by a tag
         */
        boolean isOverridable();
    }
}
