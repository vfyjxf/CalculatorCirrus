//package dev.vfyjxf.calculatorcirrus.recipe.ingredient;
//
//import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
//import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
//import dev.vfyjxf.calculatorcirrus.api.content.value.TagStack;
//import dev.vfyjxf.calculatorcirrus.api.content.type.TagType;
//import dev.vfyjxf.calculatorcirrus.api.recipe.ingredient.IngredientStack;
//import net.minecraft.resources.ResourceLocation;
//import net.minecraft.tags.TagKey;
//import org.eclipse.collections.api.list.MutableList;
//import org.jetbrains.annotations.ApiStatus;
//
//@ApiStatus.Internal
//public class TagIngredientStack implements IngredientStack {
//
//    private final TagStack<?> internalStack;
//    private final TagType<?> type;
//    private final TagKey<?> tagKey;
//    private final ResourceLocation location;
//    private long amount;
//    private float chance;
//
//    public <T> TagIngredientStack(TagType<T> type, TagKey<T> tagKey) {
//        this(type, tagKey, 1, MAX_CHANCE);
//    }
//
//    public <T> TagIngredientStack(TagType<T> type, TagKey<T> tagKey, long amount) {
//        this(type, tagKey, amount, MAX_CHANCE);
//    }
//
//    public <T> TagIngredientStack(TagType<T> type, TagKey<T> tagKey, long amount, float chance) {
//        this.internalStack = TagStack.create(type, tagKey);
//        this.type = type;
//        this.tagKey = tagKey;
//        this.amount = amount;
//        this.chance = chance;
//        this.location = tagKey.location();
//    }
//
//    public TagType<?> getType() {
//        return type;
//    }
//
//    public TagKey<?> getTagKey() {
//        return internalStack.tagKey();
//    }
//
//    public TagStack<?> getInternalStack() {
//        return internalStack;
//    }
//
//    @Override
//    public IngredientStack copy() {
//        return new TagIngredientStack(type.cast(), tagKey, amount, chance);
//    }
//
//    @Override
//    public MutableList<? extends ContentStack<?>> getStacks() {
//        return internalStack.getAsStacks();
//    }
//
//    @Override
//    public float getChance() {
//        return chance;
//    }
//
//    @Override
//    public IngredientStack setChance(float chance) {
//        this.chance = chance;
//        return this;
//    }
//
//    @Override
//    public long getAmount() {
//        return amount;
//    }
//
//    @Override
//    public IngredientStack setAmount(long amount) {
//        this.amount = amount;
//        return this;
//    }
//
//    @Override
//    public boolean matches(IngredientStack stack, MatchStrategy strategy) {
//        if (stack instanceof TagIngredientStack tagStack) {
//            return tagStack.type == type && tagStack.tagKey.equals(tagKey);
//        }
//        return false;
//    }
//
//    @Override
//    public boolean equals(Object o) {
//        if (this == o) return true;
//        if (o == null || getClass() != o.getClass()) return false;
//
//        TagIngredientStack that = (TagIngredientStack) o;
//        return amount == that.amount && Float.compare(chance, that.chance) == 0 && type.equals(that.type) && tagKey.equals(that.tagKey);
//    }
//
//    @Override
//    public int hashCode() {
//        int result = type.hashCode();
//        result = 31 * result + tagKey.hashCode();
//        return result;
//    }
//
//    @Override
//    public String toString() {
//        return "TagIngredientStack{" +
//                "type=" + type +
//                ", tagKey=" + tagKey +
//                ", location=" + location +
//                ", amount=" + amount +
//                ", chance=" + chance +
//                '}';
//    }
//}
