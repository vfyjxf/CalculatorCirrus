package dev.vfyjxf.cirrus.planning.bridge;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventoryTier;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageApi;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSource;
import dev.vfyjxf.cirrus.planning.core.lang.SafeMath;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Captures player + Cirrus {@link StorageApi} providers into a 4-tier {@link InventorySnapshot}
 * (hand, network, nearby, remote) preserving accessibility semantics instead of flattening.
 */
public final class StorageInventorySnapshotAdapter {

    private StorageInventorySnapshotAdapter() {}

    public static InventorySnapshot capture(@Nullable Player player) {
        return capture(player, StockLedger.empty);
    }

    public static InventorySnapshot capture(@Nullable Player player, StockLedger reserved) {
        return capture(player, reserved, Set.of());
    }

    public static InventorySnapshot capture(
        @Nullable Player player,
        StockLedger reserved,
        Set<ComponentStackKey> protectedFromGenericTags
    ) {
        Objects.requireNonNull(reserved, "reserved");
        List<ItemStack> playerStacks = collectPlayerStacks(player);
        Iterable<? extends StorageSource> providers = safeProviders();
        if (player != null) {
            List<StorageSource> nonDuplicateProviders = new ArrayList<>();
            for (StorageSource source : providers) {
                if (source == null) {
                    continue;
                }
                if (Objects.equals(source.identifier(), player)) {
                    continue;
                }
                nonDuplicateProviders.add(source);
            }
            providers = nonDuplicateProviders;
        }
        return fromSources(playerStacks, providers, reserved, protectedFromGenericTags);
    }

    public static InventorySnapshot fromSources(
        Iterable<ItemStack> playerStacks,
        Iterable<? extends StorageSource> storageProviders,
        StockLedger reserved
    ) {
        return fromSources(playerStacks, storageProviders, reserved, Set.of());
    }

    public static InventorySnapshot fromSources(
        Iterable<ItemStack> playerStacks,
        Iterable<? extends StorageSource> storageProviders,
        StockLedger reserved,
        Set<ComponentStackKey> protectedFromGenericTags
    ) {
        Objects.requireNonNull(reserved, "reserved");
        EnumMap<InventoryTier, Map<ComponentStackKey, Long>> byTier = new EnumMap<>(InventoryTier.class);
        EnumSet<InventoryTier> openTiers = EnumSet.of(InventoryTier.hand);

        if (playerStacks != null) {
            Map<ComponentStackKey, Long> handMap = byTier.computeIfAbsent(InventoryTier.hand, k -> new HashMap<>());
            for (ItemStack stack : playerStacks) {
                if (stack == null || stack.isEmpty() || stack.getCount() <= 0) {
                    continue;
                }
                ComponentStackKey.fromItemStack(stack)
                        .ifPresent(key -> handMap.merge(key, (long) stack.getCount(), SafeMath::addSaturating));
            }
        }

        if (storageProviders != null) {
            for (StorageSource source : storageProviders) {
                if (source == null) {
                    continue;
                }
                try {
                    if (!source.canProvide()) {
                        continue;
                    }
                    InventoryTier tier = classifyTier(source);
                    if (source.accessibility().accessible() && tier == InventoryTier.network) {
                        openTiers.add(InventoryTier.network);
                    }
                    Map<ComponentStackKey, Long> tierMap = byTier.computeIfAbsent(tier, k -> new HashMap<>());
                    for (ContentVariant variant : source.contents()) {
                        if (!(variant instanceof ContentStack<?, ?> contentStack)) {
                            continue;
                        }
                        if (contentStack.isEmpty() || contentStack.amount() <= 0L) {
                            continue;
                        }
                        ComponentStackKey key = ComponentStackKey.ofNormalized(contentStack);
                        tierMap.merge(key, contentStack.amount(), SafeMath::addSaturating);
                    }
                } catch (RuntimeException | LinkageError ignored) {}
            }
        }

        EnumMap<InventoryTier, StockLedger> availLedgers = new EnumMap<>(InventoryTier.class);
        for (var e : byTier.entrySet()) {
            if (!e.getValue().isEmpty()) {
                availLedgers.put(e.getKey(), new StockLedger(e.getValue()));
            }
        }
        Map<InventoryTier, StockLedger> resLedgers = reserved.isEmpty()
                ? Map.of()
                : Map.of(InventoryTier.hand, reserved);
        return InventorySnapshot.fromTiers(availLedgers, resLedgers, openTiers, protectedFromGenericTags);
    }

    private static InventoryTier classifyTier(StorageSource source) {
        if (!source.accessibility().accessible()) {
            return InventoryTier.remote;
        }
        String typeId = source.type() != null && source.type().id() != null
                ? source.type().id().toString().toLowerCase(java.util.Locale.ROOT)
                : "";
        if (typeId.contains("player") || typeId.contains("inventory")) {
            return InventoryTier.hand;
        }
        if (typeId.contains("network") || typeId.contains("ae2") || typeId.contains("refined") || typeId.contains("terminal")) {
            return InventoryTier.network;
        }
        return InventoryTier.nearby;
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
