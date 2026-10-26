package org.magconch.entity.bot.actions;

import org.jetbrains.annotations.NotNull;
import org.magconch.bot.ServerBot;
import org.magconch.bot.agent.actions.ServerUseItemAction;
import org.magconch.entity.bot.action.UseItemAction;

public class CraftUseItemAction extends CraftTimerBotAction<UseItemAction, ServerUseItemAction> implements UseItemAction {

    public CraftUseItemAction(ServerUseItemAction serverAction) {
        super(serverAction, CraftUseItemAction::new);
    }

    public boolean doTick(@NotNull ServerBot bot) {
        return serverAction.doTick(bot);
    }

    @Override
    public int getUseTickTimeout() {
        return serverAction.getUseTickTimeout();
    }

    @Override
    public CraftUseItemAction setUseTickTimeout(int timeout) {
        serverAction.setUseTickTimeout(timeout);
        return this;
    }
}
