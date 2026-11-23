package dev.vfyjxf.cirrus.internal.debug;

import com.mojang.brigadier.Command;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class CirrusDiagnosticsCommands {

    private CirrusDiagnosticsCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("cirrus")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("routingStats")
                    .then(Commands.literal("export")
                        .executes(context -> {
                            // TODO(planning-bridge): 规划统计改由新规划器诊断输出提供（旧路由已删除，待 bridge 接线）
                            context.getSource().sendSuccess(
                                () -> Component.literal("Cirrus planning stats unavailable: planning bridge not wired yet"),
                                false
                            );
                            return Command.SINGLE_SUCCESS;
                        }))
                    .executes(context -> {
                        // TODO(planning-bridge): 规划统计改由新规划器诊断输出提供（旧路由已删除，待 bridge 接线）
                        context.getSource().sendSuccess(
                            () -> Component.literal("Cirrus planning stats unavailable: planning bridge not wired yet"),
                            false
                        );
                        return Command.SINGLE_SUCCESS;
                    }))
        );
    }
}
