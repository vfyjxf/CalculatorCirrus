package dev.vfyjxf.calculatorcirrus.content.types;

import dev.vfyjxf.calculatorcirrus.api.content.IContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.IContentType;
import dev.vfyjxf.calculatorcirrus.api.content.MatchContext;
import dev.vfyjxf.calculatorcirrus.content.renderer.ItemStackContentRenderer;
import net.minecraft.core.component.DataComponentMap;
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

import java.util.Collection;
import java.util.stream.Stream;

public class ItemStackType extends AbstractContentType<ItemStack> {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(namespace, "item_stack");

    public ItemStackType() {
        super(ItemStack.class, new ItemStackContentRenderer());
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public boolean supportTag() {
        return true;
    }

    @Override
    public @Nullable String getUid(IContentStack<ItemStack> content, ItemStack value) {
        return BuiltInRegistries.ITEM.getResourceKey(value.getItem())
                .map(ResourceKey::location)
                .map(ResourceLocation::toString)
                .orElseThrow(() -> new IllegalArgumentException("Unknown item " + value.getItem()));
    }

    @Override
    public @Nullable String getModid(IContentStack<ItemStack> content, ItemStack value) {
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
    public boolean isEmpty(IContentStack<ItemStack> content, ItemStack value) {
        return value.isEmpty();
    }

    @Override
    public ItemStack copy(IContentStack<ItemStack> content, ItemStack value) {
        return value.copy();
    }

    @Override
    public ItemStack normalize(IContentStack<ItemStack> content, ItemStack value) {
        ItemStack result = value.copy();
        result.setCount(1);
        return result;
    }

    @Override
    public ItemStack wildcard(IContentStack<ItemStack> entry, ItemStack value) {
        return new ItemStack(value.getItem(), 1);
    }

    @Override
    public long getAmount(IContentStack<ItemStack> content, ItemStack value) {
        return value.getCount();
    }

    @Override
    public long getAmount(ItemStack value) {
        return value.getCount();
    }

    @Override
    public @Nullable DataComponentMap getComponents(IContentStack<ItemStack> content, ItemStack value) {
        return value.getComponents();
    }

    @Nullable
    @Override
    public ItemStack merge(ItemStack value1, ItemStack value2, MatchContext matchContext) {
        if (matches(value1, value2, matchContext)) {
            ItemStack result = value1.copy();
            result.grow(value2.getCount());
            return result;
        }
        return null;
    }

    @Override
    public boolean matches(ItemStack value1, ItemStack value2, MatchContext matchContext) {
        if (matchContext.exact()) {
            return matchContext.withCount() ? ItemStack.matches(value1, value2) : ItemStack.isSameItemSameComponents(value1, value2);
        } else {
            boolean itemEqual = ItemStack.isSameItem(value1, value2);
            return matchContext.withCount() ? itemEqual && value1.getCount() == value2.getCount() : itemEqual;
        }
    }

    @Override
    public long hash(IContentStack<ItemStack> content, ItemStack value, boolean fuzzy) {
        if (fuzzy) {
            CompoundTag serialized = save(content, value).getCompound("tag");
            serialized.remove("tag");
            return serialized.hashCode();
        }
        return this.save(content, value).hashCode();
    }

    @Override
    public Stream<? extends TagKey<?>> getTagsFor(IContentStack<ItemStack> content, ItemStack value) {
        Stream<? extends TagKey<?>> tags = value.getTags();
        if (value.getItem() instanceof BlockItem blockItem) {
            tags = Stream.concat(tags, blockItem.getBlock().builtInRegistryHolder().tags());
        }
        return tags;
    }

    @Override
    public CompoundTag save(IContentStack<ItemStack> stack, ItemStack value) {
        return (CompoundTag) ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
    }

    @Override
    public @Nullable ItemStack read(CompoundTag tag) {
        return ItemStack.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    @Override
    public @Nullable ItemStack cheatStack(IContentStack<ItemStack> content, ItemStack value) {
        return value.copy();
    }

    public static class ItemTagType extends AbstractTagType<ItemStack, Item> {

        private final static ResourceLocation IDENTIFIER = ResourceLocation.parse("tag:item");

        public ItemTagType(IContentType<ItemStack> type) {
            super(type, Item.class, BuiltInRegistries.ITEM);
        }

        @Override
        public ResourceLocation getId() {
            return IDENTIFIER;
        }

        @Override
        public Item getValue(ItemStack itemStack) {
            return itemStack.getItem();
        }

        @Override
        public ItemStack makeStack(Item item) {
            return new ItemStack(item);
        }

        @Override
        public ItemStack makeStackWithAmount(Item item, int amount) {
            return new ItemStack(item, amount);
        }

        @Override
        public Stream<? extends TagKey<Item>> getTags(Collection<ItemStack> collection) {
            return null;
        }
    }

}
