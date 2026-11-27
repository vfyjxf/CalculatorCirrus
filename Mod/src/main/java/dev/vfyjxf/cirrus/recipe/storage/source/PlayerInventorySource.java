package dev.vfyjxf.cirrus.recipe.storage.source;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSource;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSourceType;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;

public class PlayerInventorySource implements StorageSource {

    private static final Codec<PlayerInventorySource> codec = RecordCodecBuilder.create(
        instance -> instance
                .group(Codec.STRING.fieldOf("player").forGetter(source -> source.inventory.player.getStringUUID()))
                .apply(instance, uuid -> {
                    for (AbstractClientPlayer player : Minecraft.getInstance().level.players()) {
                        if (player.getStringUUID().equals(uuid))
                            return new PlayerInventorySource(player.getInventory());
                    }
                    return null;
                })
    );

    public static final StorageSourceType<PlayerInventorySource> type = new StorageSourceType<>(
        CirrusNamespaces.ofMod("player_inventory"),
        codec
    );

    private final Inventory inventory;

    public PlayerInventorySource(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public StorageSourceType<?> type() {
        return type;
    }

    @Override
    public Object identifier() {
        return inventory.player;
    }

    @Override
    public RichIterable<? extends ContentVariant> contents() {
        MutableList<ContentVariant> contents = Lists.mutable.empty();

        for (ItemStack stack : inventory.items) {
            if (!stack.isEmpty()) {
                contents.add(ContentStack.fromStack(stack));
            }
        }
        if (!inventory.offhand.getFirst().isEmpty()) {
            contents.add(ContentStack.fromStack(inventory.offhand.getFirst()));
        }
        return contents;
    }
}
