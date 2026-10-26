package org.magconch.bot.agent.actions;

import org.jetbrains.annotations.NotNull;
import org.magconch.bot.ServerBot;
import org.magconch.entity.bot.actions.CraftBotAction;
import org.magconch.entity.bot.actions.CraftDropAction;

public class ServerDropAction extends AbstractTimerBotAction<ServerDropAction> {

    public ServerDropAction() {
        super("drop");
    }

    @Override
    public boolean doTick(@NotNull ServerBot bot) {
        bot.dropAll(false);
        return true;
    }

    @Override
    public CraftBotAction<?, ServerDropAction> asCraft() {
        return new CraftDropAction(this);
    }
}
