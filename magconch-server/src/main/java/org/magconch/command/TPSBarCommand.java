package org.magconch.command;

import com.mojang.brigadier.CommandDispatcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import org.magconch.task.TPSBarTask;

import java.util.Collection;
import java.util.Collections;

/**
 * 原生 /tpsbar 指令处理器
 */
public class TPSBarCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tpsbar")
                .requires(listener -> !listener.getBukkitSender().isPermissionSet("bukkit.command.tpsbar") || listener.getBukkitSender().hasPermission("bukkit.command.tpsbar"))
                .executes(context -> execute(context.getSource(), Collections.singleton(context.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players())
                        .requires(listener -> listener.hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER, "bukkit.command.tpsbar.other"))
                        .executes(context -> execute(context.getSource(), EntityArgument.getPlayers(context, "targets")))
                )
        );
    }

    private static int execute(CommandSourceStack sender, Collection<ServerPlayer> targets) {
        for (ServerPlayer player : targets) {
            boolean result = TPSBarTask.instance().togglePlayer(player.getBukkitEntity());
            player.tpsBar(result);

            Component output;
            if (sender.getEntity() == player) {
                output = MiniMessage.miniMessage().deserialize(
                        result ? "<gray>TPSBar <green>已开启</green></gray>" : "<gray>TPSBar <red>已关闭</red></gray>"
                );
            } else {
                output = MiniMessage.miniMessage().deserialize(
                        result ? "<gray>已将 <gold><target></gold> 的 TPSBar <green>开启</green></gray>"
                               : "<gray>已将 <gold><target></gold> 的 TPSBar <red>关闭</red></gray>",
                        Placeholder.parsed("target", player.getScoreboardName())
                );
            }

            sender.getBukkitSender().sendMessage(output);
        }
        return targets.size();
    }
}
