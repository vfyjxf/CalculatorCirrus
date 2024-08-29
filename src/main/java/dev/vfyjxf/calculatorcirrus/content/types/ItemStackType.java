package dev.vfyjxf.calculatorcirrus.content.types;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.MatchContext;
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

public class ItemStackType extends BasicContentType<ItemStack> {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(namespace, "item_stack");

    public ItemStackType() {
        super(ItemStack.class, Item.class);
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }


    @Override
    public Object getBase(ItemStack content) {
        return content.getItem();
    }

    @Override
    public @Nullable String getUid(ContentStack<ItemStack> content, ItemStack value) {
        return BuiltInRegistries.ITEM.getResourceKey(value.getItem())
                .map(ResourceKey::location)
                .map(ResourceLocation::toString)
                .orElseThrow(() -> new IllegalArgumentException("Unknown item " + value.getItem()));
    }

    @Override
    public @Nullable String getModid(ContentStack<ItemStack> content, ItemStack value) {
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
    public boolean isEmpty(ContentStack<ItemStack> content, ItemStack value) {
        return value.isEmpty();
    }

    @Override
    public ItemStack create(ContentStack<ItemStack> content, DataComponentPatch components, long amount) {
        return new ItemStack(content.value().getItemHolder(), (int) amount, components);
    }

    @Override
    public ItemStack copy(ContentStack<ItemStack> content, ItemStack value) {
        return value.copy();
    }

    @Override
    public ItemStack normalize(ContentStack<ItemStack> content, ItemStack value) {
        ItemStack result = value.copy();
        result.setCount(1);
        return result;
    }

    @Override
    public ItemStack wildcard(ContentStack<ItemStack> entry, ItemStack value) {
        return new ItemStack(value.getItem(), 1);
    }

    @Override
    public long getAmount(ContentStack<ItemStack> content, ItemStack value) {
        return value.getCount();
    }

    @Override
    public long getAmount(ItemStack value) {
        return value.getCount();
    }

    @Override
    public @Nullable DataComponentMap getComponents(ContentStack<ItemStack> content, ItemStack value) {
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
            return ItemStack.isSameItemSameComponents(value1, value2);
        } else {
            return ItemStack.isSameItem(value1, value2);
        }
    }

    @Override
    public long hash(ContentStack<ItemStack> content, ItemStack value, boolean fuzzy) {
        //TODO:Rewrite this
        if (fuzzy) {
            CompoundTag serialized = save(content, value).getCompound("tag");
            serialized.remove("tag");
            return serialized.hashCode();
        }
        return this.save(content, value).hashCode();
    }

    @SuppressWarnings("deprecation")
    @Override
    public Stream<? extends TagKey<?>> getTagsFor(ContentStack<ItemStack> content, ItemStack value) {
        Stream<? extends TagKey<?>> tags = value.getTags();
        if (value.getItem() instanceof BlockItem blockItem) {
            tags = Stream.concat(tags, blockItem.getBlock().builtInRegistryHolder().tags());
        }
        return tags;
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

}
