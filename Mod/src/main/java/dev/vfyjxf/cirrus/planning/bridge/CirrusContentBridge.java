package dev.vfyjxf.cirrus.planning.bridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.planning.graph.RawIngredientSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawOutputSlot;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import dev.vfyjxf.cirrus.api.recipe.content.ComplexContent;
import dev.vfyjxf.cirrus.api.recipe.content.EmptyRecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.content.TypedStackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.planning.adapter.DropReason;
import dev.vfyjxf.cirrus.planning.adapter.StackIdentity;
import dev.vfyjxf.cirrus.recipe.clause.DurabilityCostClause;
import dev.vfyjxf.cloudlib.api.util.Games;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CirrusContentBridge {

    private CirrusContentBridge() {
    }

    public record InputMapping(
            @Nullable RawIngredientSlot slot,
            @Nullable RawOutputSlot catalystReturnOutput,
            @Nullable String valueTypeId,
            double valueAmount,
            @Nullable DropReason dropReason
    ) {
        public static InputMapping empty() {
            return new InputMapping(null, null, null, 0.0, null);
        }

        public static InputMapping slot(RawIngredientSlot slot, @Nullable RawOutputSlot catalystReturn) {
            return new InputMapping(slot, catalystReturn, null, 0.0, null);
        }

        public static InputMapping value(String valueTypeId, double amount) {
            return new InputMapping(null, null, valueTypeId, amount, null);
        }

        public static InputMapping dropped(DropReason reason) {
            return new InputMapping(null, null, null, 0.0, reason);
        }
    }

    public record OutputMapping(
            @Nullable RawOutputSlot slot,
            @Nullable String valueTypeId,
            double valueAmount,
            @Nullable DropReason dropReason
    ) {
        public static OutputMapping empty() {
            return new OutputMapping(null, null, 0.0, null);
        }

        public static OutputMapping slot(RawOutputSlot slot) {
            return new OutputMapping(slot, null, 0.0, null);
        }

        public static OutputMapping value(String valueTypeId, double amount) {
            return new OutputMapping(null, valueTypeId, amount, null);
        }

        public static OutputMapping dropped(DropReason reason) {
            return new OutputMapping(null, null, 0.0, reason);
        }
    }

    public static StackIdentity.MatchFlags toMatchFlags(@Nullable MatchStrategy strategy) {
        if (strategy == null) {
            return StackIdentity.MatchFlags.IDENTITY_AND_COMPONENT;
        }
        return new StackIdentity.MatchFlags(
                strategy.withIdentity(),
                strategy.withAmount(),
                strategy.withComponent(),
                strategy.usesExactComponents()
        );
    }

    public static @Nullable JsonObject encodePatch(@Nullable DataComponentPatch patch) {
        if (patch == null || patch.isEmpty()) {
            return null;
        }
        DynamicOps<JsonElement> ops = currentJsonOps();
        JsonObject out = new JsonObject();
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            DataComponentType<?> type = entry.getKey();
            if (type == null || type.isTransient()) {
                continue;
            }
            ResourceLocation compLoc = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            String compId = compLoc != null ? compLoc.toString() : type.getClass().getName();
            Optional<?> valueOpt = entry.getValue();
            if (valueOpt.isEmpty()) {
                out.add("!" + compId, JsonNull.INSTANCE);
                continue;
            }
            Object value = valueOpt.get();
            JsonElement encoded = encodeSingleComponent(type, value, ops);
            if (encoded != null) {
                out.add(compId, encoded);
            } else {
                out.addProperty(compId, StackIdentity.unencodableFallbackToken(compId, value));
            }
        }
        return out.isEmpty() ? null : out;
    }

    public static Optional<ResourceKey> toConcreteResourceKey(@Nullable ContentStack<?, ?> stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return toConcreteResourceKeyTyped(stack);
    }

    public static Optional<ResourceKey> toResourceKey(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation loc = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (loc == null || loc.equals(BuiltInRegistries.ITEM.getDefaultKey())) {
            return Optional.empty();
        }
        return StackIdentity.mapConcrete(StackIdentity.ITEM_CONTENT_TYPE, loc.toString(), encodePatch(stack.getComponentsPatch()));
    }

    public static Optional<ResourceKey> toResourceKey(@Nullable FluidStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation loc;
        try {
            loc = BuiltInRegistries.FLUID.getKey(stack.getFluid());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        if (loc == null || loc.equals(BuiltInRegistries.FLUID.getDefaultKey())) {
            return Optional.empty();
        }
        return StackIdentity.mapConcrete(StackIdentity.FLUID_CONTENT_TYPE, loc.toString(), encodePatch(stack.getComponentsPatch()));
    }

    public static InputMapping mapInput(
            @Nullable InputContent input,
            @Nullable DurabilityCostClause durabilityClause
    ) {
        if (input == null || input.isEmpty() || input instanceof EmptyRecipeContent) {
            return InputMapping.empty();
        }
        if (!input.constantly()) {
            return InputMapping.dropped(DropReason.PROBABILISTIC);
        }
        boolean catalyst = input.notConsumed();

        if (input instanceof ValueContent<?> value) {
            String valueTypeId = value.type().typeId().toString();
            if (valueTypeId.isEmpty()) {
                return InputMapping.dropped(DropReason.INVALID_AMOUNT);
            }
            return catalyst ? InputMapping.empty() : InputMapping.value(valueTypeId, (double) value.amount());
        }

        long amount = input.amount();
        if (amount <= 0L) {
            return InputMapping.dropped(DropReason.INVALID_AMOUNT);
        }

        if (input instanceof StackContent<?, ?> stackContent) {
            StackIdentity.MatchFlags flags = toMatchFlags(stackContent.matchStrategy());
            Optional<SlotMatchMode> modeOpt = flags.toSlotMatchMode();
            if (modeOpt.isEmpty()) {
                return InputMapping.dropped(DropReason.UNSUPPORTED_MATCH_STRATEGY);
            }
            Optional<ResourceKey> keyOpt = mapStackInput(stackContent.content(), flags);
            if (keyOpt.isEmpty()) {
                return InputMapping.dropped(DropReason.INVALID_STACK);
            }
            ResourceKey key = keyOpt.get();
            SlotMatchMode mode = modeOpt.get();
            if (durabilityClause != null && durabilityClause.durabilityCost() > 0) {
                int maxDur = extractMaxDurability(stackContent.content());
                if (maxDur > 0) {
                    return InputMapping.slot(
                            new RawIngredientSlot(Optional.empty(), List.of(key), amount, mode, durabilityClause.durabilityCost(), maxDur),
                            null
                    );
                }
            }
            RawOutputSlot returnOut = catalyst ? new RawOutputSlot(key, amount) : null;
            return InputMapping.slot(new RawIngredientSlot(Optional.empty(), List.of(key), amount, mode), returnOut);
        }

        if (input instanceof TagStackContent<?> tagContent) {
            return mapTagInput(tagContent, amount, catalyst);
        }

        if (input instanceof StackListContent listContent) {
            return mapStackListInput(listContent.stacks(), amount, catalyst);
        }

        if (input instanceof TypedStackListContent<?, ?> typedList) {
            return mapStackListInput(typedList.stacks(), amount, catalyst);
        }

        if (input instanceof ComplexContent complex) {
            if (!complex.isEnumerable()) {
                return InputMapping.dropped(DropReason.COMPLEX_SLOT);
            }
            LinkedHashSet<ResourceKey> candidates = new LinkedHashSet<>();
            for (ContentVariant variant : complex.enumerate()) {
                if (!(variant instanceof ContentStack<?, ?> stack)) {
                    return InputMapping.dropped(DropReason.COMPLEX_SLOT);
                }
                Optional<ResourceKey> key = toConcreteResourceKey(stack);
                if (key.isEmpty()) {
                    return InputMapping.dropped(DropReason.INVALID_STACK);
                }
                candidates.add(key.get());
            }
            if (candidates.isEmpty()) {
                return InputMapping.dropped(DropReason.EMPTY_SLOT_CANDIDATES);
            }
            return buildListInput(Optional.empty(), List.copyOf(candidates), amount, catalyst);
        }

        return InputMapping.dropped(DropReason.UNKNOWN_KIND);
    }

    public static OutputMapping mapOutput(@Nullable OutputContent output) {
        if (output == null || output.isEmpty() || output instanceof EmptyRecipeContent) {
            return OutputMapping.empty();
        }
        if (!output.constantly()) {
            return OutputMapping.dropped(DropReason.PROBABILISTIC);
        }
        if (output.chance() == 0f) {
            return OutputMapping.empty();
        }
        if (output instanceof ValueContent<?> value) {
            String valueTypeId = value.type().typeId().toString();
            if (valueTypeId.isEmpty()) {
                return OutputMapping.dropped(DropReason.INVALID_AMOUNT);
            }
            return OutputMapping.value(valueTypeId, (double) value.amount());
        }
        if (output instanceof StackContent<?, ?> stackContent) {
            long amount = stackContent.amount();
            if (amount <= 0L) {
                return OutputMapping.dropped(DropReason.INVALID_AMOUNT);
            }
            Optional<ResourceKey> keyOpt = toConcreteResourceKey(stackContent.content());
            if (keyOpt.isEmpty()) {
                return OutputMapping.dropped(DropReason.INVALID_STACK);
            }
            return OutputMapping.slot(new RawOutputSlot(keyOpt.get(), amount));
        }
        return OutputMapping.dropped(DropReason.UNKNOWN_KIND);
    }

    public static Optional<ContentStack<?, ?>> toBestEffortContentStack(ResourceKey key, long amount) {
        if (amount <= 0L) {
            return Optional.empty();
        }
        ResourceLocation loc = ResourceLocation.tryParse(key.resourceId());
        if (loc == null) {
            return Optional.empty();
        }
        if (StackIdentity.ITEM_CONTENT_TYPE.equals(key.contentTypeId())) {
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(loc);
            if (item.isPresent()) {
                ContentStack<Item, ItemStack> stack = ContentStack.fromContent(item.get(), amount);
                return stack.isEmpty() ? Optional.empty() : Optional.of(stack);
            }
        } else if (StackIdentity.FLUID_CONTENT_TYPE.equals(key.contentTypeId())) {
            Optional<Fluid> fluid = BuiltInRegistries.FLUID.getOptional(loc);
            if (fluid.isPresent()) {
                ContentStack<Fluid, FluidStack> stack = ContentStack.fromContent(fluid.get(), amount);
                return stack.isEmpty() ? Optional.empty() : Optional.of(stack);
            }
        }
        return Optional.empty();
    }

    private static <T, S> Optional<ResourceKey> toConcreteResourceKeyTyped(ContentStack<T, S> stack) {
        String contentTypeId = stack.contentType().typeId().toString();
        ResourceLocation loc = safeLocation(stack.contentType(), stack.content());
        if (loc == null) {
            return Optional.empty();
        }
        JsonObject patchJson = encodePatch(stack.componentsPatch());
        return StackIdentity.mapConcrete(contentTypeId, loc.toString(), patchJson);
    }

    private static <T, S> Optional<ResourceKey> mapStackInput(
            ContentStack<T, S> stack,
            StackIdentity.MatchFlags flags
    ) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        String contentTypeId = stack.contentType().typeId().toString();
        ResourceLocation loc = safeLocation(stack.contentType(), stack.content());
        if (loc == null) {
            return Optional.empty();
        }
        JsonObject patchJson = flags.withComponent() ? encodePatch(stack.componentsPatch()) : null;
        return StackIdentity.mapInput(new StackIdentity.StackSpec(contentTypeId, loc.toString(), patchJson, flags)).key();
    }

    private static <T> InputMapping mapTagInput(TagStackContent<T> tag, long amount, boolean catalyst) {
        String tagId = tag.tagKey().location().toString();
        ContentType<T> contentType = tag.contentType();
        String contentTypeId = StackIdentity.normalizeContentTypeId(contentType.typeId().toString());
        if (contentTypeId.isEmpty()) {
            return InputMapping.dropped(DropReason.INVALID_STACK);
        }
        LinkedHashSet<ResourceKey> candidates = new LinkedHashSet<>();
        for (T value : tag.values()) {
            if (value == null || contentType.isEmpty(value)) {
                continue;
            }
            ResourceLocation loc = safeLocation(contentType, value);
            if (loc != null) {
                candidates.add(new ResourceKey(contentTypeId, loc.toString(), Optional.empty()));
            }
        }
        if (candidates.isEmpty()) {
            return InputMapping.dropped(DropReason.EMPTY_SLOT_CANDIDATES);
        }
        return buildListInput(Optional.of(tagId), List.copyOf(candidates), amount, catalyst);
    }

    private static InputMapping mapStackListInput(
            Iterable<? extends ContentStack<?, ?>> stacks,
            long amount,
            boolean catalyst
    ) {
        LinkedHashSet<ResourceKey> candidates = new LinkedHashSet<>();
        for (ContentStack<?, ?> stack : stacks) {
            Optional<ResourceKey> key = toConcreteResourceKey(stack);
            if (key.isPresent()) {
                candidates.add(key.get());
            }
        }
        if (candidates.isEmpty()) {
            return InputMapping.dropped(DropReason.EMPTY_SLOT_CANDIDATES);
        }
        return buildListInput(Optional.empty(), List.copyOf(candidates), amount, catalyst);
    }

    private static InputMapping buildListInput(
            Optional<String> tagId,
            List<ResourceKey> candidates,
            long amount,
            boolean catalyst
    ) {
        SlotMatchMode mode = StackIdentity.listSlotMatchMode(candidates);
        if (catalyst) {
            if (candidates.size() == 1) {
                ResourceKey only = candidates.get(0);
                return InputMapping.slot(
                        new RawIngredientSlot(Optional.empty(), candidates, amount, mode),
                        new RawOutputSlot(only, amount)
                );
            }
            return InputMapping.slot(new RawIngredientSlot(tagId, candidates, 0L, mode, 0, 1), null);
        }
        return InputMapping.slot(new RawIngredientSlot(tagId, candidates, amount, mode), null);
    }

    private static int extractMaxDurability(ContentStack<?, ?> stack) {
        try {
            if (stack.hasComponent(DataComponents.MAX_DAMAGE)) {
                Integer max = stack.getComponent(DataComponents.MAX_DAMAGE);
                return max == null ? 0 : Math.max(0, max);
            }
        } catch (RuntimeException ignored) {
        }
        return 0;
    }

    private static <T> @Nullable ResourceLocation safeLocation(ContentType<T> type, T content) {
        try {
            return type.getLocation(content);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static DynamicOps<JsonElement> currentJsonOps() {
        try {
            RegistryAccess access = Games.registryAccess();
            if (access != null) {
                return RegistryOps.create(JsonOps.INSTANCE, access);
            }
        } catch (RuntimeException ignored) {
        }
        return JsonOps.INSTANCE;
    }

    @SuppressWarnings("unchecked")
    private static <C> @Nullable JsonElement encodeSingleComponent(
            DataComponentType<C> type,
            Object rawValue,
            DynamicOps<JsonElement> ops
    ) {
        Codec<C> codec = type.codec();
        if (codec == null) {
            return null;
        }
        try {
            return codec.encodeStart(ops, (C) rawValue).result().orElse(null);
        } catch (RuntimeException ignored) {
            if (ops != JsonOps.INSTANCE) {
                try {
                    return codec.encodeStart(JsonOps.INSTANCE, (C) rawValue).result().orElse(null);
                } catch (RuntimeException ignoredAgain) {
                    return null;
                }
            }
            return null;
        }
    }
}
