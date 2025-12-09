package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import org.eclipse.collections.api.factory.SortedSets;
import org.eclipse.collections.api.set.sorted.MutableSortedSet;

public class Stock {

    private final MutableSortedSet<ContentStack<?, ?>> stacks = SortedSets.mutable.empty();

    public static Stock empty() {
        return new Stock();
    }

}
