package dev.vfyjxf.cirrus.api.recipe.execution;

import dev.vfyjxf.cirrus.api.content.value.ContentKind;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.content.TypedStackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Formats translated interaction plans into debug-readable text.
 *
 * <p>The formatter intentionally prefers stable semantic identifiers over
 * localized names so logs stay searchable and easy to diff.
 */
public final class InteractionExecutionPlanDebugText {

    private InteractionExecutionPlanDebugText() {
    }

    //region plan

    public static String describe(@Nullable InteractionExecutionPlan plan) {
        if (plan == null) {
            return "<null interaction plan>";
        }

        MutableList<String> lines = Lists.mutable.empty();
        lines.add("interaction-plan target=" + describeRecipe(plan.targetRecipe()) + " x" + plan.targetTimes());
        lines.add("steps=" + plan.steps().size() + " capabilities=" + plan.requiredCapabilities().makeString("[", ", ", "]"));
        for (int index = 0; index < plan.steps().size(); index++) {
            lines.addAllIterable(describeLines(index, plan.steps().get(index)));
        }
        return lines.makeString(System.lineSeparator());
    }

    public static ImmutableList<String> describeLines(int index, InteractionStep step) {
        MutableList<String> lines = Lists.mutable.empty();
        String prefix = "[" + index + "] ";
        String suffix = sideCapabilitySuffix(step);
        switch (step) {
            case InteractionStep.EnsureAccessibleStep ensure ->
                lines.add(prefix + "ensure-accessible " + describeAccessTarget(ensure.target())
                    + " x" + ensure.requestedAmount() + suffix);
            case InteractionStep.WithdrawResourceStep withdraw ->
                lines.add(prefix + "withdraw " + describeWithdrawTarget(withdraw.target())
                    + " x" + withdraw.requestedAmount() + suffix);
            case InteractionStep.ReserveCatalystStep reserve ->
                lines.add(prefix + "reserve-catalyst " + describeInput(reserve.input())
                    + " x" + reserve.amount() + suffix);
            case InteractionStep.EnsureCapacityStep capacity ->
                lines.add(prefix + "ensure-capacity " + describeResource(capacity.resource())
                    + " x" + capacity.amount() + suffix);
            case InteractionStep.StoreOutputsStep store ->
                lines.add(prefix + "store-output " + describeResource(store.resource())
                    + " x" + store.amount() + suffix);
            case InteractionStep.OpenInteractionStep open ->
                lines.add(prefix + "open " + describeRecipe(open.recipe()) + " x" + open.times()
                    + " interaction=" + open.interactionId() + suffix);
            case InteractionStep.PrepareInputsStep prepare -> {
                lines.add(prefix + "prepare " + describeRecipe(prepare.recipe()) + suffix);
                lines.add("  inputs: " + describeInputs(prepare.inputs()));
            }
            case InteractionStep.ExecuteRecipeStep execute -> {
                lines.add(prefix + "execute " + describeRecipe(execute.recipe()) + " x" + execute.times() + suffix);
                lines.add("  inputs:  " + describeInputs(execute.inputs()));
                lines.add("  outputs: " + describeOutputs(execute.expectedOutputs()));
            }
            case InteractionStep.CollectOutputsStep collect -> {
                lines.add(prefix + "collect " + describeRecipe(collect.recipe()) + suffix);
                lines.add("  outputs: " + describeOutputs(collect.expectedOutputs()));
            }
            case InteractionStep.ManualCheckpointStep checkpoint ->
                lines.add(prefix + "manual-checkpoint " + describeGuidance(checkpoint.guidance()) + suffix);
        }
        return lines.toImmutable();
    }

    //endregion

    //region targets

    private static String describeAccessTarget(InteractionStep.AccessTarget target) {
        return switch (target) {
            case InteractionStep.AccessTarget.Resource resource -> describeResource(resource.resource());
            case InteractionStep.AccessTarget.Input input -> describeInput(input.input());
        };
    }

    private static String describeWithdrawTarget(InteractionStep.WithdrawTarget target) {
        return switch (target) {
            case InteractionStep.WithdrawTarget.Resource resource -> describeResource(resource.resource());
            case InteractionStep.WithdrawTarget.Input input -> describeInput(input.input());
        };
    }

    //endregion

    //region bindings

    private static String describeInputs(ImmutableList<InputBinding> bindings) {
        if (bindings.isEmpty()) {
            return "[]";
        }
        return bindings.collect(binding -> describeInput(binding.input()) + " x" + binding.amount())
            .makeString("[", ", ", "]");
    }

    private static String describeOutputs(ImmutableList<OutputBinding> bindings) {
        if (bindings.isEmpty()) {
            return "[]";
        }
        return bindings.collect(binding -> describeResource(binding.resource()) + " x" + binding.amount())
            .makeString("[", ", ", "]");
    }

    //endregion

    //region content

    private static String describeInput(InputContent input) {
        return switch (input) {
            case TagStackContent<?> tag -> "#" + tag.tagKey().location();
            case StackContent<?, ?> stack -> describeResource(stack.content());
            case TypedStackListContent<?, ?> typedList -> describeVariants(typedList.stacks());
            case StackListContent stackList -> describeVariants(stackList.stacks());
            case ValueContent<?> value -> describeValue(value.content());
            default -> input.isEnumerable() ? describeVariants(input.enumerate()) : Objects.toString(input, "unknown");
        };
    }

    private static String describeResource(ContentResource resource) {
        return switch (resource) {
            case ContentStack<?, ?> stack -> describeStack(stack);
            case ContentValue<?> value -> describeValue(value);
            default -> Objects.toString(resource, "unknown");
        };
    }

    //endregion

    //region helpers

    private static String describeRecipe(RecipeEntry<?> recipe) {
        ResourceLocation id = recipe.id();
        return id == null ? recipe.systemId().toString() : id.toString();
    }

    private static String describeVariants(Iterable<? extends ContentVariant> variants) {
        ArrayList<String> labels = new ArrayList<>(3);
        int total = 0;
        for (ContentVariant variant : variants) {
            if (labels.size() < 3) {
                labels.add(describeVariant(variant));
            }
            total++;
        }
        if (labels.isEmpty()) {
            return "unknown";
        }
        return total > labels.size()
            ? String.join(" | ", labels) + " | ..."
            : String.join(" | ", labels);
    }

    private static String describeVariant(ContentVariant variant) {
        return switch (variant) {
            case ContentStack<?, ?> stack -> describeStack(stack);
            case ContentKind<?> kind -> describeKind(kind);
            case ContentValue<?> value -> describeValue(value);
            default -> Objects.toString(variant, "unknown");
        };
    }

    private static String describeStack(ContentStack<?, ?> stack) {
        if (stack.isItem()) {
            Item item = stack.<Item, ItemStack>cast().content();
            return Objects.toString(BuiltInRegistries.ITEM.getKey(item), "minecraft:air");
        }
        if (stack.isFluid()) {
            Fluid fluid = stack.<Fluid, FluidStack>cast().content();
            return Objects.toString(BuiltInRegistries.FLUID.getKey(fluid), "minecraft:empty");
        }
        ResourceLocation location = stack.kind().getLocation();
        return location == null ? Objects.toString(stack.content(), "unknown") : location.toString();
    }

    private static String describeKind(ContentKind<?> kind) {
        ResourceLocation location = kind.getLocation();
        if (location != null) {
            return location.toString();
        }
        return kind.type().typeId() + "=" + Objects.toString(kind.value(), "unknown");
    }

    private static String describeValue(ContentValue<?> value) {
        return value.type().typeId() + "=" + value.value();
    }

    private static String describeGuidance(ExecutionGuidance guidance) {
        Component summary = guidance.summary();
        return summary == null ? "<no guidance>" : quote(summary.getString());
    }

    private static String sideCapabilitySuffix(InteractionStep step) {
        return " side=" + step.requiredSide() + " capability=" + step.requiredCapability();
    }

    private static String quote(String text) {
        return "\"" + text.replace("\"", "\\\"") + "\"";
    }

    //endregion
}
