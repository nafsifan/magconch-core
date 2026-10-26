package org.magconch.entity.bot.actions;

import org.magconch.bot.agent.actions.ServerSwapAction;
import org.magconch.entity.bot.action.SwapAction;

public class CraftSwapAction extends CraftBotAction<SwapAction, ServerSwapAction> implements SwapAction {

    public CraftSwapAction(ServerSwapAction serverAction) {
        super(serverAction, CraftSwapAction::new);
    }
}
