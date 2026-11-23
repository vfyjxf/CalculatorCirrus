package dev.vfyjxf.cirrus.planning.bridge;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageApi;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSource;
import dev.vfyjxf.cirrus.planning.core.lang.SafeMath;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class StorageInventorySnapshotAdapter {

    private StorageInventorySnapshotAdapter() {
    }

    public static InventorySnapshot capture(@Nullable Player player) {
        return capture(player, StockLedger.EMPTY);
    }

    public static InventorySnapshot capture(@Nullable Player player, StockLedger reserved) {
        Objects.requireNonNull(reserved, "reserved");
        List<ItemStack> playerStacks = collectPlayerStacks(player);
        Iterable<? extends StorageSource> providers = safeProviders();
        return fromSources(playerStacks, providers, reserved);
    }

    public static InventorySnapshot fromSources(
            Iterable<ItemStack> playerStacks,
            Iterable<? extends StorageSource> storageProviders,
            StockLedger reserved
    ) {
        Objects.requireNonNull(reserved, "reserved");
        Map<ResourceKey, Long> available = new HashMap<>();

        if (playerStacks != null) {
            for (ItemStack stack : playerStacks) {
                if (stack == null || stack.isEmpty() || stack.getCount() <= 0) {
                    continue;
                }
                CirrusContentBridge.toResourceKey(stack).ifPresent(key ->
                        available.merge(key, (long) stack.getCount(), SafeMath::addSaturating));
            }
        }

        if (storageProviders != null) {
            for (StorageSource source : storageProviders) {
                if (source == null) {
                    continue;
                }
                try {
                    if (!source.canProvide() || !source.accessibility().accessible()) {
                        continue;
                    }
                    for (ContentVariant variant : source.contents()) {
                        if (!(variant instanceof ContentStack<?, ?> contentStack)) {
                            continue;
                        }
                        if (contentStack.isEmpty() || contentStack.amount() <= 0L) {
                            continue;
                        }
                        CirrusContentBridge.toConcreteResourceKey(contentStack).ifPresent(key ->
                                available.merge(key, contentStack.amount(), SafeMath::addSaturating));
                    }
                } catch (RuntimeException | LinkageError ignored) {
                }
            }
        }

        StockLedger availableLedger = available.isEmpty() ? StockLedger.EMPTY : new StockLedger(available);
        return new InventorySnapshot(availableLedger, reserved);
    }

    private static List<ItemStack> collectPlayerStacks(@Nullable Player player) {
        if (player == null) {
            return List.of();
        }
        Inventory inv = player.getInventory();
        if (inv == null) {
            return List.of();
        }
        List<ItemStack> stacks = new ArrayList<>(inv.items.size() + inv.offhand.size());
        stacks.addAll(inv.items);
        stacks.addAll(inv.offhand);
        return stacks;
    }

    private static Iterable<? extends StorageSource> safeProviders() {
        try {
            return StorageApi.instance().providers();
        } catch (RuntimeException | LinkageError ignored) {
            return List.of();
        }
    }
}
