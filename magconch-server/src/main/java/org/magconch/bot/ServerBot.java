package org.magconch.bot;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.Stat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.portal.TeleportTransition;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * 原生服务端假人实体 (ServerBot)
 * 继承自 ServerPlayer，具备原生区块加载、刷怪与随机刻激活能力，屏蔽无效网络发包与统计归档。
 */
public class ServerBot extends ServerPlayer {

    public final @Nullable UUID creatorUUID;

    public ServerBot(
        final MinecraftServer server,
        final ServerLevel level,
        final GameProfile profile,
        final @Nullable UUID creatorUUID
    ) {
        super(server, level, profile, ClientInformation.createDefault());
        this.creatorUUID = creatorUUID;
        // 开启全部外层皮肤图层（披风除外：0x7E = 126）
        this.getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7E);
        // 标记为伪睡眠与已读终末之诗，防止阻碍跳过夜晚或掉入末地门卡死
        this.fauxSleeping = true;
        this.seenCredits = true;
    }

    // ==================== 物理、传送与权威模拟 ====================

    @Override
    public boolean isClientAuthoritative() {
        // 由服务端接管重力、击退、活塞弹射与摔落伤害计算
        return false;
    }

    @Override
    public boolean isReceivingWaypoints() {
        return false;
    }

    @Override
    public @Nullable ServerBot teleport(final TeleportTransition transition) {
        if (this.isSleeping() || this.isRemoved()) {
            return null;
        }
        if (!transition.asPassenger()) {
            this.removeVehicle();
        }

        ServerLevel fromLevel = this.level();
        ServerLevel toLevel = transition.newLevel();
        if (toLevel.dimension() == fromLevel.dimension()) {
            this.teleportSetPosition(PositionMoveRotation.of(transition), transition.relatives());
            transition.postTeleportTransition().onTransition(this);
            return this;
        }

        fromLevel.removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
        this.unsetRemoved();
        this.setServerLevel(toLevel);
        this.teleportSetPosition(PositionMoveRotation.of(transition), transition.relatives());
        toLevel.addDuringTeleport(this);
        this.stopUsingItem();
        transition.postTeleportTransition().onTransition(this);
        return this;
    }

    // ==================== Tick 驱动与生存状态维持 ====================

    @Override
    public void doTick() {
        if (!this.isAlive()) {
            this.die(this.damageSources().generic());
            return;
        }

        // 锁定满饱食度与水分，每秒缓慢恢复 0.5 生命值防止挂机饿死
        this.foodData.setFoodLevel(20);
        this.thirstData.setThirstValue(20);
        if (this.tickCount % 20 == 0 && this.getHealth() > 0.0F && this.getHealth() < this.getMaxHealth()) {
            this.setHealth(Math.min(this.getHealth() + 0.5F, this.getMaxHealth()));
        }

        super.doTick();
        this.level().getChunkSource().move(this);
    }

    // ==================== 死亡与移除生命周期 ====================

    @Override
    public void die(final DamageSource source) {
        if (this.isRemoved()) {
            return;
        }
        this.gameEvent(GameEvent.ENTITY_DIE);
        if (this.level().getGameRules().get(GameRules.SHOW_DEATH_MESSAGES)) {
            this.level().getServer().getPlayerList().broadcastSystemMessage(this.getCombatTracker().getDeathMessage(), false);
        }
        this.removeEntitiesOnShoulder();
        if (this.level().getGameRules().get(GameRules.FORGIVE_DEAD_PLAYERS)) {
            this.tellNeutralMobsThatIDied();
        }
        BotList.instance().removeBot(this);
    }

    // ==================== Tab 列表同步与统计屏蔽 ====================

    public void sendPlayerInfo(final ServerPlayer target) {
        target.connection.send(new ClientboundPlayerInfoUpdatePacket(
            EnumSet.of(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED
            ),
            List.of(this)
        ));
    }

    public void sendPlayerInfoToAll() {
        for (ServerPlayer player : this.level().getServer().getPlayerList().getPlayers()) {
            this.sendPlayerInfo(player);
        }
    }

    public void removeTabFromAll() {
        ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(List.of(this.getUUID()));
        for (ServerPlayer player : this.level().getServer().getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }

    @Override
    public int awardRecipes(final Collection<RecipeHolder<?>> recipes) {
        return 0;
    }

    @Override
    public int resetRecipes(final Collection<RecipeHolder<?>> recipes) {
        return 0;
    }

    @Override
    public void triggerRecipeCrafted(final RecipeHolder<?> recipe, final List<ItemStack> items) {
    }

    @Override
    public void awardKillScore(final Entity entity, final DamageSource damageSource) {
    }

    @Override
    public void awardStat(final Stat<?> stat, final int amount) {
    }

    @Override
    public void resetStat(final Stat<?> stat) {
    }

    // ==================== 虚拟空网络通道 ====================

    public static final class BotPacketListener extends ServerGamePacketListenerImpl {

        private static final net.minecraft.network.Connection DUMMY_CONNECTION = new net.minecraft.network.Connection(PacketFlow.SERVERBOUND) {
            @Override
            public void tick() {
            }

            @Override
            public boolean isConnected() {
                return true;
            }

            @Override
            public boolean isConnecting() {
                return false;
            }

            @Override
            public boolean isMemoryConnection() {
                return false;
            }

            @Override
            public void send(final Packet<?> packet, final @Nullable ChannelFutureListener listener, final boolean flush) {
            }
        };

        public BotPacketListener(final MinecraftServer server, final ServerBot bot) {
            super(server, DUMMY_CONNECTION, bot, CommonListenerCookie.createInitial(bot.getGameProfile(), false));
        }

        @Override
        public void tick() {
        }

        @Override
        public boolean isAcceptingMessages() {
            return true;
        }

        @Override
        public boolean hasClientLoaded() {
            return true;
        }

        @Override
        public void send(final Packet<?> packet, final @Nullable ChannelFutureListener listener) {
        }

        @Override
        public void disconnect(final DisconnectionDetails details) {
        }
    }
}
