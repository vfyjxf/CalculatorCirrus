package dev.vfyjxf.calculatorcirrus.recipes.ingredients;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.TagStack;
import dev.vfyjxf.calculatorcirrus.api.content.TagType;
import dev.vfyjxf.calculatorcirrus.api.content.recipes.ingredients.IngredientStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class TagIngredientStack implements IngredientStack {

    private final TagType<?, ?> type;
    private final TagKey<?> tagKey;
    private final ResourceLocation location;
    private MutableList<? extends ContentStack<?>> stacks = null;
    private long amount = 0;
    private float chance = MAX_CHANCE;

    public TagIngredientStack(TagKey<?> tagKey, TagType<?, ?> type) {
        this(type, tagKey, 1, MAX_CHANCE);
    }

    public TagIngredientStack(TagKey<?> tagKey, long amount, TagType<?, ?> type) {
        this(type, tagKey, amount, MAX_CHANCE);
    }

    public TagIngredientStack(TagType<?, ?> type, TagKey<?> tagKey, long amount, float chance) {
        this.type = type;
        this.tagKey = tagKey;
        this.amount = amount;
        this.chance = chance;
        this.location = tagKey.location();
    }

    @Override
    public MutableList<? extends ContentStack<?>> getStacks() {
        if (stacks == null) {
            TagStack<?, ?> tagStack = ContentStack.create(type.cast(), tagKey);
            stacks = tagStack.getValues();
        }
        return stacks;
    }

    @Override
    public IngredientStack setChance(float chance) {
        this.chance = chance;
        return this;
    }

    @Override
    public long getAmount() {
        return amount;
    }

    @Override
    public IngredientStack setAmount(long amount) {
        this.amount = amount;
        return this;
    }
}
