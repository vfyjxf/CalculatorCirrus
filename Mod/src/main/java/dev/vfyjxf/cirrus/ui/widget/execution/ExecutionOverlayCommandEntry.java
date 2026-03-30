package dev.vfyjxf.cirrus.ui.widget.execution;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Client command entry for opening the execution overlay sandbox.
 */
public final class ExecutionOverlayCommandEntry {

    private static final String commandPath = "cirrus exec_overlay";

    private ExecutionOverlayCommandEntry() {
    }

    public static String debugCommandPath() {
        return commandPath;
    }

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(root());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("cirrus")
            .then(Commands.literal("exec_overlay").executes(context -> {
                openSandbox();
                return Command.SINGLE_SUCCESS;
            }));
    }

    private static void openSandbox() {
        Minecraft.getInstance().setScreen(new ExecutionOverlaySandboxScreen());
    }
}
