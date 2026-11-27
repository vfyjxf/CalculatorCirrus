package dev.vfyjxf.cirrus.ui;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Shared UI-side worker pool for expensive read-only recipe calculations.
 */
public final class CirrusUiAsync {
    private static final AtomicInteger threadIds = new AtomicInteger();
    private static final AtomicInteger databaseIndexThreadIds = new AtomicInteger();
    private static final ExecutorService recipeExecutor = Executors.newFixedThreadPool(
        Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)),
        daemonThreadFactory("cirrus-recipe-worker-", threadIds)
    );
    private static final ExecutorService databaseIndexExecutor = Executors
            .newSingleThreadExecutor(daemonThreadFactory("cirrus-database-index-", databaseIndexThreadIds));
    private static final AtomicInteger computeThreadIds = new AtomicInteger();
    private static final int computeWorkers = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
    private static final ExecutorService computeExecutor = Executors
            .newFixedThreadPool(computeWorkers, daemonThreadFactory("cirrus-compute-", computeThreadIds));

    private CirrusUiAsync() {}

    public static <T> CompletableFuture<T> recipeTask(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, recipeExecutor);
    }

    public static <T> CompletableFuture<T> databaseIndexTask(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, databaseIndexExecutor);
    }

    /**
     * Runs {@code task.accept(index)} for every index in {@code [0, count)} on
     * the shared compute pool (blocking until all finish); small counts run on
     * the calling thread. For parallelizing large per-element work inside an
     * async recipe task (sort decoration, bucketing).
     */
    public static void parallelFor(int count, IntConsumer task) {
        if (count <= 0) {
            return;
        }
        if (count < 16_384) {
            for (int index = 0; index < count; index++) {
                task.accept(index);
            }
            return;
        }
        int workers = Math.min(count, computeWorkers);
        int chunk = (count + workers - 1) / workers;
        try {
            ArrayList<Future<?>> futures = new ArrayList<>(workers);
            for (int start = 0; start < count; start += chunk) {
                int from = start;
                int to = Math.min(count, start + chunk);
                futures.add(computeExecutor.submit(() -> {
                    for (int index = from; index < to; index++) {
                        task.accept(index);
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during parallel work", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Parallel work failed", exception.getCause());
        }
    }

    private static ThreadFactory daemonThreadFactory(String prefix, AtomicInteger ids) {
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + ids.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
