package org.magconch.bot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.logging.LogUtils;
import io.papermc.paper.configuration.GlobalConfiguration;
import io.papermc.paper.profile.MutablePropertyMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 假人全局管理器 (BotList)
 * 负责假人实体的创建、移除、队伍挂载、配额统计、持久化存储以及创建者离线超时自动注销。
 */
public class BotList {

    private static final Logger LOGGER = LoggerFactory.getLogger("BOT");
    private static final BotList INSTANCE = new BotList();
    private static final String TEAM_NAME = "server_bot";
    private static final Component TEAM_PREFIX = Component.empty()
        .append(Component.literal("[").withStyle(ChatFormatting.GRAY))
        .append(Component.literal("BOT").withStyle(ChatFormatting.DARK_GRAY))
        .append(Component.literal("] ").withStyle(ChatFormatting.GRAY));
    private static final Path SAVE_PATH = Path.of("bots.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public final List<ServerBot> bots = new CopyOnWriteArrayList<>();
    private final Map<String, ServerBot> botsByName = new ConcurrentHashMap<>();
    private final Map<UUID, ServerBot> botsByUUID = new ConcurrentHashMap<>();
    private final Map<String, String[]> skinCache = new ConcurrentHashMap<>();
    private int tickCounter = 0;
    private boolean isRestoring = false;

    public static BotList instance() {
        return INSTANCE;
    }

    // ==================== 假人创建与入服管线 ====================

    public void createBotAsync(
        final ServerLevel level,
        final double x,
        final double y,
        final double z,
        final float yaw,
        final float pitch,
        final String name,
        final @Nullable UUID creatorUUID,
        final boolean bypassLimit,
        final Consumer<@Nullable ServerBot> callback
    ) {
        CompletableFuture.supplyAsync(() -> this.getOrFetchSkin(name), Util.nonCriticalIoPool())
            .thenAcceptAsync(skin -> {
                GlobalConfiguration.MagconchConfig.Bot config = GlobalConfiguration.get().magconch.bot;
                if (!config.enable
                    || (!bypassLimit && config.playerLimit > 0 && creatorUUID != null && this.countByCreator(creatorUUID) >= config.playerLimit)
                    || !this.isNameAvailable(name)) {
                    callback.accept(null);
                    return;
                }
                ServerBot bot = this.placeNewBot(level, x, y, z, yaw, pitch, name, skin, creatorUUID);
                callback.accept(bot);
            }, MinecraftServer.getServer());
    }

    private ServerBot placeNewBot(
        final ServerLevel level,
        final double x,
        final double y,
        final double z,
        final float yaw,
        final float pitch,
        final String name,
        final String @Nullable [] skin,
        final @Nullable UUID creatorUUID
    ) {
        MinecraftServer server = MinecraftServer.getServer();
        UUID uuid = createBotUUID(name);
        GameProfile profile = new GameProfile(uuid, name, new MutablePropertyMap());
        if (skin != null && skin.length == 2) {
            profile.properties().put("textures", new Property("textures", skin[0], skin[1]));
        }

        ServerBot bot = new ServerBot(server, level, profile, creatorUUID);
        bot.isRealPlayer = true;
        bot.loginTime = System.currentTimeMillis();
        bot.connection = new ServerBot.BotPacketListener(server, bot);
        bot.snapTo(x, y, z, yaw, pitch);
        bot.setYHeadRot(yaw);

        this.bots.add(bot);
        this.botsByName.put(name.toLowerCase(Locale.ROOT), bot);
        this.botsByUUID.put(uuid, bot);

        // 挂载 server_bot 真实计分板队伍，统一渲染头顶、Tab 与聊天框前缀
        PlayerTeam team = ensureBotTeam(server);
        server.getScoreboard().addPlayerToTeam(bot.getScoreboardName(), team);

        // 先抑制实体追踪，待发送 Tab 列表初始化包后再挂载区块追踪器
        bot.suppressTrackerForLogin = true;
        level.addNewPlayer(bot);
        bot.sendPlayerInfoToAll();
        bot.suppressTrackerForLogin = false;
        level.getChunkSource().addEntity(bot);
        bot.initInventoryMenu();

        if (!this.isRestoring) {
            server.getPlayerList().broadcastSystemMessage(
                Component.translatable("multiplayer.player.joined", bot.getDisplayName()).withStyle(ChatFormatting.YELLOW),
                false
            );
            this.saveBots();
        }
        return bot;
    }

    private static PlayerTeam ensureBotTeam(final MinecraftServer server) {
        ServerScoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(TEAM_NAME);
        if (team == null) {
            team = scoreboard.addPlayerTeam(TEAM_NAME);
        }
        if (!TEAM_PREFIX.equals(team.getPlayerPrefix())) {
            team.setPlayerPrefix(TEAM_PREFIX);
        }
        return team;
    }

    // ==================== 假人移除与清理管线 ====================

    public boolean removeBot(final ServerBot bot) {
        if (!this.bots.remove(bot)) {
            return false;
        }
        this.botsByName.remove(bot.getScoreboardName().toLowerCase(Locale.ROOT));
        this.botsByUUID.remove(bot.getUUID());

        Component leaveMessage = Component.translatable("multiplayer.player.left", bot.getDisplayName()).withStyle(ChatFormatting.YELLOW);

        bot.disconnect();
        bot.unRide();
        for (ThrownEnderpearl pearl : bot.getEnderPearls()) {
            if (!pearl.level().paperConfig().misc.legacyEnderPearlBehavior) {
                pearl.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER, EntityRemoveEvent.Cause.PLAYER_QUIT);
            } else {
                pearl.setOwner(null);
            }
        }

        bot.level().removePlayerImmediately(bot, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        bot.removeTabFromAll();

        MinecraftServer server = MinecraftServer.getServer();
        ServerScoreboard scoreboard = server.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(TEAM_NAME);
        if (team != null && bot.getTeam() == team) {
            scoreboard.removePlayerFromTeam(bot.getScoreboardName(), team);
        }

        server.getPlayerList().broadcastSystemMessage(leaveMessage, false);
        if (MinecraftServer.getServer().isRunning()) {
            this.saveBots();
        }
        return true;
    }

    public void onServerShutdown() {
        this.saveBots();
        for (ServerBot bot : this.bots) {
            bot.disconnect();
            bot.unRide();
            for (ThrownEnderpearl pearl : bot.getEnderPearls()) {
                if (!pearl.level().paperConfig().misc.legacyEnderPearlBehavior) {
                    pearl.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER, EntityRemoveEvent.Cause.PLAYER_QUIT);
                } else {
                    pearl.setOwner(null);
                }
            }
            bot.level().removePlayerImmediately(bot, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        }
        this.bots.clear();
        this.botsByName.clear();
        this.botsByUUID.clear();
    }

    public void removeAllIn(final ServerLevel level) {
        if (!MinecraftServer.getServer().isRunning()) {
            return;
        }
        for (ServerBot bot : this.bots) {
            if (bot.level() == level) {
                this.removeBot(bot);
            }
        }
    }

    public void removeAll() {
        if (!MinecraftServer.getServer().isRunning()) {
            return;
        }
        for (ServerBot bot : this.bots) {
            this.removeBot(bot);
        }
    }

    public void networkTick() {
        for (ServerBot bot : this.bots) {
            bot.doTick();
        }

        // 每 5 分钟 (6000 ticks) 低频检测一次创建者离线超时，无磁盘与网络 IO
        if (++this.tickCounter % 6000 == 0) {
            this.checkOfflineTimeouts();
        }
    }

    // ==================== 持久化存储与恢复 ====================

    public void saveBots() {
        GlobalConfiguration.MagconchConfig.Bot config = GlobalConfiguration.get().magconch.bot;
        if (!config.resident) {
            try {
                Files.deleteIfExists(SAVE_PATH);
            } catch (Exception ignored) {
            }
            return;
        }

        JsonArray array = new JsonArray();
        for (ServerBot bot : this.bots) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", bot.getScoreboardName());
            if (bot.creatorUUID != null) {
                obj.addProperty("creator", bot.creatorUUID.toString());
            }
            obj.addProperty("world", bot.level().dimension().identifier().toString());
            obj.addProperty("x", bot.getX());
            obj.addProperty("y", bot.getY());
            obj.addProperty("z", bot.getZ());
            obj.addProperty("yaw", bot.getYRot());
            obj.addProperty("pitch", bot.getXRot());
            obj.addProperty("health", bot.getHealth());

            for (Property prop : bot.getGameProfile().properties().get("textures")) {
                JsonArray skinArr = new JsonArray();
                skinArr.add(prop.value());
                skinArr.add(prop.signature());
                obj.add("skin", skinArr);
                break;
            }
            array.add(obj);
        }

        try (Writer writer = Files.newBufferedWriter(SAVE_PATH, StandardCharsets.UTF_8)) {
            GSON.toJson(array, writer);
            if (!this.bots.isEmpty()) {
                LOGGER.info("Saved {} persistent bot(s) to bots.json", this.bots.size());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save bots.json", e);
        }
    }

    public void loadBots() {
        GlobalConfiguration.MagconchConfig.Bot config = GlobalConfiguration.get().magconch.bot;
        if (!config.enable || !config.resident || !Files.isRegularFile(SAVE_PATH)) {
            return;
        }

        MinecraftServer server = MinecraftServer.getServer();
        this.isRestoring = true;
        try (Reader reader = Files.newBufferedReader(SAVE_PATH, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonArray()) {
                return;
            }

            int restored = 0;
            for (JsonElement elem : parsed.getAsJsonArray()) {
                if (!elem.isJsonObject()) continue;
                JsonObject obj = elem.getAsJsonObject();

                String name = obj.get("name").getAsString();
                UUID creator = obj.has("creator") ? UUID.fromString(obj.get("creator").getAsString()) : null;

                // 检查创建者离线超时注销
                if (this.isCreatorTimedOut(creator, config.timeoutHours)) {
                    continue;
                }

                if (!this.isNameAvailable(name)) {
                    continue;
                }

                String worldId = obj.get("world").getAsString();
                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(worldId));
                ServerLevel level = server.getLevel(key);
                if (level == null) {
                    level = server.overworld();
                }

                double x = obj.get("x").getAsDouble();
                double y = obj.get("y").getAsDouble();
                double z = obj.get("z").getAsDouble();
                float yaw = obj.get("yaw").getAsFloat();
                float pitch = obj.get("pitch").getAsFloat();

                String[] skin = null;
                if (obj.has("skin") && obj.get("skin").isJsonArray()) {
                    JsonArray sArr = obj.getAsJsonArray("skin");
                    if (sArr.size() >= 2) {
                        skin = new String[]{sArr.get(0).getAsString(), sArr.get(1).getAsString()};
                    }
                }

                ServerBot bot = this.placeNewBot(level, x, y, z, yaw, pitch, name, skin, creator);
                if (obj.has("health")) {
                    bot.setHealth(Math.min(obj.get("health").getAsFloat(), bot.getMaxHealth()));
                }
                restored++;
            }
            if (restored > 0) {
                LOGGER.info("Successfully restored {} persistent bot(s)", restored);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load bots.json", e);
        } finally {
            this.isRestoring = false;
        }
    }

    private void checkOfflineTimeouts() {
        GlobalConfiguration.MagconchConfig.Bot config = GlobalConfiguration.get().magconch.bot;
        if (config.timeoutHours <= 0) {
            return;
        }

        for (ServerBot bot : this.bots) {
            if (this.isCreatorTimedOut(bot.creatorUUID, config.timeoutHours)) {
                LOGGER.info("Bot {} was removed due to creator offline timeout ({}h)", bot.getScoreboardName(), config.timeoutHours);
                this.removeBot(bot);
            }
        }
    }

    private boolean isCreatorTimedOut(final @Nullable UUID creatorUUID, final int timeoutHours) {
        if (creatorUUID == null || timeoutHours <= 0) {
            return false;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(creatorUUID);
        if (player.isOnline()) {
            return false;
        }
        long lastSeen = player.getLastSeen();
        if (lastSeen <= 0) {
            return false;
        }
        long elapsedHours = (System.currentTimeMillis() - lastSeen) / 3600_000L;
        return elapsedHours >= timeoutHours;
    }

    // ==================== 查询、配额与重名防护 ====================

    public @Nullable ServerBot getBotByName(final String name) {
        return this.botsByName.get(name.toLowerCase(Locale.ROOT));
    }

    public @Nullable ServerBot getBot(final UUID uuid) {
        return this.botsByUUID.get(uuid);
    }

    public int countByCreator(final UUID creatorUUID) {
        int count = 0;
        for (ServerBot bot : this.bots) {
            if (creatorUUID.equals(bot.creatorUUID)) {
                count++;
            }
        }
        return count;
    }

    public boolean isNameAvailable(final String name) {
        if (this.getBotByName(name) != null) {
            return false;
        }
        if (Bukkit.getPlayerExact(name) != null) {
            return false;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        return cached == null || cached.getUniqueId().equals(createBotUUID(name));
    }

    public static UUID createBotUUID(final String name) {
        return UUID.nameUUIDFromBytes(("MagconchBot:" + name.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
    }

    // ==================== Mojang 正版皮肤获取与缓存 ====================

    private String @Nullable [] getOrFetchSkin(final String name) {
        String key = name.toLowerCase(Locale.ROOT);
        String[] cached = this.skinCache.get(key);
        if (cached != null) {
            return cached.length == 2 ? cached : null;
        }
        String[] fetched = pullSkinFromMojang(name);
        if (this.skinCache.size() >= 256) {
            this.skinCache.clear();
        }
        this.skinCache.put(key, fetched != null ? fetched : new String[0]);
        return fetched;
    }

    private static String @Nullable [] pullSkinFromMojang(final String name) {
        try {
            HttpURLConnection uuidConn = (HttpURLConnection) URI.create("https://api.mojang.com/users/profiles/minecraft/" + name).toURL().openConnection();
            uuidConn.setConnectTimeout(4000);
            uuidConn.setReadTimeout(4000);
            if (uuidConn.getResponseCode() != 200) {
                return null;
            }
            String id;
            try (InputStreamReader reader = new InputStreamReader(uuidConn.getInputStream(), StandardCharsets.UTF_8)) {
                id = JsonParser.parseReader(reader).getAsJsonObject().get("id").getAsString();
            }

            HttpURLConnection profileConn = (HttpURLConnection) URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false").toURL().openConnection();
            profileConn.setConnectTimeout(4000);
            profileConn.setReadTimeout(4000);
            if (profileConn.getResponseCode() != 200) {
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(profileConn.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject prop = JsonParser.parseReader(reader)
                    .getAsJsonObject()
                    .getAsJsonArray("properties")
                    .get(0)
                    .getAsJsonObject();
                return new String[]{prop.get("value").getAsString(), prop.get("signature").getAsString()};
            }
        } catch (Exception ignored) {
            return null;
        }
    }
}
