package dev.vfyjxf.cirrus.recipe.execution;

import com.google.common.collect.ImmutableList;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.DriverLookup;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.DriverSelector.MatchResult;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverApi;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverHandle;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.context.DriverContext;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.context.SideContext;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionDriverRegistry.Entry;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

public class ExecutionDriverApiImpl implements ExecutionDriverApi {

    private static @Nullable ExecutionDriverApiImpl instance;

    private final ImmutableList<Entry> clientEntries;
    private final ImmutableList<Entry> serverEntries;

    public ExecutionDriverApiImpl(ExecutionDriverRegistry registry) {
        clientEntries = registry.clientEntries.stream().sorted(Comparator.comparingLong(Entry::order).reversed())
                .collect(ImmutableList.toImmutableList());
        serverEntries = registry.serverEntries.stream().sorted(Comparator.comparingLong(Entry::order).reversed())
                .collect(ImmutableList.toImmutableList());
    }

    public static void attach(ExecutionDriverApiImpl api) {
        instance = api;
    }

    public static @Nullable ExecutionDriverApiImpl instance() {
        return instance;
    }

    @Override
    public @Nullable ExecutionDriverHandle resolveClient(DriverLookup lookup) {
        return resolve(clientEntries, lookup, ExecutionSideRequirement.client);
    }

    @Override
    public @Nullable ExecutionDriverHandle resolveServer(DriverLookup lookup) {
        return resolve(serverEntries, lookup, ExecutionSideRequirement.server);
    }

    private @Nullable ExecutionDriverHandle resolve(
        List<Entry> entries,
        DriverLookup lookup,
        ExecutionSideRequirement side
    ) {
        DriverLookup normalizedLookup = lookup.with(new SideContext(side));
        for (Entry entry : entries) {
            boolean accepted = false;
            boolean rejected = false;
            for (DriverContext context : normalizedLookup.contexts()) {
                MatchResult result = entry.selector().match(context);
                if (result == MatchResult.accept) {
                    accepted = true;
                } else if (result == MatchResult.reject) {
                    rejected = true;
                    break;
                }
            }
            if (!rejected && accepted) {
                return new ExecutionDriverHandle(entry.factory(), normalizedLookup);
            }
        }
        return null;
    }
}
