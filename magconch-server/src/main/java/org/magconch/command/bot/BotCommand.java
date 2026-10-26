package org.magconch.command.bot;

import com.mojang.brigadier.builder.ArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.magconch.command.CommandContext;
import org.magconch.command.RootNode;
import org.magconch.command.bot.subcommands.ActionCommand;
import org.magconch.command.bot.subcommands.ConfigCommand;
import org.magconch.command.bot.subcommands.CreateCommand;
import org.magconch.command.bot.subcommands.HelpCommand;
import org.magconch.command.bot.subcommands.ListCommand;
import org.magconch.command.bot.subcommands.LoadCommand;
import org.magconch.command.bot.subcommands.RemoveCommand;
import org.magconch.command.bot.subcommands.SaveCommand;

import static org.magconch.command.CommandUtils.registerPermissions;

public class BotCommand extends RootNode {
    public static final BotCommand INSTANCE = new BotCommand();
    private static final String PERM_BASE = "bukkit.command.bot";

    private BotCommand() {
        super("bot", PERM_BASE);
        this.children(
            HelpCommand::new,
            ListCommand::new,
            ConfigCommand::new,
            RemoveCommand::new,
            LoadCommand::new,
            SaveCommand::new,
            ActionCommand::new,
            CreateCommand::new
        );
    }

    @Override
    protected boolean execute(@NotNull CommandContext context) {
        HelpCommand.sendHelp(context.getSender());
        return true;
    }

    @Override
    protected ArgumentBuilder<CommandSourceStack, ?> compile() {
        registerPermissions(PERM_BASE, this.children);
        return super.compile();
    }

    public static boolean hasPermission(@NotNull CommandSender sender) {
        return sender.hasPermission(PERM_BASE) || sender.hasPermission(PERM_BASE + ".admin") || sender.isOp();
    }

    public static boolean hasPermission(@NotNull CommandSender sender, String subcommand) {
        return sender.hasPermission(PERM_BASE)
            || sender.hasPermission(PERM_BASE + ".*")
            || sender.hasPermission(PERM_BASE + "." + subcommand)
            || sender.hasPermission(PERM_BASE + ".admin")
            || sender.isOp();
    }
}
