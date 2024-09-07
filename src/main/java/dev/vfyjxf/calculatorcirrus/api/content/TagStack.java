package dev.vfyjxf.calculatorcirrus.api.content;

import net.minecraft.tags.TagKey;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Unmodifiable;

/**
 * When handling recipe,only a tag with amount is useful for us.
 */
public interface TagStack<STACK, VALUE> extends ContentStack<STACK> {

    TagType<STACK, VALUE> tagType();

    @Unmodifiable
    MutableList<? extends ContentStack<STACK>> getValues();

    TagKey<VALUE> getTag();

    @Override
    TagStack<STACK, VALUE> copy();

    /**
     * @return make any stack with a random value and a default amount.
     */
    @Override
    STACK value();
}
