package org.magconch.entity.bot.actions;

import org.magconch.bot.agent.actions.ServerJumpAction;
import org.magconch.entity.bot.action.JumpAction;

public class CraftJumpAction extends CraftTimerBotAction<JumpAction, ServerJumpAction> implements JumpAction {

    public CraftJumpAction(ServerJumpAction serverAction) {
        super(serverAction, CraftJumpAction::new);
    }
}
