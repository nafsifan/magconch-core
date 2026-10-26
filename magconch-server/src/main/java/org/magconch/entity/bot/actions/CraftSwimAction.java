package org.magconch.entity.bot.actions;

import org.magconch.bot.agent.actions.ServerSwimAction;
import org.magconch.entity.bot.action.SwimAction;

public class CraftSwimAction extends CraftBotAction<SwimAction, ServerSwimAction> implements SwimAction {

    public CraftSwimAction(ServerSwimAction serverAction) {
        super(serverAction, CraftSwimAction::new);
    }
}
