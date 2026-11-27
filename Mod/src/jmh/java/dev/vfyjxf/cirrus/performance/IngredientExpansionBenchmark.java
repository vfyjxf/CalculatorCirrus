package dev.vfyjxf.cirrus.performance;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
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
 * Repeatedly expands a fixed pool of ingredients through {@link
 * InputContent#fromItemIngredient(Ingredient)}, modelling how graph rebuilds
 * and the routingView/resolve pair re-expand the same recipe-manager-owned
 * ingredient instances many times per session. The vanilla registry holds no
 * bound tags in the harness, so the multi-item path exercises the
 * holder-index probe against an empty tag set — cheaper than in game, but
 * identical between baseline and branch.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class IngredientExpansionBenchmark {

    @State(Scope.Benchmark)
    public static class IngredientPool {
        @Param({"1024", "8192"})
        public int poolSize;

        private Ingredient[] single;
        private Ingredient[] multi;
        private Ingredient[] tag;

        @Setup(Level.Trial)
        public void setup() {
            McBenchmarkBootstrap.ensureContentApi();
            List<Item> items = new ArrayList<>();
            BuiltInRegistries.ITEM.forEach(items::add);
            single = new Ingredient[poolSize];
            multi = new Ingredient[poolSize];
            tag = new Ingredient[poolSize];
            for (int i = 0; i < poolSize; i++) {
                single[i] = Ingredient.of(items.get((i * 3) % items.size()));
                multi[i] = Ingredient.of(
                    items.get((i * 3) % items.size()),
                    items.get((i * 3 + 1) % items.size()),
                    items.get((i * 3 + 2) % items.size())
                );
                tag[i] = Ingredient
                        .of(TagKey.create(Registries.ITEM, ResourceLocation.parse("minecraft:bench_tag_" + (i % 64))));
            }
        }
    }

    @Benchmark
    public void expandSingleItemPool(IngredientPool pool, Blackhole hole) {
        for (Ingredient ingredient : pool.single) {
            hole.consume(InputContent.fromItemIngredient(ingredient));
        }
    }

    @Benchmark
    public void expandMultiItemPool(IngredientPool pool, Blackhole hole) {
        for (Ingredient ingredient : pool.multi) {
            hole.consume(InputContent.fromItemIngredient(ingredient));
        }
    }

    @Benchmark
    public void expandTagPool(IngredientPool pool, Blackhole hole) {
        for (Ingredient ingredient : pool.tag) {
            hole.consume(InputContent.fromItemIngredient(ingredient));
        }
    }
}
