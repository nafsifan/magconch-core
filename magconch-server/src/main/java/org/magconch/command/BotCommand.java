package org.magconch.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

/**
 * 假人指令注册入口，转发至 brigadier 树节点管理器
 */
public class BotCommand {

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        org.magconch.command.bot.BotCommand.INSTANCE.register();
    }
}
