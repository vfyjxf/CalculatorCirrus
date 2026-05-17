package dev.vfyjxf.cirrus.internal.debug;

import com.mojang.brigadier.Command;
import dev.vfyjxf.cirrus.recipe.routing.RecipePathSettings;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class CirrusDiagnosticsCommands {
    private static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus Diagnostics");
    private static final DateTimeFormatter fileTimestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private CirrusDiagnosticsCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("cirrus")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("routingStats")
                    .then(Commands.literal("export")
                        .executes(context -> {
                            RecipePathSettings.RoutingDiagnostics diagnostics = RecipePathSettings.calculatorRoutingDiagnostics();
                            Path output = exportRoutingStats(diagnostics);
                            logger.info("routing.stats.export file={} {}", output, diagnostics.detailLine());
                            context.getSource().sendSuccess(
                                () -> Component.literal("Cirrus routing stats exported: " + output),
                                false
                            );
                            return Command.SINGLE_SUCCESS;
                        }))
                    .executes(context -> {
                        RecipePathSettings.RoutingDiagnostics diagnostics = RecipePathSettings.calculatorRoutingDiagnostics();
                        String summary = diagnostics.summaryLine();
                        logger.info("routing.stats {}", diagnostics.detailLine());
                        context.getSource().sendSuccess(() -> Component.literal(summary), false);
                        return Command.SINGLE_SUCCESS;
                    }))
        );
    }

    private static Path exportRoutingStats(RecipePathSettings.RoutingDiagnostics diagnostics) {
        Path directory = FMLPaths.GAMEDIR.get().resolve("cirrus-diagnostics");
        String fileName = "routing-stats-" + LocalDateTime.now().format(fileTimestamp) + ".txt";
        Path output = directory.resolve(fileName);
        try {
            Files.createDirectories(directory);
            Files.writeString(output, routingStatsReport(diagnostics), StandardCharsets.UTF_8);
            return output;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to export Cirrus routing stats to " + output, exception);
        }
    }

    private static String routingStatsReport(RecipePathSettings.RoutingDiagnostics diagnostics) {
        String lineSeparator = System.lineSeparator();
        return "CalculatorCirrus routing diagnostics" + lineSeparator
            + "generatedAt=" + LocalDateTime.now() + lineSeparator
            + "summary=" + diagnostics.summaryLine() + lineSeparator
            + "detail=" + diagnostics.detailLine() + lineSeparator;
    }
}
