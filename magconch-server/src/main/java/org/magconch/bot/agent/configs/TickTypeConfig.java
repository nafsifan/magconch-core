package org.magconch.bot.agent.configs;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.magconch.bot.BotConfig;
import org.magconch.bot.ServerBot;
import org.magconch.command.CommandContext;
import org.magconch.command.arguments.EnumArgumentType;

import java.util.Locale;

public class TickTypeConfig extends AbstractBotConfig<ServerBot.TickType> {
    private ServerBot.TickType value;

    public TickTypeConfig() {
        super("tick_type", EnumArgumentType.fromEnum(ServerBot.TickType.class));
        this.value = org.magconch.bot.BotConfig.InGame.tickType;
    }

    @Override
    public ServerBot.TickType parseFromCommand(@NotNull CommandContext context) {
        return context.getArgument("tick_type", ServerBot.TickType.class);
    }

    @Override
    public ServerBot.TickType getValue() {
        return value;
    }

    @Override
    public void setValue(ServerBot.TickType value) throws IllegalArgumentException {
        this.value = value;
    }

    @Override
    @NotNull
    public CompoundTag save(@NotNull CompoundTag nbt) {
        super.save(nbt);
        nbt.putString(getName(), this.getValue().toString().toLowerCase(Locale.ROOT));
        return nbt;
    }

    @Override
    public void load(@NotNull CompoundTag nbt) {
        String raw = nbt.getStringOr(getName(), org.magconch.bot.BotConfig.InGame.tickType.name());
        this.setValue(switch (raw.toLowerCase(Locale.ROOT)) {
            case "network" -> ServerBot.TickType.NETWORK;
            case "entity_list" -> ServerBot.TickType.ENTITY_LIST;
            default -> throw new IllegalStateException("Unexpected bot tick type value: " + raw);
        });
    }
}
