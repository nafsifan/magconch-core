package org.magconch.entity.bot.actions;

import org.magconch.bot.agent.actions.ServerAttackAction;
import org.magconch.entity.bot.action.AttackAction;

public class CraftAttackAction extends CraftTimerBotAction<AttackAction, ServerAttackAction> implements AttackAction {

    public CraftAttackAction(ServerAttackAction serverAction) {
        super(serverAction, CraftAttackAction::new);
    }
}
