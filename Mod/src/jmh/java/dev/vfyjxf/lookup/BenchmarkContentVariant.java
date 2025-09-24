package dev.vfyjxf.lookup;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
@State(Scope.Thread)
public class BenchmarkContentVariant {

    @Param({"25", "50", "100"})
    public int size;

    public List<ItemStub> queries;
    private int index;

    public HashMap<ItemStub, Object> hashMap;
    public Object2ObjectOpenHashMap<ItemStub, Object> openHashMap;
    public Object2ObjectArrayMap<ItemStub, Object> arrayMap;
    public UnifiedMap<ItemStub,Object> unifiedMap;

    public Object dummyValue = new Object();

    @Setup(Level.Iteration)
    public void setup() {
        List<ItemStub> keys = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            keys.add(new ItemStub(i));
        }

        hashMap = new HashMap<>(size);
        openHashMap = new Object2ObjectOpenHashMap<>(size);
        arrayMap = new Object2ObjectArrayMap<>(size);
        unifiedMap = new UnifiedMap<>(size);

        for (ItemStub item : keys) {
            hashMap.put(item, dummyValue);
            openHashMap.put(item, dummyValue);
            arrayMap.put(item, dummyValue);
            unifiedMap.put(item, dummyValue);
        }

        queries = new ArrayList<>();
        for (int i = 0; i < size / 4; i++) {
            queries.add(keys.get(i));
        }
        for (int i = 0; i < size * 3; i++) {
            queries.add(new ItemStub(size + i));
        }
    }

    private ItemStub nextKey() {
        index++;
        if (index >= queries.size()) index = 0;
        return queries.get(index);
    }

    @Benchmark
    public Object hashMapGet() {
        return hashMap.get(nextKey());
    }

    @Benchmark
    public Object openHashMapGet() {
        return openHashMap.get(nextKey());
    }

    @Benchmark
    public Object arrayMapGet() {
        return arrayMap.get(nextKey());
    }

    @Benchmark
    public Object unifiedMapGet() {
        return unifiedMap.get(nextKey());
    }

    static class ItemStub {
        private final int id;

        public ItemStub(int id) {this.id = id;}
    }
}


