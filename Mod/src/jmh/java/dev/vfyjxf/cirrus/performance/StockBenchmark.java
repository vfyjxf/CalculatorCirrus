package dev.vfyjxf.cirrus.performance;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.routing.Stock;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
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
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Meso benchmark for the {@link Stock} linear scans that call
 * {@link dev.vfyjxf.cirrus.api.content.MatchStrategy#identityAndComponent} per entry.
 *
 * <p>Probes are rebuilt from independent construction sites so hitting entries compare through the
 * cross-origin deep-equals path (stock entries from "inventory", probes from "recipe demand").
 * Plain entries cycle the vanilla item registry (bounded pool — duplicate adds merge, so plain-heavy
 * stocks may hold slightly fewer distinct entries than {@code stockSize}).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class StockBenchmark {

    @Param({"64", "1024", "4096"})
    public int stockSize;

    @Param({"0.0", "0.5", "1.0"})
    public String componentShareParam;

    private Stock stock;
    private ContentStack<Item, ItemStack>[] hitProbes;
    private ContentStack<Item, ItemStack> missProbe;
    private int cursor;

    @Setup
    public void setup() {
        McBenchmarkBootstrap.ensureContentApi();
        double componentShare = Double.parseDouble(componentShareParam);
        stock = Stock.empty();
        int componentCount = (int) Math.round(stockSize * componentShare);
        int plainCount = stockSize - componentCount;

        List<ContentStack<Item, ItemStack>> probes = new ArrayList<>(componentCount);
        for (int i = 0; i < componentCount; i++) {
            // Insertion and probe are built independently: equal values, distinct patch instances.
            stock.add(componentVariant(i));
            probes.add(componentVariant(i));
        }
        Item[] plainItems = plainItemPool();
        for (int i = 0; i < plainCount; i++) {
            stock.add(ContentStack.fromStack(new ItemStack(plainItems[i % plainItems.length])));
        }
        //noinspection unchecked
        hitProbes = probes.toArray(new ContentStack[0]);
        missProbe = componentVariant(-1);
        for (ContentStack<Item, ItemStack> probe : probes) {
            if (stock.countMatching(probe) <= 0) {
                throw new IllegalStateException("Probe not found in stock — fixture broken");
            }
        }
        if (stock.countMatching(missProbe) != 0) {
            throw new IllegalStateException("Miss probe unexpectedly found in stock");
        }
    }

    @Benchmark
    public void addHit(Blackhole blackhole) {
        stock.add(nextProbe());
        blackhole.consume(stock);
    }

    @Benchmark
    public long countMatchingHit() {
        return stock.countMatching(nextProbe());
    }

    @Benchmark
    public long countMatchingMiss() {
        return stock.countMatching(missProbe);
    }

    @Benchmark
    public long consumeAndRefillHit() {
        ContentStack<Item, ItemStack> probe = nextProbe();
        long consumed = stock.consume(probe, 1);
        stock.add(probe);
        return consumed;
    }

    private ContentStack<Item, ItemStack> nextProbe() {
        ContentStack<Item, ItemStack> probe = hitProbes[cursor];
        cursor = cursor + 1 == hitProbes.length ? 0 : cursor + 1;
        return probe;
    }

    /**
     * A component-bearing sword variant. Each invocation builds a fresh lineage, mirroring how stock
     * entries and recipe demands arrive from unrelated code paths.
     */
    private static ContentStack<Item, ItemStack> componentVariant(int index) {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        String name = index < 0 ? "bench absent variant" : "bench sword #" + index;
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        CompoundTag tag = new CompoundTag();
        tag.putString("owner", "benchmark");
        tag.putInt("index", index);
        tag.putLong("serial", 1_000L + index);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.DAMAGE, 42);
        return ContentStack.fromStack(stack);
    }

    private static Item[] plainItemPool() {
        List<Item> items = new ArrayList<>();
        for (Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (item != Items.AIR) items.add(item);
        }
        return items.toArray(new Item[0]);
    }
}
