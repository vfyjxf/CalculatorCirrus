package dev.vfyjxf.cirrus.performance;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ItemContainerContents;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * One-shot spike: verifies that vanilla registries can be bootstrapped inside a JMH fork
 * and that realistic component-bearing stacks can be constructed and compared.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class BootstrapSmoke {

    @Setup(Level.Trial)
    public void setup() {
        McBenchmarkBootstrap.ensureBootstrapped();
    }

    @Benchmark
    public boolean plainItemSameComponents() {
        ItemStack a = new ItemStack(Items.DIAMOND_SWORD);
        ItemStack b = new ItemStack(Items.DIAMOND_SWORD);
        return ItemStack.isSameItemSameComponents(a, b);
    }

    @Benchmark
    public boolean potionSameComponents() {
        ItemStack a = potion();
        ItemStack b = potion();
        return ItemStack.isSameItemSameComponents(a, b);
    }

    @Benchmark
    public boolean containerSameComponents() {
        ItemStack a = container();
        ItemStack b = container();
        return ItemStack.isSameItemSameComponents(a, b);
    }

    private static ItemStack potion() {
        ItemStack stack = new ItemStack(Items.POTION);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HEALING));
        return stack;
    }

    private static ItemStack container() {
        ItemStack stack = new ItemStack(Items.BUNDLE);
        stack.set(
                DataComponents.CONTAINER,
                ItemContainerContents.fromItems(java.util.List.of(
                        new ItemStack(Items.DIAMOND_SWORD),
                        new ItemStack(Items.DIAMOND),
                        new ItemStack(Items.EMERALD)
                ))
        );
        return stack;
    }
}
