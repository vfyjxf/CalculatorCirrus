package dev.vfyjxf.cirrus.api.recipe.routing.plan;

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
import net.minecraft.core.registries.BuiltInRegistries;
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
 * Formats routing plans into debug-readable text.
 *
 * <p>The formatter intentionally prefers stable semantic identifiers over
 * localized names so logs stay searchable and easy to diff.
 */
public final class RoutingPlanDebugText {

    private RoutingPlanDebugText() {
    }

    //region plan

    public static String describe(@Nullable RoutingPlan plan) {
        if (plan == null) {
            return "<null plan>";
        }

        MutableList<String> lines = Lists.mutable.empty();
        lines.add("plan target=" + describeRecipe(plan.targetRecipe()) + " x" + plan.targetTimes());
        lines.add("version=" + plan.planVersion() + " tagSnapshot=" + plan.tagSnapshotVersion() + " actions=" + plan.actions().size());
        for (PlanAction action : plan.actions()) {
            lines.addAllIterable(describeLines(action));
        }
        return lines.makeString(System.lineSeparator());
    }

    public static ImmutableList<String> describeLines(PlanAction action) {
        MutableList<String> lines = Lists.mutable.empty();
        String prefix = "[" + action.actionIndex() + "|" + action.actionId() + "] ";
        switch (action) {
            case PlanAction.ConsumeStock consume -> {
                String line = prefix + "consume-stock " + describeResource(consume.resource()) + " x" + consume.amount();
                if (consume.input().preserveOriginalShortageSemantics()) {
                    line += " <= " + describeInput(consume.input());
                }
                lines.add(line);
            }
            case PlanAction.PendingInput pending ->
                lines.add(prefix + "pending-input " + describeInput(pending.input()) + " x" + pending.amount());
            case PlanAction.ReserveCatalyst reserve ->
                lines.add(prefix + "reserve-catalyst " + describeInput(reserve.input()) + " x" + reserve.amount()
                    + " from " + describeRecipe(reserve.fromRecipe()));
            case PlanAction.ExecuteRecipe execute -> {
                lines.add(prefix + "execute " + describeRecipe(execute.recipe()) + " x" + execute.times());
                lines.add("  inputs:  " + describeInputs(execute.boundInputs()));
                lines.add("  outputs: " + describeOutputs(execute.expectedOutputs()));
            }
        }
        return lines.toImmutable();
    }

    //endregion

    //region bindings

    public static String describeInputs(ImmutableList<PlanAction.InputBinding> bindings) {
        if (bindings.isEmpty()) {
            return "[]";
        }
        return bindings.collect(binding -> describeInput(binding.input()) + " x" + binding.amount())
            .makeString("[", ", ", "]");
    }

    public static String describeOutputs(ImmutableList<PlanAction.OutputBinding> bindings) {
        if (bindings.isEmpty()) {
            return "[]";
        }
        return bindings.collect(binding -> describeResource(binding.resource()) + " x" + binding.amount())
            .makeString("[", ", ", "]");
    }

    //endregion

    //region content

    public static String describeInput(InputContent input) {
        return switch (input) {
            case TagStackContent<?> tag -> "#" + tag.tagKey().location();
            case StackContent<?, ?> stack -> describeResource(stack.content());
            case TypedStackListContent<?, ?> typedList -> describeVariants(typedList.stacks());
            case StackListContent stackList -> describeVariants(stackList.stacks());
            case ValueContent<?> value -> describeValue(value.content());
            default -> input.isEnumerable() ? describeVariants(input.enumerate()) : Objects.toString(input, "unknown");
        };
    }

    public static String describeResource(ContentResource resource) {
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

    //endregion
}
