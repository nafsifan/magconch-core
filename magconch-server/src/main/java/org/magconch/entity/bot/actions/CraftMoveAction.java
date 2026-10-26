package org.magconch.entity.bot.actions;

import org.magconch.bot.agent.actions.ServerMoveAction;
import org.magconch.entity.bot.action.MoveAction;

public class CraftMoveAction extends CraftBotAction<MoveAction, ServerMoveAction> implements MoveAction {

    public CraftMoveAction(ServerMoveAction serverAction) {
        super(serverAction, CraftMoveAction::new);
    }

    @Override
    public MoveDirection getDirection() {
        return serverAction.getDirection();
    }

    @Override
    public MoveAction setDirection(MoveDirection direction) {
        serverAction.setDirection(direction);
        return this;
    }
}
