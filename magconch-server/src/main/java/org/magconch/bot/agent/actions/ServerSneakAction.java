package org.magconch.bot.agent.actions;

import org.jetbrains.annotations.NotNull;
import org.magconch.bot.ServerBot;
import org.magconch.entity.bot.actions.CraftBotAction;
import org.magconch.entity.bot.actions.CraftSneakAction;
import org.magconch.event.bot.BotActionStopEvent;

public class ServerSneakAction extends AbstractStateBotAction<ServerSneakAction> {

    public ServerSneakAction() {
        super("sneak");
    }

    @Override
    public boolean doTick(@NotNull ServerBot bot) {
        if (bot.isShiftKeyDown()) {
            return false;
        }

        bot.setShiftKeyDown(true);
        return true;
    }

    @Override
    public void stop(@NotNull ServerBot bot, BotActionStopEvent.Reason reason) {
        super.stop(bot, reason);
        bot.setShiftKeyDown(false);
    }

    @Override
    public CraftBotAction<?, ServerSneakAction> asCraft() {
        return new CraftSneakAction(this);
    }
}
