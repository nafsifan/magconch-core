package org.magconch.command.bot.subcommands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.nbt.CompoundTag;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.magconch.bot.BotConfig;
import org.magconch.bot.BotList;
import org.magconch.bot.ServerBot;
import org.magconch.command.ArgumentNode;
import org.magconch.command.CommandContext;
import org.magconch.command.bot.BotSubcommand;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static io.papermc.paper.adventure.PaperAdventure.asAdventure;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.spaces;

public class LoadCommand extends BotSubcommand {

    public LoadCommand() {
        super("load");
        children(BotNameArgument::new);
    }

    @Override
    public boolean requires(@NotNull CommandSourceStack source) {
        return org.magconch.bot.BotConfig.canManualSaveAndLoad && super.requires(source);
    }

    private static class BotNameArgument extends ArgumentNode<String> {

        private BotNameArgument() {
            super("bot_name", StringArgumentType.word());
        }

        @Override
        protected boolean execute(@NotNull CommandContext context) throws CommandSyntaxException {
            String botName = context.getArgument(BotNameArgument.class);
            BotList botList = BotList.INSTANCE;
            CommandSender sender = context.getSender();
            if (!botList.getManualSavedBotList().contains(botName.toLowerCase(Locale.ROOT))) {
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().create();
            }
            if (botList.getBotByName(botName) != null) {
                sender.sendMessage(text("Bot with name " + botName + " already exists!", NamedTextColor.RED));
                return false;
            }

            if (sender instanceof org.bukkit.entity.Player player && !sender.hasPermission("bukkit.command.bot.admin")) {
                int playerLimit = io.papermc.paper.configuration.GlobalConfiguration.get().magconch.bot.playerLimit;
                if (playerLimit > 0 && botList.countByCreator(player.getUniqueId()) >= playerLimit) {
                    sender.sendMessage(text("You have reached the personal bot limit (" + playerLimit + ")", NamedTextColor.RED));
                    return false;
                }
            }

            ServerBot bot = botList.loadNewManualSavedBot(botName);
            if (bot == null) {
                sender.sendMessage(text("Failed to load bot, please check log", NamedTextColor.RED));
                return false;
            }
            sender.sendMessage(join(spaces(), text("Successfully loaded bot", NamedTextColor.GRAY), asAdventure(bot.getDisplayName())));
            return true;
        }

        @Override
        protected CompletableFuture<Suggestions> getSuggestions(CommandContext context, @NotNull SuggestionsBuilder builder) {
            BotList botList = BotList.INSTANCE;
            CompoundTag list = botList.getManualSavedBotList();
            Set<String> bots = list.keySet();
            if (bots.isEmpty()) {
                return builder
                    .suggest("<NO SAVED BOT EXISTS>", net.minecraft.network.chat.Component.literal("There are no bots saved before, save one first."))
                    .buildFuture();
            }
            bots.forEach(key -> builder.suggest(list.getCompoundOrEmpty(key).getString("name").orElseThrow()));
            return builder.buildFuture();
        }
    }
}
