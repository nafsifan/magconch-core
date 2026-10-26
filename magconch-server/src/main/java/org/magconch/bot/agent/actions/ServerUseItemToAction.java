package org.magconch.bot.agent.actions;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.magconch.bot.ServerBot;
import org.magconch.entity.bot.actions.CraftBotAction;
import org.magconch.entity.bot.actions.CraftUseItemToAction;

public class ServerUseItemToAction extends AbstractUseBotAction<ServerUseItemToAction> {

    public ServerUseItemToAction() {
        super("use_to");
    }

    @Override
    protected boolean interact(@NotNull ServerBot bot) {
        EntityHitResult hitResult = bot.getEntityHitResult();
        return useItemTo(bot, hitResult, InteractionHand.MAIN_HAND).consumesAction();
    }

    public static InteractionResult useItemTo(ServerBot bot, EntityHitResult hitResult, InteractionHand hand) {
        if (hitResult == null) {
            return InteractionResult.FAIL;
        }

        Entity entity = hitResult.getEntity();
        if (!bot.level().getWorldBorder().isWithinBounds(entity.blockPosition())) {
            return InteractionResult.FAIL;
        }

        Vec3 vec3 = hitResult.getLocation().subtract(entity.getX(), entity.getY(), entity.getZ());
        bot.updateItemInHand(hand);
        InteractionResult interactionResult = bot.interactOn(entity, hand, hitResult.getLocation());

        if (shouldSwing(interactionResult)) {
            bot.swing(hand);
        }

        return interactionResult;
    }

    @Override
    public CraftBotAction<?, ServerUseItemToAction> asCraft() {
        return new CraftUseItemToAction(this);
    }
}
