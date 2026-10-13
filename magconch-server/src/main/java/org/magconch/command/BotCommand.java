package org.magconch.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.configuration.GlobalConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.command.CommandSender;
import org.magconch.bot.BotList;
import org.magconch.bot.ServerBot;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 原生 /bot 假人指令处理器
 * 提供 create、remove、list 三个子指令及细粒度权限控制。
 */
public class BotCommand {

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bot")
            .requires(BotCommand::hasAnyBotPermission)
            .then(Commands.literal("create")
                .requires(source -> source.getBukkitSender().hasPermission("bukkit.command.bot.create"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> executeCreate(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                )
            )
            .then(Commands.literal("remove")
                .requires(source -> source.getBukkitSender().hasPermission("bukkit.command.bot.remove"))
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests((ctx, builder) -> suggestRemovableBots(ctx.getSource(), builder))
                    .executes(ctx -> executeRemove(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                )
            )
            .then(Commands.literal("list")
                .requires(source -> source.getBukkitSender().hasPermission("bukkit.command.bot.list"))
                .executes(ctx -> executeList(ctx.getSource()))
            )
        );
    }

    // ==================== 子指令业务实现 ====================

    private static boolean hasAnyBotPermission(final CommandSourceStack source) {
        if (!GlobalConfiguration.get().magconch.bot.enable) {
            return false;
        }
        CommandSender sender = source.getBukkitSender();
        return sender.hasPermission("bukkit.command.bot")
            || sender.hasPermission("bukkit.command.bot.create")
            || sender.hasPermission("bukkit.command.bot.remove")
            || sender.hasPermission("bukkit.command.bot.list")
            || sender.hasPermission("bukkit.command.bot.admin");
    }

    private static int executeCreate(final CommandSourceStack source, final String name) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CommandSender sender = source.getBukkitSender();
        GlobalConfiguration.MagconchConfig.Bot config = GlobalConfiguration.get().magconch.bot;
        if (!config.enable) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>假人功能当前已关闭</red>"));
            return 0;
        }

        if (name.isEmpty() || name.length() > 16 || !name.matches("[a-zA-Z0-9_]+")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<red>假人名称无效（需为 1~16 位字母、数字或下划线）</red>"
            ));
            return 0;
        }

        boolean isAdmin = sender.hasPermission("bukkit.command.bot.admin");
        BotList botList = BotList.instance();
        if (!isAdmin && config.playerLimit > 0 && botList.countByCreator(player.getUUID()) >= config.playerLimit) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<red>你创建的假人数量已达个人上限 (<yellow><max></yellow>)</red>",
                Placeholder.unparsed("max", String.valueOf(config.playerLimit))
            ));
            return 0;
        }

        if (!botList.isNameAvailable(name)) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<red>名称 <yellow><name></yellow> 已被占用或与真实玩家重名</red>",
                Placeholder.unparsed("name", name)
            ));
            return 0;
        }

        botList.createBotAsync(
            player.level(),
            player.getX(),
            player.getY(),
            player.getZ(),
            player.getYRot(),
            player.getXRot(),
            name,
            player.getUUID(),
            isAdmin,
            bot -> {
                if (bot == null) {
                    sender.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<red>假人 <yellow><name></yellow> 创建失败（名称冲突或已达配额上限）</red>",
                        Placeholder.unparsed("name", name)
                    ));
                }
            }
        );
        return 1;
    }

    private static int executeRemove(final CommandSourceStack source, final String targetName) {
        CommandSender sender = source.getBukkitSender();
        boolean isAdmin = sender.hasPermission("bukkit.command.bot.admin");
        BotList botList = BotList.instance();

        ServerBot bot = botList.getBotByName(targetName);
        if (bot == null) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<red>未找到在线假人 <yellow><name></yellow></red>",
                Placeholder.unparsed("name", targetName)
            ));
            return 0;
        }

        UUID senderUUID = source.getEntity() instanceof ServerPlayer sp ? sp.getUUID() : null;
        if (!isAdmin && (senderUUID == null || !senderUUID.equals(bot.creatorUUID))) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>你只能移除自己创建的假人</red>"));
            return 0;
        }

        botList.removeBot(bot);
        return 1;
    }

    private static int executeList(final CommandSourceStack source) {
        CommandSender sender = source.getBukkitSender();
        List<ServerBot> bots = BotList.instance().bots;
        if (bots.isEmpty()) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<gray>当前没有在线的假人</gray>"));
            return 0;
        }

        sender.sendMessage(MiniMessage.miniMessage().deserialize(
            "<gray>当前在线假人 (<green><count></green>)：</gray>",
            Placeholder.unparsed("count", String.valueOf(bots.size()))
        ));
        for (ServerBot bot : bots) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                "<gray>- <gold><name></gold> [<yellow><world></yellow> <aqua><x></aqua>, <aqua><y></aqua>, <aqua><z></aqua>]</gray>",
                Placeholder.unparsed("name", bot.getScoreboardName()),
                Placeholder.unparsed("world", bot.level().getWorld().getName()),
                Placeholder.unparsed("x", String.valueOf(bot.getBlockX())),
                Placeholder.unparsed("y", String.valueOf(bot.getBlockY())),
                Placeholder.unparsed("z", String.valueOf(bot.getBlockZ()))
            ));
        }
        return bots.size();
    }

    private static CompletableFuture<Suggestions> suggestRemovableBots(final CommandSourceStack source, final SuggestionsBuilder builder) {
        boolean isAdmin = source.getBukkitSender().hasPermission("bukkit.command.bot.admin");
        UUID senderUUID = source.getEntity() instanceof ServerPlayer sp ? sp.getUUID() : null;
        String remaining = builder.getRemainingLowerCase();

        for (ServerBot bot : BotList.instance().bots) {
            if (isAdmin || (senderUUID != null && senderUUID.equals(bot.creatorUUID))) {
                String botName = bot.getScoreboardName();
                if (botName.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                    builder.suggest(botName);
                }
            }
        }
        return builder.buildFuture();
    }
}
