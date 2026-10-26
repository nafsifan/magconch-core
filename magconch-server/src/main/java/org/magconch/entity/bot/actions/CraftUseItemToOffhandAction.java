package org.magconch.entity.bot.actions;

import org.magconch.bot.agent.actions.ServerUseItemToOffhandAction;
import org.magconch.entity.bot.action.UseItemToOffhandAction;

public class CraftUseItemToOffhandAction extends CraftTimerBotAction<UseItemToOffhandAction, ServerUseItemToOffhandAction> implements UseItemToOffhandAction {

    public CraftUseItemToOffhandAction(ServerUseItemToOffhandAction serverAction) {
        super(serverAction, CraftUseItemToOffhandAction::new);
    }

    @Override
    public int getUseTickTimeout() {
        return serverAction.getUseTickTimeout();
    }

    @Override
    public CraftUseItemToOffhandAction setUseTickTimeout(int timeout) {
        serverAction.setUseTickTimeout(timeout);
        return this;
    }
}