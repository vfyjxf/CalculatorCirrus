package dev.vfyjxf.cirrus.internal.export;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveError;
import dev.vfyjxf.cirrus.internal.export.RecipeExporter.ExportResult;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Commands for exporting the modpack's recipes and content registries as JSON:
 * <ul>
 *     <li>{@code /cirrus recipes list} — lists all recipe systems Cirrus knows about.</li>
 *     <li>{@code /cirrus recipes export} — exports every recipe to {@code cirrus-recipes/}.</li>
 *     <li>{@code /cirrus recipes export output <path>} — exports into a custom directory.</li>
 *     <li>{@code /cirrus recipes export systems <id1,id2,...>} — exports only the given systems.</li>
 *     <li>{@code /cirrus registry export} — exports the content registries (content/stack/tag/value/variant
 *     types) as one JSON file per category to {@code cirrus-recipes/}.</li>
 *     <li>{@code /cirrus registry export output <path>} — exports into a custom directory.</li>
 * </ul>
 * The heavy work runs on a background thread so the server thread is not blocked.
 */
public final class RecipeExportCommands {
    private static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus Recipe Export");

    private RecipeExportCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("cirrus")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("recipes")
                    .then(Commands.literal("list")
                        .executes(context -> list(context.getSource())))
                    .then(Commands.literal("export")
                        .executes(context -> export(context.getSource(), null, null))
                        .then(Commands.literal("output")
                            .then(Commands.argument("path", StringArgumentType.greedyString())
                                .executes(context -> export(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "path"),
                                    null))))
                        .then(Commands.literal("systems")
                            .then(Commands.argument("ids", StringArgumentType.greedyString())
                                .executes(context -> export(
                                    context.getSource(),
                                    null,
                                    splitIds(StringArgumentType.getString(context, "ids"))))))))
                .then(Commands.literal("registry")
                    .then(Commands.literal("export")
                        .executes(context -> registryExport(context.getSource(), null))
                        .then(Commands.literal("output")
                            .then(Commands.argument("path", StringArgumentType.greedyString())
                                .executes(context -> registryExport(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "path")))))))
        );
    }

    private static int list(CommandSourceStack source) {
        List<String> lines = RecipeExporter.listSystems(null);
        source.sendSuccess(() -> Component.literal("Cirrus recipe systems (" + lines.size() + "):"), false);
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal("  " + line), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int export(CommandSourceStack source, @Nullable String outputPath, @Nullable Set<String> systems) {
        MinecraftServer server = source.getServer();
        Path directory = FMLPaths.GAMEDIR.get().resolve(outputPath == null ? "cirrus-recipes" : outputPath);
        source.sendSuccess(() -> Component.literal("Cirrus recipe export started, output: " + directory), false);
        Thread thread = new Thread(() -> runExport(source, server, directory, systems), "cirrus-recipe-export");
        thread.setDaemon(true);
        thread.start();
        return Command.SINGLE_SUCCESS;
    }

    private static int registryExport(CommandSourceStack source, @Nullable String outputPath) {
        MinecraftServer server = source.getServer();
        Path directory = FMLPaths.GAMEDIR.get().resolve(outputPath == null ? "cirrus-recipes" : outputPath);
        source.sendSuccess(() -> Component.literal("Cirrus content registry export started, output: " + directory), false);
        Thread thread = new Thread(() -> runRegistryExport(source, server, directory), "cirrus-registry-export");
        thread.setDaemon(true);
        thread.start();
        return Command.SINGLE_SUCCESS;
    }

    private static void runRegistryExport(CommandSourceStack source, MinecraftServer server, Path directory) {
        try {
            ContentRegistryExporter.ExportResult result = ContentRegistryExporter.export(directory);
            logger.info(
                "Content registry export completed directory={} contentTypes={} stackTypes={} tagTypes={} valueTypes={} variantTypes={}",
                directory, result.contentTypeCount(), result.stackTypeCount(), result.tagTypeCount(), result.valueTypeCount(), result.variantTypeCount()
            );
            String message = "Content registry export completed: "
                + result.contentTypesFile() + ", " + result.stackTypesFile() + ", " + result.tagTypesFile() + ", "
                + result.valueTypesFile() + ", " + result.variantTypesFile()
                + " (" + result.contentTypeCount() + " content types, " + result.stackTypeCount() + " stack types, "
                + result.tagTypeCount() + " tag types, " + result.valueTypeCount() + " value types, "
                + result.variantTypeCount() + " variant types)";
            server.execute(() -> source.sendSuccess(() -> Component.literal(message), false));
        } catch (Throwable throwable) {
            logger.error("Content registry export failed", throwable);
            server.execute(() -> source.sendFailure(Component.literal("Content registry export failed: " + throwable.getMessage())));
        }
    }

    private static void runExport(
        CommandSourceStack source,
        MinecraftServer server,
        Path directory,
        @Nullable Set<String> systems
    ) {
        try {
            ExportResult result = RecipeExporter.export(directory, systems);
            logger.info(
                "Recipe export completed file={} skippedFile={} systems={} recipes={} skipped={}",
                result.file(), result.skippedFile(), result.systemCount(), result.recipeCount(), result.skippedCount()
            );
            StringBuilder message = new StringBuilder("Recipe export completed: ")
                .append(result.file())
                .append(" (").append(result.systemCount()).append(" systems, ")
                .append(result.recipeCount()).append(" recipes, ")
                .append(result.skippedCount()).append(" skipped")
                .append(formatSkipReasons(result.skipReasons())).append(')');
            if (result.skippedCount() > 0) {
                message.append("; skipped details: ").append(result.skippedFile());
            }
            server.execute(() -> source.sendSuccess(() -> Component.literal(message.toString()), false));
        } catch (Throwable throwable) {
            logger.error("Recipe export failed", throwable);
            server.execute(() -> source.sendFailure(Component.literal("Recipe export failed: " + throwable.getMessage())));
        }
    }

    private static String formatSkipReasons(Map<ResolveError, Integer> skipReasons) {
        if (skipReasons.isEmpty()) {
            return "";
        }
        return "; skipped reasons: "
            + skipReasons.entrySet().stream()
                .map(entry -> entry.getKey().name() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private static Set<String> splitIds(String ids) {
        Set<String> result = new HashSet<>();
        for (String id : ids.split(",")) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
