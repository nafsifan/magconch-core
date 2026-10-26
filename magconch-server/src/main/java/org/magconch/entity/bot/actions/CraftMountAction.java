package org.magconch.entity.bot.actions;

import org.magconch.bot.agent.actions.ServerMountAction;
import org.magconch.entity.bot.action.MountAction;

public class CraftMountAction extends CraftBotAction<MountAction, ServerMountAction> implements MountAction {

    public CraftMountAction(ServerMountAction serverAction) {
        super(serverAction, CraftMountAction::new);
    }
}
