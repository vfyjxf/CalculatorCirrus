package dev.vfyjxf.cirrus.api.recipe.value;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.collector.Collectors2;

import java.util.Collection;
import java.util.Objects;

public record ItemDamage(ImmutableList<Item> items, int damagePerRecipe, int maxDamage) {

    static ItemDamage fromTag(TagKey<Item> tag, int damage, int maxDamage) throws IllegalArgumentException {
        Checks.checkNotNull(tag, "tag");
        var tagValues = CirrusTypes.Contents.itemTag.getTagValues(tag);
        var immutable = tagValues.toImmutableList();
        return new ItemDamage(immutable, damage, maxDamage);
    }

    static ItemDamage fromItem(Item item, int damage, int maxDamage) throws IllegalArgumentException {
        Checks.checkNotNull(item, "item");
        return new ItemDamage(Lists.immutable.of(item), damage, maxDamage);
    }

    static ItemDamage fromItemList(Collection<? extends Item> items, int damage, int maxDamage) {
        Checks.checkNotNull(items, "items");
        ImmutableList<Item> values = items.stream().filter(Objects::nonNull).filter(item -> item != Items.AIR)
                .collect(Collectors2.toImmutableList());
        return new ItemDamage(values, damage, maxDamage);
    }

    public boolean valid() {
        return damagePerRecipe > 0 && !items.isEmpty() && items.noneSatisfy(it -> it == null || it == Items.AIR);
    }

}
