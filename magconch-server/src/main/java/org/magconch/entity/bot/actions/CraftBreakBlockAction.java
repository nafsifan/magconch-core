package org.magconch.entity.bot.actions;

import org.magconch.bot.agent.actions.ServerBreakBlockAction;
import org.magconch.entity.bot.action.BreakBlockAction;

public class CraftBreakBlockAction extends CraftTimerBotAction<BreakBlockAction, ServerBreakBlockAction> implements BreakBlockAction {

    public CraftBreakBlockAction(ServerBreakBlockAction serverAction) {
        super(serverAction, CraftBreakBlockAction::new);
    }
}
