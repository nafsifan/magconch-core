package org.magconch.entity.bot;

import com.google.common.base.Preconditions;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.magconch.bot.BotList;
import org.magconch.bot.ServerBot;
import org.magconch.bot.agent.actions.AbstractBotAction;
import org.magconch.entity.bot.action.BotAction;
import org.magconch.entity.bot.actions.CraftBotAction;
import org.magconch.event.bot.BotActionStopEvent;
import org.magconch.event.bot.BotRemoveEvent;

import java.util.UUID;

public class CraftBot extends CraftPlayer implements Bot {

    public CraftBot(CraftServer server, ServerBot entity) {
        super(server, entity);
    }

    @Override
    public String getSkinName() {
        return this.getHandle().createState.skinName();
    }

    @Override
    public @NotNull String getRawName() {
        return this.getHandle().createState.rawName();
    }

    @Override
    public @Nullable UUID getCreatePlayerUUID() {
        return this.getHandle().createPlayer;
    }

    @Override
    public <T extends BotAction<T>> void addAction(@NotNull T action) {
        if (action instanceof CraftBotAction<?, ?> act) {
            this.getHandle().addBotAction(act.getHandle(), null);
        } else {
            throw new IllegalArgumentException("Action " + action.getClass().getName() + " is not a valid BotAction type!");
        }
    }

    public void addAction(@NotNull AbstractBotAction<?> action) {
        this.getHandle().addBotAction(action, null);
    }

    @Override
    public BotAction<?> getAction(int index) {
        return this.getHandle().getBotActions().get(index).asCraft();
    }

    @Override
    public int getActionSize() {
        return this.getHandle().getBotActions().size();
    }

    @Override
    public void stopAction(int index) {
        this.getHandle().getBotActions().get(index).stop(this.getHandle(), BotActionStopEvent.Reason.PLUGIN);
    }

    @Override
    public void stopAllActions() {
        for (AbstractBotAction<?> action : this.getHandle().getBotActions()) {
            action.stop(this.getHandle(), BotActionStopEvent.Reason.PLUGIN);
        }
    }

    @Override
    public boolean remove(boolean save) {
        return BotList.INSTANCE.removeBot(this.getHandle(), BotRemoveEvent.RemoveReason.PLUGIN, null, save, false);
    }

    @Override
    public boolean remove(boolean save, boolean resume) {
        return BotList.INSTANCE.removeBot(this.getHandle(), BotRemoveEvent.RemoveReason.PLUGIN, null, save, resume);
    }

    @Override
    public boolean teleport(Location location, PlayerTeleportEvent.@NotNull TeleportCause cause, io.papermc.paper.entity.TeleportFlag @NotNull ... flags) {
        Preconditions.checkArgument(location != null, "location cannot be null");
        Preconditions.checkState(location.getWorld().equals(this.getWorld()), "[Leaves] Fakeplayers do not support changing world, Please use leaves fakeplayer-api instead!");
        return super.teleport(location, cause, flags);
    }

    @Override
    public ServerBot getHandle() {
        return (ServerBot) entity;
    }

    public void setHandle(final ServerBot entity) {
        super.setHandle(entity);
    }

    @Override
    public boolean hasMetadata(@NotNull String metadataKey) {
        return "NPC".equalsIgnoreCase(metadataKey) || super.hasMetadata(metadataKey);
    }

    @Override
    public @NotNull java.util.List<org.bukkit.metadata.MetadataValue> getMetadata(@NotNull String metadataKey) {
        if ("NPC".equalsIgnoreCase(metadataKey)) {
            return java.util.List.of(new org.bukkit.metadata.FixedMetadataValue(org.magconch.util.MinecraftInternalPlugin.INSTANCE, true));
        }
        return super.getMetadata(metadataKey);
    }

    @Override
    public String toString() {
        return "CraftBot{" + "name=" + getName() + '}';
    }
}
