package org.magconch.bot;

import io.papermc.paper.configuration.GlobalConfiguration;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class BotConfig {

    public static boolean enable = true;
    public static boolean canResident = true;
    public static int limit = 20;
    public static String prefix = "";
    public static String suffix = "";
    public static List<String> unableNames = List.of("player-name");
    public static double regenAmount = 0.0;
    public static boolean canOpenInventory = true;
    public static boolean canUseAction = true;
    public static boolean canModifyConfig = true;
    public static boolean canManualSaveAndLoad = true;
    public static boolean useSkinCache = true;

    public static boolean isEnabled() {
        try {
            return GlobalConfiguration.get().magconch.bot.enable;
        } catch (Throwable t) {
            return enable;
        }
    }

    public static boolean isResident() {
        try {
            return GlobalConfiguration.get().magconch.bot.resident;
        } catch (Throwable t) {
            return canResident;
        }
    }

    public static int getTimeoutHours() {
        try {
            return GlobalConfiguration.get().magconch.bot.timeoutHours;
        } catch (Throwable t) {
            return 24;
        }
    }

    public static class InGame {
        public static boolean canSendDataAlways = true;
        public static boolean canSkipSleep = true;
        public static boolean canSpawnPhantom = false;
        public static ServerBot.TickType tickType = ServerBot.TickType.NETWORK;
        public static int simulationDistance = -1;
        public static boolean enableLocatorBar = true;

        public static int getSimulationDistance(ServerPlayer player) {
            if (simulationDistance <= 0) {
                return player.level().getChunkSource().chunkMap.serverViewDistance;
            }
            return simulationDistance;
        }
    }
}
