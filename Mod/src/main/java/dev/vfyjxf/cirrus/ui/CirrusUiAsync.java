package dev.vfyjxf.cirrus.ui;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
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
    private static final ExecutorService databaseIndexExecutor = Executors.newSingleThreadExecutor(
        daemonThreadFactory("cirrus-database-index-", databaseIndexThreadIds)
    );

    private CirrusUiAsync() {
    }

    public static <T> CompletableFuture<T> recipeTask(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, recipeExecutor);
    }

    public static <T> CompletableFuture<T> databaseIndexTask(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, databaseIndexExecutor);
    }

    private static ThreadFactory daemonThreadFactory(String prefix, AtomicInteger ids) {
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + ids.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
