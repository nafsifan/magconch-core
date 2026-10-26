package org.magconch.bot.agent.actions;

import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.NotNull;
import org.magconch.bot.ServerBot;
import org.magconch.entity.bot.actions.CraftBotAction;
import org.magconch.entity.bot.actions.CraftUseItemOffhandAction;

import static org.magconch.bot.agent.actions.ServerUseItemAction.useItem;

public class ServerUseItemOffhandAction extends AbstractUseBotAction<ServerUseItemOffhandAction> {

    public ServerUseItemOffhandAction() {
        super("use_offhand");
    }

    @Override
    protected boolean interact(@NotNull ServerBot bot) {
        return useItem(bot, InteractionHand.OFF_HAND).consumesAction();
    }

    @Override
    public CraftBotAction<?, ServerUseItemOffhandAction> asCraft() {
        return new CraftUseItemOffhandAction(this);
    }
}
