package dev.vfyjxf.cirrus.recipe.execution;

import dev.vfyjxf.cirrus.api.recipe.execution.driver.DriverSelector;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverFactory;
import dev.vfyjxf.cirrus.api.register.ExecutionDriverRegister;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runtime registry for execution drivers.
 * <p>
 * Flat list, later registration wins.
 */
public final class ExecutionDriverRegistry implements ExecutionDriverRegister {

    //region storage

    final CopyOnWriteArrayList<Entry> clientEntries = new CopyOnWriteArrayList<>();
    final CopyOnWriteArrayList<Entry> serverEntries = new CopyOnWriteArrayList<>();

    record Entry(long order, DriverSelector selector, ExecutionDriverFactory factory) {}

    //endregion

    private final AtomicLong registrationOrder = new AtomicLong();

    @Override
    public void registerClient(DriverSelector selector, ExecutionDriverFactory factory) {
        clientEntries.add(new Entry(registrationOrder.incrementAndGet(), selector, factory));
    }

    @Override
    public void registerServer(DriverSelector selector, ExecutionDriverFactory factory) {
        serverEntries.add(new Entry(registrationOrder.incrementAndGet(), selector, factory));
    }
}
