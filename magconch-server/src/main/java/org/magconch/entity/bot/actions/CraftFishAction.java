package org.magconch.entity.bot.actions;

import org.magconch.bot.agent.actions.ServerFishAction;
import org.magconch.entity.bot.action.FishAction;

public class CraftFishAction extends CraftTimerBotAction<FishAction, ServerFishAction> implements FishAction {

    public CraftFishAction(ServerFishAction serverAction) {
        super(serverAction, CraftFishAction::new);
    }
}
