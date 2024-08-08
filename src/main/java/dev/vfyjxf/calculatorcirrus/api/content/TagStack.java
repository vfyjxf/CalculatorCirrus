package dev.vfyjxf.calculatorcirrus.api.content;

import net.minecraft.tags.TagKey;

import java.util.List;

/**
 * When handling recipe,only a tag with amount is useful for us.
 */
public interface TagStack<STACK, VALUE> extends ContentStack<STACK> {

    TagType<STACK, VALUE> tagType();

    int getAmount();

    void setAmount(int amount);

    List<VALUE> getValues();

    TagKey<VALUE> getTag();

    /**
     * @return make any stack with a random value and a default amount.
     */
    @Override
    STACK value();
}
