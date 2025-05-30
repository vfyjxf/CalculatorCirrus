package dev.vfyjxf.calculatorcirrus.content.types;

import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.value.ContentStack;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

public class ItemStackType extends BasicStackContentType<ItemStack> {

    public static final ItemStackType INSTANCE = new ItemStackType();
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ContentType.namespace, "item_stack");

    private ItemStackType() {
        super(ItemStack.class, Item.class);
    }

    @Override
    public ResourceLocation typeId() {
        return ID;
    }

    @Override
    public @Nullable ResourceLocation getLocation(ItemStack value) {
        return BuiltInRegistries.ITEM.getResourceKey(value.getItem())
                .map(ResourceKey::location)
                .orElseThrow(() -> new IllegalArgumentException("Unknown item " + value.getItem()));
    }

    @Override
    public @Nullable String getNamespace(ItemStack value) {
        Item item = value.getItem();
        String modId = item.getCreatorModId(value);
        if (modId == null) {
            modId = BuiltInRegistries.ITEM.getResourceKey(item)
                    .map(ResourceKey::location)
                    .map(ResourceLocation::getNamespace)
                    .orElse("Unknown");
        }
        return modId;
    }

    @Override
    public boolean isEmpty(ItemStack value) {
        return value.isEmpty();
    }

    @Override
    public Object getIdentity(ItemStack content) {
        return content.getItem();
    }

    @Override
    public boolean isEmpty(ContentStack<ItemStack> content, ItemStack value) {
        return content.amount() <= 0 || value.isEmpty();
    }

    @Override
    public ItemStack create(ContentStack<ItemStack> content, long amount, DataComponentPatch componentPatch) {
        if (amount > Integer.MAX_VALUE) {
            amount = Integer.MAX_VALUE;
        }
        return new ItemStack(content.value().getItemHolder(), (int) amount, componentPatch);
    }


    @Override
    public ItemStack copy(ItemStack value) {
        return value.copy();
    }

    @Override
    public ItemStack normalize(ItemStack value) {
        // Temporarily setting the count on the original stack this way can "recover" some empty ItemStacks.
        // Copying it first results in the copy being a hard-coded ItemStack#EMPTY that cannot be recovered.
        int originalCount = value.getCount();
        value.setCount(1);
        ItemStack copy = value.copy();
        value.setCount(originalCount);
        return copy;
    }

    @Override
    public ItemStack wildcard(ItemStack value) {
        return new ItemStack(value.getItem(), 1);
    }

    @Override
    public DataComponentMap getComponents(ItemStack value) {
        return value.getComponents();
    }

    @Override
    public DataComponentPatch getComponentsPatch(ItemStack value) {
        return value.getComponentsPatch();
    }

    @Override
    public long getAmount(ContentStack<ItemStack> content, ItemStack value) {
        return value.getCount();
    }

    @Override
    public long getAmount(ItemStack value) {
        return value.getCount();
    }

    @Nullable
    @Override
    public ItemStack merge(ItemStack value1, ItemStack value2, MatchStrategy matchStrategy) {
        if (matches(value1, value2, matchStrategy)) {
            ItemStack result = value1.copy();
            result.grow(value2.getCount());
            return result;
        }
        return null;
    }

    @Override
    public boolean matches(ItemStack value1, ItemStack value2, MatchStrategy strategy) {
        if (value1 == value2) return true;
        boolean identity = !strategy.withIdentity() || value1.getItem() == value2.getItem();
        boolean amount = !strategy.withAmount() || value1.getCount() == value2.getCount();
        boolean components = !strategy.withComponent() || strategy.testComponents(value1.getComponents(), value2.getComponents());
        return identity && amount && components;
    }

    @Override
    public int hash(ContentStack<ItemStack> content, ItemStack value, MatchStrategy strategy) {
        if (value.isEmpty()) return ItemStack.EMPTY.hashCode();
        int hashcode = value.hashCode();
        hashcode = strategy.withAmount() ? 31 * hashcode + Long.hashCode(content.amount()) : hashcode;
        hashcode = strategy.withComponent() ? 31 * hashcode + value.getComponents().hashCode() : hashcode;
        return hashcode;
    }

    @Override
    public int hash(ItemStack value, MatchStrategy strategy) {
        if (value.isEmpty()) return ItemStack.EMPTY.hashCode();
        int hashcode = value.hashCode();
        hashcode = strategy.withAmount() ? 31 * hashcode + value.getCount() : hashcode;
        hashcode = strategy.withComponent() ? 31 * hashcode + value.getComponents().hashCode() : hashcode;
        return hashcode;
    }

    @Override
    public long maxAmount() {
        return Integer.MAX_VALUE;
    }

    @SuppressWarnings("deprecation")
    @Override
    public Stream<? extends TagKey<?>> getTags(ItemStack value) {
        Stream<? extends TagKey<?>> tags = value.getTags();
        if (value.getItem() instanceof BlockItem blockItem) {
            tags = Stream.concat(tags, blockItem.getBlock().builtInRegistryHolder().tags());
        }
        return tags;
    }

    @Override
    public ItemStack cheatStack(ItemStack value) {
        return value.copy();
    }

    @Override
    public CompoundTag save(ContentStack<ItemStack> stack, ItemStack value) {
        return (CompoundTag) ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
    }

    @Override
    public @Nullable ItemStack read(CompoundTag tag) {
        return ItemStack.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    @Override
    public @Nullable ItemStack cheatStack(ContentStack<ItemStack> content, ItemStack value) {
        return value.copy();
    }

    @Override
    public String toString() {
        return "item_stack";
    }
}
