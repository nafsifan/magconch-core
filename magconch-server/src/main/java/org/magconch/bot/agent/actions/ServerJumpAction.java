package org.magconch.bot.agent.actions;

import org.jetbrains.annotations.NotNull;
import org.magconch.bot.ServerBot;
import org.magconch.entity.bot.actions.CraftBotAction;
import org.magconch.entity.bot.actions.CraftJumpAction;

public class ServerJumpAction extends AbstractTimerBotAction<ServerJumpAction> {

    public ServerJumpAction() {
        super("jump");
    }

    @Override
    public boolean doTick(@NotNull ServerBot bot) {
        if (!bot.onGround()) {
            return false;
        } else {
            bot.jumpFromGround();
        }
        return true;
    }

    @Override
    public CraftBotAction<?, ServerJumpAction> asCraft() {
        return new CraftJumpAction(this);
    }
}
