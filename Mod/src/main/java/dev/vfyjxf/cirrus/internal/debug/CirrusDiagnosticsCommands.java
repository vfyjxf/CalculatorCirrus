package dev.vfyjxf.cirrus.internal.debug;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import dev.vfyjxf.cirrus.api.planning.graph.GraphCompilationStats;
import dev.vfyjxf.cirrus.planning.adapter.DropReason;
import dev.vfyjxf.cirrus.planning.bridge.CirrusCatalogAdapter;
import dev.vfyjxf.cirrus.planning.bridge.CirrusPlanningRuntime;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public final class CirrusDiagnosticsCommands {

    private static final Gson prettyGson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private CirrusDiagnosticsCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher()
                .register(
                    Commands.literal("cirrus").requires(source -> source.hasPermission(2))
                            .then(
                                Commands.literal("routingStats")
                                        .then(
                                            Commands.literal("export")
                                                    .executes(context -> exportStats(context.getSource()))
                                        ).executes(context -> printStats(context.getSource()))
                            )
                );
    }

    private static int printStats(CommandSourceStack source) {
        Optional<CirrusPlanningRuntime.PublishedSnapshot> snapOpt = CirrusPlanningRuntime.shared()
                .flatMap(CirrusPlanningRuntime::currentSnapshot);
        if (snapOpt.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Cirrus planner snapshot not ready yet."), false);
            return Command.SINGLE_SUCCESS;
        }
        CirrusPlanningRuntime.PublishedSnapshot snap = snapOpt.get();
        GraphCompilationStats gs = snap.graph().stats();
        CirrusCatalogAdapter.CaptureStats cs = snap.captureStats();
        int producible = snap.graph().allProducibleResources().size();
        String msg = String.format(
            "Cirrus Planner v%d (epoch=%d): compiled=%d raw=%d producible=%d macros=%d largestScc=%d "
                    + "reversible=%d discouraged=%d lastResort=%d excludedDisplay=%d | capture visited=%d kept=%d resolveFail=%d exFail=%d",
            snap.version(),
            snap.attachEpoch(),
            gs.compiledRecipes(),
            gs.totalRawRecipes(),
            producible,
            gs.cycleMacrosMined(),
            gs.largestSccSize(),
            gs.reversibleEdgesDetected(),
            gs.discouragedRecipes(),
            gs.lastResortRecipes(),
            gs.excludedDisplaySystems(),
            cs.recipesVisited(),
            cs.recipesKept(),
            cs.recipesResolveFailed(),
            cs.recipesExceptionFailed()
        );
        source.sendSuccess(() -> Component.literal(msg), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int exportStats(CommandSourceStack source) {
        Optional<CirrusPlanningRuntime.PublishedSnapshot> snapOpt = CirrusPlanningRuntime.shared()
                .flatMap(CirrusPlanningRuntime::currentSnapshot);
        if (snapOpt.isEmpty()) {
            source.sendFailure(Component.literal("Cirrus planner snapshot not ready yet."));
            return 0;
        }
        CirrusPlanningRuntime.PublishedSnapshot snap = snapOpt.get();
        JsonObject root = buildStatsJson(snap);
        Path outFile = FMLPaths.GAMEDIR.get().resolve("logs").resolve("cirrus_planning_stats.json");
        try {
            Path parent = outFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(outFile, prettyGson.toJson(root), StandardCharsets.UTF_8);
            source.sendSuccess(
                () -> Component.literal("Exported Cirrus planner stats to " + outFile.toAbsolutePath()),
                false
            );
            return Command.SINGLE_SUCCESS;
        } catch (IOException e) {
            source.sendFailure(Component.literal("Failed to export Cirrus planner stats: " + e.getMessage()));
            return 0;
        }
    }

    private static JsonObject buildStatsJson(CirrusPlanningRuntime.PublishedSnapshot snap) {
        GraphCompilationStats gs = snap.graph().stats();
        CirrusCatalogAdapter.CaptureStats cs = snap.captureStats();

        JsonObject graphJson = new JsonObject();
        graphJson.addProperty("totalRawRecipes", gs.totalRawRecipes());
        graphJson.addProperty("compiledRecipes", gs.compiledRecipes());
        graphJson.addProperty("producibleResources", snap.graph().allProducibleResources().size());
        graphJson.addProperty("unresolvedOutputs", snap.graph().unresolvedOutputs().size());
        graphJson.addProperty("cycleMacrosMined", gs.cycleMacrosMined());
        graphJson.addProperty("largestSccSize", gs.largestSccSize());
        graphJson.addProperty("reversibleEdgesDetected", gs.reversibleEdgesDetected());
        graphJson.addProperty("deduplicatedRecipes", gs.deduplicatedRecipes());
        graphJson.addProperty("discouragedRecipes", gs.discouragedRecipes());
        graphJson.addProperty("lastResortRecipes", gs.lastResortRecipes());
        graphJson.addProperty("excludedInvalidOrComplex", gs.excludedInvalidOrComplex());
        graphJson.addProperty("excludedDisplaySystems", gs.excludedDisplaySystems());
        graphJson.addProperty("excludedByPolicy", gs.excludedByPolicy());
        graphJson.addProperty("excludedInsaneAmount", gs.excludedInsaneAmount());
        graphJson.addProperty("excludedNoInputs", gs.excludedNoInputs());
        graphJson.addProperty("normalizedResourceIds", gs.normalizedResourceIds());

        JsonObject dropsJson = new JsonObject();
        for (Map.Entry<DropReason, Integer> entry : cs.droppedByReason().entrySet()) {
            dropsJson.addProperty(entry.getKey().name(), entry.getValue());
        }

        JsonObject captureJson = new JsonObject();
        captureJson.addProperty("systemsVisited", cs.systemsVisited());
        captureJson.addProperty("systemsFailed", cs.systemsFailed());
        captureJson.addProperty("recipesVisited", cs.recipesVisited());
        captureJson.addProperty("recipesKept", cs.recipesKept());
        captureJson.addProperty("recipesResolveFailed", cs.recipesResolveFailed());
        captureJson.addProperty("recipesExceptionFailed", cs.recipesExceptionFailed());
        captureJson.addProperty("unresolvedOutputsRecorded", cs.unresolvedOutputsRecorded());
        captureJson.add("droppedByReason", dropsJson);

        JsonObject root = new JsonObject();
        root.addProperty("version", snap.version());
        root.addProperty("attachEpoch", snap.attachEpoch());
        root.add("graphStats", graphJson);
        root.add("captureStats", captureJson);
        return root;
    }
}
