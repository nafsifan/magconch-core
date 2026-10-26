package org.magconch.entity.bot.actions;

import org.magconch.bot.agent.actions.ServerDropAction;
import org.magconch.entity.bot.action.DropAction;

public class CraftDropAction extends CraftTimerBotAction<DropAction, ServerDropAction> implements DropAction {

    public CraftDropAction(ServerDropAction serverAction) {
        super(serverAction, CraftDropAction::new);
    }
}
