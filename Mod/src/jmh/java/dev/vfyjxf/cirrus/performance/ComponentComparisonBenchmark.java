package dev.vfyjxf.cirrus.performance;

import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Micro benchmark for the component part of {@link MatchStrategy#identityAndComponent}.
 *
 * <p>Scenarios model the realistic shapes an item's {@link DataComponentMap} takes; relations model
 * where the two compared stacks come from:
 * <ul>
 *     <li>{@code SAME_REF} — copy chain (patch map COW-shared): vanilla's fast path.</li>
 *     <li>{@code EQUAL_DISTINCT} — independently built, logically equal patches: the deep-equals
 *         hot path (stock stack vs recipe-output copy, planner demand vs graph output).</li>
 *     <li>{@code UNEQUAL_ONE_VALUE} — equal except the last-iterated component's value.</li>
 *     <li>{@code UNEQUAL_VARIANT} — different variant-carrying component (e.g. another potion).</li>
 * </ul>
 *
 * <p>{@code contentStackMatchesBaseline} uses a fresh lambda equivalent to {@link MatchStrategy#same}
 * so it keeps measuring the slow path even after the canonical fast path lands; {@code contentStackMatchesDefault}
 * measures the production default strategy.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Benchmark)
@SuppressWarnings("PMD.PublicNonFinalInstanceField")
public class ComponentComparisonBenchmark {

    /**
     * Semantically identical to {@link MatchStrategy#identityAndComponent} but built from a fresh
     * lambda, so a fast path keyed on the exact {@code same} matcher instance will not apply to it.
     */
    private static final MatchStrategy explicitSlow = new MatchStrategy(
        true,
        false,
        true,
        (a, b) -> Objects.equals(a, b)
    );

    @Param({"PLAIN", "TOOL", "ENCHANTED", "POTION", "RENAMED", "CONTAINER"})
    public String scenario;

    @Param({"SAME_REF", "EQUAL_DISTINCT", "UNEQUAL_ONE_VALUE", "UNEQUAL_VARIANT"})
    public String relation;

    private ContentStack<Item, ItemStack> left;
    private ContentStack<Item, ItemStack> right;
    private DataComponentMap leftComponents;
    private DataComponentMap rightComponents;

    @Setup
    public void setup() {
        McBenchmarkBootstrap.ensureContentApi();
        left = ContentStack.fromStack(sourceStack(false, false));
        right = switch (relation) {
            case "SAME_REF" -> left.copy();
            case "EQUAL_DISTINCT" -> ContentStack.fromStack(sourceStack(false, false));
            case "UNEQUAL_ONE_VALUE" -> ContentStack.fromStack(sourceStack(false, true));
            case "UNEQUAL_VARIANT" -> ContentStack.fromStack(sourceStack(true, false));
            default -> throw new IllegalArgumentException("Unknown relation: " + relation);
        };
        leftComponents = left.components();
        rightComponents = right.components();
        if (relation.startsWith("UNEQUAL") && left.matches(right, MatchStrategy.identityAndComponent)) {
            throw new IllegalStateException("Fixture for " + scenario + "/" + relation + " must not match");
        }
        if (!relation.startsWith("UNEQUAL") && !left.matches(right, MatchStrategy.identityAndComponent)) {
            throw new IllegalStateException("Fixture for " + scenario + "/" + relation + " must match");
        }
    }

    @Benchmark
    public boolean vanillaMapEquals() {
        return MatchStrategy.identityAndComponent.testComponents(leftComponents, rightComponents);
    }

    @Benchmark
    public boolean contentStackMatchesBaseline() {
        return left.matches(right, explicitSlow);
    }

    @Benchmark
    public boolean contentStackMatchesDefault() {
        return left.matches(right, MatchStrategy.identityAndComponent);
    }

    /**
     * Measures fresh stack construction — before the optimization this is construction only;
     * afterwards it additionally pays the first (cold) canonicalization, quantifying the amortization.
     */
    @Benchmark
    public ContentStack<Item, ItemStack> canonicalizeCold() {
        return ContentStack.fromStack(sourceStack(false, false));
    }

    //region fixtures

    private ItemStack sourceStack(boolean variantB, boolean unequalValue) {
        return switch (scenario) {
            case "PLAIN" -> new ItemStack(Items.STONE);
            case "TOOL" -> new ItemStack(Items.DIAMOND_SWORD);
            case "ENCHANTED" -> multiComponentSword(variantB, unequalValue);
            case "POTION" -> potion(variantB);
            case "RENAMED" -> renamedPickaxe(variantB, unequalValue);
            case "CONTAINER" -> container(variantB, unequalValue);
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
        };
    }

    /**
     * A tool-shaped stack: several patch components whose values need deep comparison. The differing
     * value (if any) is set last — patch iteration order — so unequal comparisons walk the whole map.
     */
    private static ItemStack multiComponentSword(boolean variantB, boolean unequalValue) {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Sword of Benchmarking"));
        stack.set(
            DataComponents.LORE,
            new ItemLore(List.of(Component.literal("Forged in JMH"), Component.literal("Do not nerf")), List.of())
        );
        stack.set(DataComponents.REPAIR_COST, 7);
        CompoundTag tag = new CompoundTag();
        tag.putInt("grind", variantB ? 2 : 1);
        tag.putFloat("efficiency", 0.75f);
        CompoundTag nested = new CompoundTag();
        nested.putString("origin", "benchmark");
        nested.putLong("seed", 42L);
        tag.put("meta", nested);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.DAMAGE, unequalValue ? 43 : 42);
        return stack;
    }

    private static ItemStack potion(boolean variantB) {
        ItemStack stack = new ItemStack(Items.POTION);
        stack.set(
            DataComponents.POTION_CONTENTS,
            new PotionContents(variantB ? Potions.REGENERATION : Potions.HEALING)
        );
        return stack;
    }

    private static ItemStack renamedPickaxe(boolean variantB, boolean unequalValue) {
        ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
        String name = variantB ? "Miner's Delight" : "Benchmark Pickaxe";
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(unequalValue ? name + " II" : name));
        return stack;
    }

    private static ItemStack container(boolean variantB, boolean unequalValue) {
        ItemStack stack = new ItemStack(Items.BUNDLE);
        ItemStack nestedSword = new ItemStack(Items.DIAMOND_SWORD);
        nestedSword.set(DataComponents.CUSTOM_NAME, Component.literal(variantB ? "Nested B" : "Nested A"));
        ItemStack nestedPickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        nestedPickaxe.set(DataComponents.DAMAGE, 12);
        ItemStack nestedGem = new ItemStack(Items.DIAMOND, unequalValue ? 3 : 2);
        stack.set(
            DataComponents.CONTAINER,
            ItemContainerContents.fromItems(List.of(nestedSword, nestedPickaxe, nestedGem))
        );
        return stack;
    }

    //endregion
}
