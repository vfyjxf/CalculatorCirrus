package dev.vfyjxf.cirrus.api.recipe.system.extension;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

import java.util.LinkedHashSet;
import java.util.Set;

public record SubsystemOrder(int order, Set<Namespace> after, Set<Namespace> before) {

    public SubsystemOrder {
        Checks.checkNotNull(after, "after");
        Checks.checkNotNull(before, "before");
        after = Set.copyOf(after);
        before = Set.copyOf(before);
    }

    public static SubsystemOrder early() {
        return new SubsystemOrder(500, Set.of(), Set.of());
    }

    public static SubsystemOrder standard() {
        return new SubsystemOrder(1000, Set.of(), Set.of());
    }

    public static SubsystemOrder late() {
        return new SubsystemOrder(1500, Set.of(), Set.of());
    }

    public static SubsystemOrder fallback() {
        return new SubsystemOrder(2000, Set.of(), Set.of());
    }

    public SubsystemOrder after(Namespace... ids) {
        Checks.checkNotNull(ids, "ids");
        LinkedHashSet<Namespace> next = new LinkedHashSet<>(after);
        next.addAll(Set.of(ids));
        return new SubsystemOrder(order, next, before);
    }

    public SubsystemOrder before(Namespace... ids) {
        Checks.checkNotNull(ids, "ids");
        LinkedHashSet<Namespace> next = new LinkedHashSet<>(before);
        next.addAll(Set.of(ids));
        return new SubsystemOrder(order, after, next);
    }
}
