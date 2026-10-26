package org.magconch.command.bot;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.jetbrains.annotations.NotNull;
import org.magconch.command.LiteralNode;

public abstract class BotSubcommand extends LiteralNode {
    protected BotSubcommand(String name) {
        super(name);
    }

    @Override
    public boolean requires(@NotNull CommandSourceStack source) {
        return BotCommand.hasPermission(source.getSender(), this.name);
    }
}
