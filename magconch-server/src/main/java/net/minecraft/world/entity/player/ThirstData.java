package net.minecraft.world.entity.player;

import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class ThirstData {
    public static final int MAX_VALUE = 20;

    private final Player player;
    private int thirstValue = MAX_VALUE;
    private float thirstExhaustion;

    public ThirstData(final Player player) {
        this.player = player;
    }

    public int getThirstValue() {
        return this.thirstValue;
    }

    public boolean needsWater() {
        return this.thirstValue < MAX_VALUE;
    }

    public void setThirstValue(final int value) {
        this.thirstValue = Mth.clamp(value, 0, MAX_VALUE);
    }

    public void addThirstValue(final int add) {
        this.setThirstValue(this.thirstValue + add);
        if (add > 0) {
            this.thirstExhaustion = 0.0F;
        }
    }

    public float getThirstExhaustion() {
        return this.thirstExhaustion;
    }

    public void addExhaustion(final float exhaustion) {
        final Level level = this.player.level();
        if (level.getBiome(this.player.blockPosition()).value().climateSettings.downfall() <= 0.0F) {
            this.thirstExhaustion += exhaustion * 1.5F;
        } else {
            this.thirstExhaustion += exhaustion;
        }
    }

    /**
     * 将口渴值映射为客户端气泡数 (1-10个气泡).
     * 保持最低返回值 >= 3，防止原版 LivingEntity 底层误判为负数溺水.
     */
    public int getThirstProgress() {
        final int bubbles = Mth.ceil(this.thirstValue / 2.0);
        if (bubbles <= 0) {
            return 3;
        }
        return (300 / 10) * (bubbles - 1) + 8;
    }

    public boolean isThirsty() {
        return this.thirstValue <= 0 && !this.player.getAbilities().invulnerable;
    }

    public void tick() {
        if (this.player.getAbilities().invulnerable) {
            return;
        }

        final boolean wet = this.player.isInWaterOrRain();
        if (!wet) {
            // 移动消耗，挂机静止时不增加任何消耗
            if (this.player.isSprinting()) {
                this.addExhaustion(0.015F);
            } else if (this.player.getDeltaMovement().horizontalDistanceSqr() > 0.001) {
                this.addExhaustion(0.005F);
            }
        }

        if (this.thirstExhaustion >= 4.0F) {
            this.thirstExhaustion -= 4.0F;
            if (this.player.level().getDifficulty() != Difficulty.PEACEFUL) {
                this.addThirstValue(-1);
            }
        }

        // 口渴较重 (<= 6 即 3 颗气泡以下) 时赋予虚弱
        if (this.thirstValue <= 6 && this.player.tickCount % 40 == 0) {
            this.player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0, false, false, true));
        }

        // 在水下发送真实空气值，陆地上发送伪装口渴气泡值
        final boolean inWater = this.player.isEyeInFluid(FluidTags.WATER);
        if (inWater) {
            this.player.sendAirSupply(this.player.realAir);
        } else {
            this.player.sendAirSupply(this.getThirstProgress());
        }
    }

    public void readAdditionalSaveData(final ValueInput input) {
        this.thirstValue = input.getIntOr("magconch.thirst_value", MAX_VALUE);
        this.thirstExhaustion = input.getFloatOr("magconch.thirst_exhaustion", 0.0F);
    }

    public void addAdditionalSaveData(final ValueOutput output) {
        output.putInt("magconch.thirst_value", this.thirstValue);
        output.putFloat("magconch.thirst_exhaustion", this.thirstExhaustion);
    }
}
