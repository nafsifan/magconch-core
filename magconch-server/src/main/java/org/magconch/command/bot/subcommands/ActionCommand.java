package org.magconch.command.bot.subcommands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.jetbrains.annotations.NotNull;
import org.magconch.bot.BotConfig;
import org.magconch.bot.ServerBot;
import org.magconch.command.ArgumentNode;
import org.magconch.command.CommandContext;
import org.magconch.command.arguments.BotArgumentType;
import org.magconch.command.bot.BotSubcommand;
import org.magconch.command.bot.subcommands.action.ListCommand;
import org.magconch.command.bot.subcommands.action.StartCommand;
import org.magconch.command.bot.subcommands.action.StopCommand;

public class ActionCommand extends BotSubcommand {

    public ActionCommand() {
        super("action");
        children(BotArgument::new);
    }

    @Override
    public boolean requires(@NotNull CommandSourceStack source) {
        return org.magconch.bot.BotConfig.canUseAction && super.requires(source);
    }

    public static class BotArgument extends ArgumentNode<ServerBot> {

        private BotArgument() {
            super("bot", BotArgumentType.bot());
            children(
                StartCommand::new,
                StopCommand::new,
                ListCommand::new
            );
        }

        public static @NotNull ServerBot getBot(@NotNull CommandContext context) {
            return context.getArgument(BotArgument.class);
        }
    }
}