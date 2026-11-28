package dev.vfyjxf.cirrus.planning.bridge;

import dev.vfyjxf.cirrus.api.content.ContentApi;
import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.TagType;
import dev.vfyjxf.cirrus.api.content.util.TagLookup;
import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.planning.graph.RawIngredientSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawOutputSlot;
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
import dev.vfyjxf.cirrus.api.recipe.content.complex.DurabilityContent;
import dev.vfyjxf.cirrus.planning.adapter.DropReason;
import dev.vfyjxf.cloudlib.api.util.Games;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

public final class CirrusContentBridge {

    private CirrusContentBridge() {}

    public record InputMapping(
        @Nullable RawIngredientSlot slot,
        @Nullable RawOutputSlot catalystReturnOutput,
        @Nullable Namespace valueTypeId,
        double valueAmount,
        @Nullable DropReason dropReason
    ) {
        public static InputMapping empty() {
            return new InputMapping(null, null, null, 0.0, null);
        }

        public static InputMapping slot(RawIngredientSlot slot, @Nullable RawOutputSlot catalystReturn) {
            return new InputMapping(slot, catalystReturn, null, 0.0, null);
        }

        public static InputMapping value(Namespace valueTypeId, double amount) {
            return new InputMapping(null, null, valueTypeId, amount, null);
        }

        public static InputMapping dropped(DropReason reason) {
            return new InputMapping(null, null, null, 0.0, reason);
        }
    }

    public record OutputMapping(
        @Nullable RawOutputSlot slot,
        @Nullable Namespace valueTypeId,
        double valueAmount,
        @Nullable DropReason dropReason
    ) {
        public static OutputMapping empty() {
            return new OutputMapping(null, null, 0.0, null);
        }

        public static OutputMapping slot(RawOutputSlot slot) {
            return new OutputMapping(slot, null, 0.0, null);
        }

        public static OutputMapping value(Namespace valueTypeId, double amount) {
            return new OutputMapping(null, valueTypeId, amount, null);
        }

        public static OutputMapping dropped(DropReason reason) {
            return new OutputMapping(null, null, 0.0, reason);
        }
    }

    public static Optional<ComponentStackKey> toConcreteKey(@Nullable ContentStack<?, ?> stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ComponentStackKey.ofNormalized(stack));
    }

    public static Optional<ComponentStackKey> toKey(@Nullable ItemStack stack) {
        return ComponentStackKey.fromItemStack(stack);
    }

    public static Optional<ComponentStackKey> toKey(@Nullable FluidStack stack) {
        return ComponentStackKey.fromFluidStack(stack);
    }

    public static InputMapping mapInput(@Nullable InputContent input, int durabilityCost) {
        if (input == null || input.isEmpty() || input instanceof EmptyRecipeContent) {
            return InputMapping.empty();
        }
        if (!input.constantly()) {
            return InputMapping.dropped(DropReason.probabilistic);
        }
        boolean catalyst = input.notConsumed();

        if (input instanceof ValueContent<?> value) {
            Namespace valueTypeId = value.type().typeId();
            return catalyst ? InputMapping.empty() : InputMapping.value(valueTypeId, (double) value.amount());
        }

        long amount = input.amount();
        if (amount <= 0L) {
            return InputMapping.dropped(DropReason.invalidAmount);
        }

        if (input instanceof DurabilityContent durabilityContent) {
            int effectiveCost = Math.max(durabilityCost, durabilityContent.durabilityCost());
            InputMapping base = mapInput(durabilityContent.ingredient(), 0);
            if (base.dropReason() != null) {
                return base;
            }
            RawIngredientSlot baseSlot = base.slot();
            if (baseSlot == null) {
                return InputMapping.dropped(DropReason.complexSlot);
            }
            int maxDur = durabilityContent.maxDurability();
            if (maxDur > 0 && effectiveCost > 0) {
                return InputMapping.slot(
                    new RawIngredientSlot(
                        baseSlot.tagKey(),
                        baseSlot.candidates(),
                        amount,
                        baseSlot.matchMode(),
                        effectiveCost,
                        maxDur
                    ),
                    null
                );
            }
            if (catalyst) {
                return buildListInput(baseSlot.tagKey(), baseSlot.candidates(), amount, true);
            }
            return InputMapping.slot(
                new RawIngredientSlot(baseSlot.tagKey(), baseSlot.candidates(), amount, baseSlot.matchMode()),
                null
            );
        }

        if (input instanceof StackContent<?, ?> stackContent) {
            Optional<SlotMatchMode> modeOpt = toSlotMatchMode(stackContent.matchStrategy());
            if (modeOpt.isEmpty()) {
                return InputMapping.dropped(DropReason.unsupportedMatchStrategy);
            }
            ContentStack<?, ?> rawStack = stackContent.content();
            if (rawStack == null || rawStack.isEmpty()) {
                return InputMapping.dropped(DropReason.invalidStack);
            }
            SlotMatchMode mode = modeOpt.get();
            ComponentStackKey key = ComponentStackKey.ofNormalized(rawStack);
            if (mode == SlotMatchMode.identity) {
                key = key.withoutComponents();
            }
            if (durabilityCost > 0) {
                int maxDur = DurabilityContent.resolveMaxDurability(rawStack);
                if (maxDur > 0) {
                    return InputMapping.slot(
                        new RawIngredientSlot(
                            Optional.empty(),
                            List.of(key),
                            amount,
                            mode,
                            durabilityCost,
                            maxDur
                        ),
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
            return mapUntypedStackListInput(listContent, amount, catalyst);
        }

        if (input instanceof TypedStackListContent<?, ?> typedList) {
            return mapTypedStackListInput(typedList, amount, catalyst);
        }

        if (input instanceof ComplexContent complex) {
            if (!complex.isEnumerable()) {
                return InputMapping.dropped(DropReason.complexSlot);
            }
            LinkedHashSet<ComponentStackKey> candidates = new LinkedHashSet<>();
            for (ContentVariant variant : complex.enumerate()) {
                if (!(variant instanceof ContentStack<?, ?> stack)) {
                    return InputMapping.dropped(DropReason.complexSlot);
                }
                Optional<ComponentStackKey> key = toConcreteKey(stack);
                if (key.isEmpty()) {
                    return InputMapping.dropped(DropReason.invalidStack);
                }
                candidates.add(key.get());
            }
            if (candidates.isEmpty()) {
                return InputMapping.dropped(DropReason.emptySlotCandidates);
            }
            return buildListInput(Optional.empty(), List.copyOf(candidates), amount, catalyst);
        }

        return InputMapping.dropped(DropReason.unknownKind);
    }

    public static OutputMapping mapOutput(@Nullable OutputContent output) {
        if (output == null || output.isEmpty() || output instanceof EmptyRecipeContent) {
            return OutputMapping.empty();
        }
        if (output.chance() == 0f) {
            return OutputMapping.empty();
        }
        if (!output.constantly()) {
            return OutputMapping.dropped(DropReason.probabilistic);
        }
        if (output instanceof ValueContent<?> value) {
            return OutputMapping.value(value.type().typeId(), (double) value.amount());
        }
        if (output instanceof StackContent<?, ?> stackContent) {
            long amount = stackContent.amount();
            if (amount <= 0L) {
                return OutputMapping.dropped(DropReason.invalidAmount);
            }
            Optional<ComponentStackKey> keyOpt = toConcreteKey(stackContent.content());
            if (keyOpt.isEmpty()) {
                return OutputMapping.dropped(DropReason.invalidStack);
            }
            return OutputMapping.slot(new RawOutputSlot(keyOpt.get(), amount));
        }
        return OutputMapping.dropped(DropReason.unknownKind);
    }

    public static List<ComponentStackKey> enumerateRegisteredResources() {
        LinkedHashSet<ComponentStackKey> out = new LinkedHashSet<>();
        try {
            ContentApi contentApi = ContentApi.instance();
            for (ContentType<?> contentType : contentApi.contentTypes()) {
                if (!contentType.hasRegistry() || !contentApi.hasStackType(contentType)) {
                    continue;
                }
                collectRegistryResources(contentApi, contentType, out);
            }
            if (!out.isEmpty()) {
                return List.copyOf(out);
            }
        } catch (RuntimeException | LinkageError ignored) {}
        try {
            for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item != null && !item.getDefaultInstance().isEmpty()) {
                    out.add(ComponentStackKey.ofItem(item));
                }
            }
        } catch (RuntimeException | LinkageError ignored) {}
        return List.copyOf(out);
    }

    @SuppressWarnings("unchecked")
    private static <T, S> void collectRegistryResources(
        ContentApi contentApi,
        ContentType<T> contentType,
        LinkedHashSet<ComponentStackKey> out
    ) {
        Registry<T> registry = resolveRegistry(contentType);
        if (registry == null) {
            return;
        }
        StackType<T, S> stackType = (StackType<T, S>) contentApi.getStackType(contentType);
        for (T value : registry) {
            if (value == null || contentType.isEmpty(value)) {
                continue;
            }
            ContentStack<T, S> stack = ContentStack.fromContent(stackType, value, 1L);
            if (!stack.isEmpty()) {
                out.add(ComponentStackKey.ofNormalized(stack));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> @Nullable Registry<T> resolveRegistry(ContentType<T> contentType) {
        var registryKey = contentType.getRegistryKey();
        if (registryKey == null) {
            return null;
        }
        try {
            RegistryAccess access = Games.registryAccess();
            if (access != null) {
                Optional<Registry<T>> reg = access.registry(registryKey);
                if (reg.isPresent()) {
                    return reg.get();
                }
            }
        } catch (RuntimeException ignored) {}
        try {
            Registry<?> raw = BuiltInRegistries.REGISTRY.get(registryKey.location());
            if (raw != null) {
                return (Registry<T>) raw;
            }
        } catch (RuntimeException ignored) {}
        return null;
    }

    private static Optional<SlotMatchMode> toSlotMatchMode(@Nullable MatchStrategy strategy) {
        if (strategy == null) {
            return Optional.of(SlotMatchMode.identityAndComponent);
        }
        if (!strategy.withIdentity()) {
            return Optional.empty();
        }
        if (!strategy.withComponent()) {
            return Optional.of(SlotMatchMode.identity);
        }
        return Optional.of(SlotMatchMode.identityAndComponent);
    }

    @SuppressWarnings("unchecked")
    private static <T, S> InputMapping mapTagInput(TagStackContent<T> tag, long amount, boolean catalyst) {
        TagKey<T> tagKey = tag.tagKey();
        ContentType<T> contentType = tag.contentType();
        @Nullable StackType<T, S> stackType = null;
        try {
            ContentApi api = ContentApi.instance();
            if (api.hasStackType(contentType)) {
                stackType = (StackType<T, S>) api.getStackType(contentType);
            }
        } catch (RuntimeException | LinkageError ignored) {}

        LinkedHashSet<ComponentStackKey> candidates = new LinkedHashSet<>();
        for (T value : tag.values()) {
            if (value == null || contentType.isEmpty(value)) {
                continue;
            }
            if (stackType != null) {
                ContentStack<T, S> stack = ContentStack.fromContent(stackType, value, 1L);
                if (!stack.isEmpty()) {
                    candidates.add(ComponentStackKey.ofNormalized(stack));
                }
            } else if (value instanceof Item item && !item.getDefaultInstance().isEmpty()) {
                candidates.add(ComponentStackKey.ofItem(item));
            } else if (value instanceof Fluid fluid) {
                candidates.add(ComponentStackKey.ofFluid(fluid));
            }
        }
        if (candidates.isEmpty()) {
            return InputMapping.dropped(DropReason.emptySlotCandidates);
        }
        return buildListInput(Optional.of(tagKey), List.copyOf(candidates), amount, catalyst);
    }

    private static <T, S> InputMapping mapTypedStackListInput(
        TypedStackListContent<T, S> typedList,
        long amount,
        boolean catalyst
    ) {
        Optional<TagKey<?>> matchedTag = resolveTagForStacks(
            typedList.type().contentType(),
            typedList.stacks().castToList()
        );
        return mapStackListInput(matchedTag, typedList.stacks(), amount, catalyst);
    }

    @SuppressWarnings("unchecked")
    private static InputMapping mapUntypedStackListInput(StackListContent listContent, long amount, boolean catalyst) {
        Optional<TagKey<?>> matchedTag = Optional.empty();
        if (!listContent.stacks().isEmpty()) {
            ContentType<?> firstType = listContent.stacks().get(0).contentType();
            boolean homogeneous = true;
            for (ContentStack<?, ?> stack : listContent.stacks()) {
                if (stack.contentType() != firstType) {
                    homogeneous = false;
                    break;
                }
            }
            if (homogeneous) {
                matchedTag = resolveTagForUntypedStacks(firstType, listContent.stacks());
            }
        }
        return mapStackListInput(matchedTag, listContent.stacks(), amount, catalyst);
    }

    @SuppressWarnings("unchecked")
    private static <T> Optional<TagKey<?>> resolveTagForUntypedStacks(
        ContentType<T> contentType,
        Iterable<? extends ContentStack<?, ?>> stacks
    ) {
        List<ContentStack<T, ?>> typed = new ArrayList<>();
        for (ContentStack<?, ?> stack : stacks) {
            typed.add((ContentStack<T, ?>) stack);
        }
        return resolveTagForStacks(contentType, typed);
    }

    private static <T> Optional<TagKey<?>> resolveTagForStacks(
        ContentType<T> contentType,
        List<? extends ContentStack<T, ?>> stacks
    ) {
        if (stacks.isEmpty()) {
            return Optional.empty();
        }
        try {
            ContentApi contentApi = ContentApi.instance();
            if (!contentApi.hasTagType(contentType)) {
                return Optional.empty();
            }
            TagType<T> tagType = contentApi.getTagType(contentType);
            TagKey<T> matched = TagLookup.findTagMatchStacks(tagType, stacks);
            return matched == null ? Optional.empty() : Optional.of(matched);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static InputMapping mapStackListInput(
        Optional<TagKey<?>> tag,
        Iterable<? extends ContentStack<?, ?>> stacks,
        long amount,
        boolean catalyst
    ) {
        LinkedHashSet<ComponentStackKey> candidates = new LinkedHashSet<>();
        for (ContentStack<?, ?> stack : stacks) {
            Optional<ComponentStackKey> key = toConcreteKey(stack);
            if (key.isPresent()) {
                candidates.add(key.get());
            }
        }
        if (candidates.isEmpty()) {
            return InputMapping.dropped(DropReason.emptySlotCandidates);
        }
        return buildListInput(tag, List.copyOf(candidates), amount, catalyst);
    }

    private static InputMapping buildListInput(
        Optional<TagKey<?>> tag,
        List<ComponentStackKey> candidates,
        long amount,
        boolean catalyst
    ) {
        SlotMatchMode mode = listSlotMatchMode(candidates);
        if (catalyst) {
            if (candidates.size() == 1) {
                ComponentStackKey only = candidates.get(0);
                return InputMapping.slot(
                    new RawIngredientSlot(Optional.empty(), candidates, amount, mode),
                    new RawOutputSlot(only, amount)
                );
            }
            return InputMapping.slot(new RawIngredientSlot(tag, candidates, 0L, mode, 0, 1), null);
        }
        return InputMapping.slot(new RawIngredientSlot(tag, candidates, amount, mode), null);
    }

    private static SlotMatchMode listSlotMatchMode(List<ComponentStackKey> candidates) {
        for (ComponentStackKey c : candidates) {
            if (c.hasComponents()) {
                return SlotMatchMode.identityAndComponent;
            }
        }
        return SlotMatchMode.identity;
    }
}
