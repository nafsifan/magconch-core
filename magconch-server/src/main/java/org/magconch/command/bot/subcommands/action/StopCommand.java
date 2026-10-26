package org.magconch.command.bot.subcommands.action;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.network.chat.Component;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.magconch.bot.ServerBot;
import org.magconch.bot.agent.actions.AbstractBotAction;
import org.magconch.command.ArgumentNode;
import org.magconch.command.CommandContext;
import org.magconch.command.LiteralNode;
import org.magconch.command.bot.subcommands.ActionCommand;
import org.magconch.event.bot.BotActionStopEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static io.papermc.paper.adventure.PaperAdventure.asAdventure;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.spaces;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public class StopCommand extends LiteralNode {

    public StopCommand() {
        super("stop");
        children(StopIndexArgument::new);
        children(StopAll::new);
    }

    private static class StopIndexArgument extends ArgumentNode<Integer> {

        private StopIndexArgument() {
            super("index", IntegerArgumentType.integer(0));
        }

        @Override
        protected CompletableFuture<Suggestions> getSuggestions(CommandContext context, SuggestionsBuilder builder) throws CommandSyntaxException {
            ServerBot bot = ActionCommand.BotArgument.getBot(context);

            for (int i = 0; i < bot.getBotActions().size(); i++) {
                AbstractBotAction<?> action = bot.getBotActions().get(i);
                builder.suggest(String.valueOf(i), Component.literal(action.getName()));
            }

            return builder.buildFuture();
        }

        @Override
        protected boolean execute(CommandContext context) throws CommandSyntaxException {
            ServerBot bot = ActionCommand.BotArgument.getBot(context);
            CommandSender sender = context.getSender();

            if (sender instanceof org.bukkit.entity.Player player && !sender.hasPermission("bukkit.command.bot.admin")) {
                if (bot.createPlayer != null && !bot.createPlayer.equals(player.getUniqueId())) {
                    sender.sendMessage(text("You can only control your own bot", RED));
                    return false;
                }
            }

            int index = context.getArgument(StopIndexArgument.class);
            int maxIndex = bot.getBotActions().size() - 1;
            if (maxIndex < 0) {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().create();
            }
            if (index > maxIndex) {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.integerTooHigh().create(index, maxIndex);
            }

            AbstractBotAction<?> action = bot.getBotActions().get(index);
            BotActionStopEvent event = new BotActionStopEvent(
                bot.getBukkitEntity(), action.getName(), action.getUUID(), BotActionStopEvent.Reason.COMMAND, sender
            );
            event.callEvent();
            if (!event.isCancelled()) {
                action.stop(bot, BotActionStopEvent.Reason.COMMAND);
                bot.getBotActions().remove(index);
                sender.sendMessage(join(spaces(),
                    text("Already stopped", GRAY),
                    asAdventure(bot.getDisplayName()).append(text("'s", GRAY)),
                    text("action", GRAY),
                    text(action.getName(), AQUA).hoverEvent(showText(text(action.getActionDataString())))
                ));
            } else {
                sender.sendMessage(text("Action stop cancelled by a plugin", RED));
            }
            return true;
        }
    }

    private static class StopAll extends LiteralNode {

        private StopAll() {
            super("all");
        }

        @Override
        protected boolean execute(@NotNull CommandContext context) throws CommandSyntaxException {
            ServerBot bot = ActionCommand.BotArgument.getBot(context);
            CommandSender sender = context.getSender();

            if (sender instanceof org.bukkit.entity.Player player && !sender.hasPermission("bukkit.command.bot.admin")) {
                if (bot.createPlayer != null && !bot.createPlayer.equals(player.getUniqueId())) {
                    sender.sendMessage(text("You can only control your own bot", RED));
                    return false;
                }
            }

            List<AbstractBotAction<?>> actions = bot.getBotActions();
            if (actions.isEmpty()) {
                sender.sendMessage(text("This bot has no active actions", GRAY));
                return true;
            }

            Set<AbstractBotAction<?>> canceled = new HashSet<>();
            Set<AbstractBotAction<?>> forRemoval = new HashSet<>();
            for (AbstractBotAction<?> action : actions) {
                BotActionStopEvent event = new BotActionStopEvent(
                    bot.getBukkitEntity(), action.getName(), action.getUUID(), BotActionStopEvent.Reason.COMMAND, sender
                );
                event.callEvent();
                if (!event.isCancelled()) {
                    forRemoval.add(action);
                    action.stop(bot, BotActionStopEvent.Reason.COMMAND);
                } else {
                    canceled.add(action);
                }
            }
            bot.getBotActions().removeAll(forRemoval);

            if (canceled.isEmpty()) {
                sender.sendMessage(join(spaces(),
                    asAdventure(bot.getDisplayName()).append(text("'s", GRAY)),
                    text("action list cleared", GRAY)
                ));
            } else {
                sender.sendMessage(join(spaces(),
                    text("Tried to clear", GRAY),
                    asAdventure(bot.getDisplayName()).append(text("'s", GRAY)),
                    text("action list, but following actions' stop was canceled by plugin:", GRAY)
                ));
                for (AbstractBotAction<?> action : canceled) {
                    context.getSender().sendMessage(
                        text(action.getName(), AQUA).hoverEvent(showText(text(action.getActionDataString())))
                    );
                }
            }
            return true;
        }
    }
}
