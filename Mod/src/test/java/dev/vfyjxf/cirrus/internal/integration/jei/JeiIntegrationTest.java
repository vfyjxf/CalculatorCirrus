package dev.vfyjxf.cirrus.internal.integration.jei;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class JeiIntegrationTest {

    @AfterEach
    void resetBridge() {
        JeiIntegration.resetForTests();
    }

    @Test
    void callbackBeforeReadyRunsOnlyWhenReadyFlips() throws InterruptedException {
        AtomicInteger ran = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(1);
        JeiIntegration.onSystemsReady(() -> {
            ran.incrementAndGet();
            latch.countDown();
        });
        Assertions.assertEquals(0, ran.get(), "callback must not run before readiness");
        JeiIntegration.markSystemsReady();
        Assertions.assertTrue(latch.await(5, TimeUnit.SECONDS), "callback did not run after flip");
        Assertions.assertEquals(1, ran.get());
        Assertions.assertTrue(JeiIntegration.systemsReady());
    }

    @Test
    void callbackAfterReadyRunsSynchronously() {
        JeiIntegration.markSystemsReady();
        AtomicInteger ran = new AtomicInteger();
        JeiIntegration.onSystemsReady(ran::incrementAndGet);
        Assertions.assertEquals(1, ran.get(), "late callback must run inline");
    }

    @Test
    void markSystemsReadyIsIdempotent() {
        List<Integer> order = new ArrayList<>();
        JeiIntegration.onSystemsReady(() -> order.add(1));
        JeiIntegration.markSystemsReady();
        JeiIntegration.markSystemsReady();
        JeiIntegration.onSystemsReady(() -> order.add(2));
        Assertions.assertEquals(List.of(1, 2), order, "exactly one flip, late callbacks inline");
    }

    @Test
    void concurrentWaitersAllRunExactlyOnce() throws InterruptedException {
        int waiters = 8;
        AtomicInteger ran = new AtomicInteger();
        CountDownLatch allDone = new CountDownLatch(waiters);
        CountDownLatch registered = new CountDownLatch(waiters);
        for (int i = 0; i < waiters; i++) {
            new Thread(() -> {
                JeiIntegration.onSystemsReady(() -> {
                    ran.incrementAndGet();
                    allDone.countDown();
                });
                registered.countDown();
            }).start();
        }
        Assertions.assertTrue(registered.await(5, TimeUnit.SECONDS));
        JeiIntegration.markSystemsReady();
        Assertions.assertTrue(allDone.await(5, TimeUnit.SECONDS));
        Assertions.assertEquals(waiters, ran.get(), "every waiter runs exactly once");
    }
}
