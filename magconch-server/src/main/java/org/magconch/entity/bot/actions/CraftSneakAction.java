package org.magconch.entity.bot.actions;

import org.magconch.bot.agent.actions.ServerSneakAction;
import org.magconch.entity.bot.action.SneakAction;

public class CraftSneakAction extends CraftBotAction<SneakAction, ServerSneakAction> implements SneakAction {

    public CraftSneakAction(ServerSneakAction serverAction) {
        super(serverAction, CraftSneakAction::new);
    }
}
