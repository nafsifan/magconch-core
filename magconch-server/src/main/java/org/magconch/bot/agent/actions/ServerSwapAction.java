package org.magconch.bot.agent.actions;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.magconch.bot.ServerBot;
import org.magconch.entity.bot.actions.CraftBotAction;
import org.magconch.entity.bot.actions.CraftSwapAction;

public class ServerSwapAction extends AbstractBotAction<ServerSwapAction> {

    public ServerSwapAction() {
        super("swap");
    }

    @Override
    public boolean doTick(@NotNull ServerBot bot) {
        ItemStack mainHandItem = bot.getMainHandItem();
        ItemStack offHandItem = bot.getOffhandItem();
        bot.setItemInHand(InteractionHand.MAIN_HAND, offHandItem);
        bot.setItemInHand(InteractionHand.OFF_HAND, mainHandItem);
        return true;
    }

    @Override
    public CraftBotAction<?, ServerSwapAction> asCraft() {
        return new CraftSwapAction(this);
    }
}
