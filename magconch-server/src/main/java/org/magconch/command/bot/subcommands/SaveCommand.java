package org.magconch.command.bot.subcommands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.magconch.bot.BotConfig;
import org.magconch.bot.BotList;
import org.magconch.bot.ServerBot;
import org.magconch.command.ArgumentNode;
import org.magconch.command.CommandContext;
import org.magconch.command.arguments.BotArgumentType;
import org.magconch.command.bot.BotSubcommand;
import org.magconch.event.bot.BotRemoveEvent;

import static io.papermc.paper.adventure.PaperAdventure.asAdventure;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.spaces;

public class SaveCommand extends BotSubcommand {

    public SaveCommand() {
        super("save");
        children(BotArgument::new);
    }

    @Override
    public boolean requires(@NotNull CommandSourceStack source) {
        return org.magconch.bot.BotConfig.canManualSaveAndLoad && super.requires(source);
    }

    private static class BotArgument extends ArgumentNode<ServerBot> {

        private BotArgument() {
            super("bot", BotArgumentType.bot());
        }

        @Override
        protected boolean execute(@NotNull CommandContext context) {
            ServerBot bot = context.getArgument(BotArgument.class);
            CommandSender sender = context.getSender();

            if (sender instanceof org.bukkit.entity.Player player && !sender.hasPermission("bukkit.command.bot.admin")) {
                if (bot.createPlayer != null && !bot.createPlayer.equals(player.getUniqueId())) {
                    sender.sendMessage(text("You can only save your own bot", NamedTextColor.RED));
                    return false;
                }
            }

            BotList botList = BotList.INSTANCE;

            boolean success = botList.removeBot(bot, BotRemoveEvent.RemoveReason.COMMAND, sender, true, false);
            if (success) {
                sender.sendMessage(join(spaces(),
                    text("Successfully saved bot", NamedTextColor.GRAY),
                    asAdventure(bot.getDisplayName()),
                    text("as " + bot.createState.fullName(), NamedTextColor.GRAY)
                ));
            } else {
                sender.sendMessage(text("Bot save canceled by a plugin", NamedTextColor.RED));
            }
            return success;
        }
    }
}
