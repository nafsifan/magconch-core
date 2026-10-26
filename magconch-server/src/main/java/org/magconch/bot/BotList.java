package org.magconch.bot;

import com.google.common.collect.Maps;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.logging.LogUtils;
import io.papermc.paper.adventure.PaperAdventure;
import io.papermc.paper.profile.MutablePropertyMap;
import io.papermc.paper.util.MCUtil;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.magconch.bot.BotConfig;
import org.magconch.event.bot.BotCreateEvent;
import org.magconch.event.bot.BotJoinEvent;
import org.magconch.event.bot.BotLoadEvent;
import org.magconch.event.bot.BotRemoveEvent;
import org.magconch.event.bot.BotSpawnLocationEvent;
import org.slf4j.Logger;
import net.minecraft.ChatFormatting;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.PlayerTeam;
import org.bukkit.OfflinePlayer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class BotList {

    public static BotList INSTANCE;

    public static BotList instance() {
        return INSTANCE;
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_SAVE_PER_TICK = 20;

    private static final String TEAM_NAME = "server_bot";
    private static final Component TEAM_PREFIX = Component.empty()
        .append(Component.literal("[").withStyle(ChatFormatting.GRAY))
        .append(Component.literal("BOT").withStyle(ChatFormatting.DARK_GRAY))
        .append(Component.literal("] ").withStyle(ChatFormatting.GRAY));

    private final MinecraftServer server;

    public final List<ServerBot> bots = new CopyOnWriteArrayList<>();
    private final BotDataStorage manualSaveDataStorage;
    private final BotDataStorage resumeDataStorage;

    private final Map<UUID, ServerBot> botsByUUID = Maps.newHashMap();
    private final Map<String, ServerBot> botsByLowerName = Maps.newHashMap();
    private final Map<String, Set<String>> botsNameByWorldUuid = Maps.newHashMap();
    private final Map<String, Set<String>> legacyBotsNameByWorldUuid = Maps.newHashMap();
    private int tickCounter = 0;

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

    public BotList(@NotNull MinecraftServer server) {
        this.server = server;
        this.manualSaveDataStorage = new BotDataStorage(server.storageSource, "fakeplayerdata", "fakeplayer.dat");
        this.resumeDataStorage = new BotDataStorage(server.storageSource, "resume_fakeplayerdata", "resume_fakeplayer.dat");
        INSTANCE = this;
    }

    public void loadBots() {
        this.loadResumeBotInfo();
        for (ServerLevel level : this.server.getAllLevels()) {
            this.loadResume(level.uuid.toString());
        }
        this.migrateLegacyBotsJsonIfPresent();
    }

    public void onServerShutdown() {
        this.removeAll();
    }

    public void removeAllIn(ServerLevel level) {
        this.removeAllIn(level.uuid.toString());
    }

    public boolean removeBot(ServerBot bot) {
        return this.removeBot(bot, org.magconch.event.bot.BotRemoveEvent.RemoveReason.INTERNAL, null, false, false);
    }

    public void saveAllResumeBots(final int interval) {
        MCUtil.ensureMain("Save Bots", () -> {
            final long now = MinecraftServer.currentTick;
            int numSaved = 0;
            for (ServerBot bot : bots) {
                if (interval == -1 || now - bot.lastSave >= interval) {
                    this.resumeDataStorage.save(bot, false);
                    bot.lastSave = now;
                    numSaved++;
                    if (interval != -1 && numSaved >= MAX_SAVE_PER_TICK) {
                        break;
                    }
                }
            }
            if (numSaved > 0) {
                this.resumeDataStorage.saveBotList();
            }
            return null;
        });
    }

    public void saveAllResumeBots() {
        if (!org.magconch.bot.BotConfig.enable || !org.magconch.bot.BotConfig.canResident) {
            return;
        }
        for (ServerBot bot : this.bots) {
            this.resumeDataStorage.save(bot);
        }
    }

    public ServerBot createNewBot(@NotNull BotCreateState state) {
        BotCreateEvent event = new BotCreateEvent(state.fullName(), state.skinName(), state.location(), state.createReason(), state.creator());
        event.setCancelled(!BotUtil.isCreateLegal(state.fullName()));
        this.server.server.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return null;
        }

        Location location = event.getCreateLocation();
        ServerLevel world = ((CraftWorld) location.getWorld()).getHandle();

        GameProfile profile = createBotProfile(BotUtil.getBotUUID(state), state.fullName(), state.skin());
        ServerBot bot = new ServerBot(this.server, world, profile);
        bot.createState = state;
        if (event.getCreator() instanceof org.bukkit.entity.Player player) {
            bot.createPlayer = player.getUniqueId();
        }

        return this.placeNewBot(bot, world, location, null);
    }

    public ServerBot loadNewManualSavedBot(String fullName) {
        return this.loadNewBot(fullName, this.manualSaveDataStorage);
    }

    public ServerBot loadNewResumeBot(String fullName) {
        return this.loadNewBot(fullName, this.resumeDataStorage);
    }

    public ServerBot loadNewBot(String inputName, BotDataStorage storage) {
        String lowerName = inputName.toLowerCase(Locale.ROOT);
        if (botsByLowerName.containsKey(lowerName)) {
            return null;
        }
        try {
            if (!storage.getSavedBotList().contains(lowerName)) {
                return null;
            }
            String name = storage.getNameFromLower(lowerName);
            UUID uuid = storage.getUUIDFromLower(lowerName);
            BotLoadEvent event = new BotLoadEvent(name, uuid);
            this.server.server.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return null;
            }

            ServerBot bot = new ServerBot(this.server, this.server.getLevel(Level.OVERWORLD), new GameProfile(uuid, name));
            bot.connection = new ServerBotPacketListenerImpl(this.server, bot);
            Optional<ValueInput> optional;
            try (ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(bot.problemPath(), LOGGER)) {
                optional = storage.load(bot, scopedCollector);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            if (optional.isEmpty()) {
                return null;
            }
            ValueInput nbt = optional.get();

            ResourceKey<Level> resourcekey = null;
            if (nbt.getLong("WorldUUIDMost").isPresent() && nbt.getLong("WorldUUIDLeast").isPresent()) {
                org.bukkit.World bWorld = Bukkit.getServer().getWorld(new UUID(nbt.getLong("WorldUUIDMost").orElseThrow(), nbt.getLong("WorldUUIDLeast").orElseThrow()));
                if (bWorld != null) {
                    resourcekey = ((CraftWorld) bWorld).getHandle().dimension();
                }
            }
            if (resourcekey == null) {
                return null;
            }

            ServerLevel world = this.server.getLevel(resourcekey);
            return this.placeNewBot(bot, world, bot.getLocation(), nbt);
        } catch (Exception e) {
            LOGGER.error("Failed to load bot {}", inputName, e);
            return null;
        }
    }

    public ServerBot placeNewBot(@NotNull ServerBot bot, ServerLevel world, Location location, ValueInput save) {
        Optional<ValueInput> optional = Optional.ofNullable(save);

        bot.isRealPlayer = true;
        bot.loginTime = System.currentTimeMillis();
        bot.connection = new ServerBotPacketListenerImpl(this.server, bot);
        bot.setServerLevel(world);

        BotSpawnLocationEvent event = new BotSpawnLocationEvent(bot.getBukkitEntity(), location);
        this.server.server.getPluginManager().callEvent(event);
        location = event.getSpawnLocation();

        bot.gameMode.setLevel(bot.level());

        bot.setPosRaw(location.getX(), location.getY(), location.getZ());
        bot.setRot(location.getYaw(), location.getPitch());

        bot.connection.teleport(bot.getX(), bot.getY(), bot.getZ(), bot.getYRot(), bot.getXRot());

        this.bots.add(bot);
        this.botsByLowerName.put(bot.getScoreboardName().toLowerCase(Locale.ROOT), bot);
        this.botsByUUID.put(bot.getUUID(), bot);

        PlayerTeam team = ensureBotTeam(this.server);
        this.server.getScoreboard().addPlayerToTeam(bot.getScoreboardName(), team);

        bot.suppressTrackerForLogin = true;
        world.addNewPlayer(bot);
        optional.ifPresent(nbt -> {
            bot.loadAndSpawnEnderPearls(nbt);
            bot.loadAndSpawnParentVehicle(nbt);
        });

        BotJoinEvent event1 = new BotJoinEvent(bot.getBukkitEntity(), PaperAdventure.asAdventure(Component.translatable("multiplayer.player.joined", bot.getDisplayName())).style(Style.style(NamedTextColor.YELLOW)));
        this.server.server.getPluginManager().callEvent(event1);

        net.kyori.adventure.text.Component joinMessage = event1.joinMessage();
        if (joinMessage != null && !joinMessage.equals(net.kyori.adventure.text.Component.empty())) {
            this.server.getPlayerList().broadcastSystemMessage(PaperAdventure.asVanilla(joinMessage), false);
        }

        bot.renderInfo();
        bot.suppressTrackerForLogin = false;

        bot.level().getChunkSource().chunkMap.addEntity(bot);
        bot.renderData();
        bot.initInventoryMenu();
        botsNameByWorldUuid
            .computeIfAbsent(bot.level().uuid.toString(), (k) -> new HashSet<>())
            .add(bot.getBukkitEntity().getName());
        BotList.LOGGER.info("{}[{}] logged in with entity id {} at ([{}]{}, {}, {})", bot.getName().getString(), "Local", bot.getId(), bot.level().serverLevelData.getLevelName(), bot.getX(), bot.getY(), bot.getZ());
        return bot;
    }

    public boolean removeBot(@NotNull ServerBot bot, @NotNull BotRemoveEvent.RemoveReason reason, @Nullable CommandSender remover, boolean save, boolean resume) {
        BotRemoveEvent event = new BotRemoveEvent(bot.getBukkitEntity(), reason, remover, PaperAdventure.asAdventure(Component.translatable("multiplayer.player.left", bot.getDisplayName())).style(Style.style(NamedTextColor.YELLOW)), save);
        this.server.server.getPluginManager().callEvent(event);

        if (event.isCancelled() && event.getReason() != BotRemoveEvent.RemoveReason.INTERNAL) {
            return false;
        }

        if (bot.removeTaskId != -1) {
            Bukkit.getScheduler().cancelTask(bot.removeTaskId);
            bot.removeTaskId = -1;
        }

        bot.disconnect();

        this.resumeDataStorage.removeSavedData(bot.nameAndId().name());
        if (event.shouldSave()) {
            if (resume) {
                this.resumeDataStorage.save(bot);
            } else {
                this.manualSaveDataStorage.save(bot);
            }
        } else {
            bot.dropExperience();
            bot.dropAll(true);
            botsNameByWorldUuid.getOrDefault(bot.level().uuid.toString(), new HashSet<>()).remove(bot.getBukkitEntity().getName());
        }

        if (bot.isPassenger() && event.shouldSave()) {
            Entity entity = bot.getRootVehicle();
            if (entity.hasExactlyOnePlayerPassenger()) {
                bot.stopRiding();
                entity.getPassengersAndSelf().forEach((entity1) -> {
                    entity1.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER);
                });
            }
        }

        bot.unRide();
        for (ThrownEnderpearl thrownEnderpearl : bot.getEnderPearls()) {
            if (!thrownEnderpearl.level().paperConfig().misc.legacyEnderPearlBehavior) {
                thrownEnderpearl.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER, EntityRemoveEvent.Cause.PLAYER_QUIT);
            } else {
                thrownEnderpearl.setOwner(null);
            }
        }

        bot.level().removePlayerImmediately(bot, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        this.bots.remove(bot);
        this.botsByLowerName.remove(bot.getScoreboardName().toLowerCase(Locale.ROOT));

        PlayerTeam team = this.server.getScoreboard().getPlayerTeam(TEAM_NAME);
        if (team != null && bot.getTeam() == team) {
            this.server.getScoreboard().removePlayerFromTeam(bot.getScoreboardName(), team);
        }

        UUID uuid = bot.getUUID();
        ServerBot bot1 = this.botsByUUID.get(uuid);
        if (bot1 == bot) {
            this.botsByUUID.remove(uuid);
        }

        bot.removeTab();
        ClientboundRemoveEntitiesPacket packet = new ClientboundRemoveEntitiesPacket(bot.getId());
        for (ServerPlayer player : bot.level().players()) {
            if (!(player instanceof ServerBot)) {
                player.connection.send(packet);
            }
        }

        net.kyori.adventure.text.Component removeMessage = event.removeMessage();
        if (removeMessage != null && !removeMessage.equals(net.kyori.adventure.text.Component.empty())) {
            this.server.getPlayerList().broadcastSystemMessage(PaperAdventure.asVanilla(removeMessage), false);
        }
        return true;
    }

    public void removeAllIn(String worldUuid) {
        for (String fullName : this.botsNameByWorldUuid.getOrDefault(worldUuid, new HashSet<>())) {
            ServerBot bot = this.getBotByName(fullName);
            if (bot != null) {
                bot.resume = org.magconch.bot.BotConfig.canResident;
                this.removeBot(bot, BotRemoveEvent.RemoveReason.INTERNAL, null, org.magconch.bot.BotConfig.canResident, org.magconch.bot.BotConfig.canResident);
            }
        }
    }

    public void removeAll() {
        for (ServerBot bot : this.bots) {
            bot.resume = org.magconch.bot.BotConfig.canResident;
            this.removeBot(bot, BotRemoveEvent.RemoveReason.INTERNAL, null, org.magconch.bot.BotConfig.canResident, org.magconch.bot.BotConfig.canResident);
        }
    }

    public void loadResumeBotInfo() {
        if (!org.magconch.bot.BotConfig.enable || !org.magconch.bot.BotConfig.canResident) {
            return;
        }
        CompoundTag savedBotList = this.getResumeBotList().copy();
        for (Map.Entry<String, Tag> entry : savedBotList.entrySet()) {
            String lowerName = entry.getKey();
            String fullName = ((CompoundTag) entry.getValue()).getStringOr("name", lowerName);
            UUID levelUuid = BotUtil.getBotLevel(fullName, this.resumeDataStorage);
            if (levelUuid == null) {
                LOGGER.warn("Bot {} has no world UUID, skipping loading.", fullName);
                continue;
            }
            this.botsNameByWorldUuid
                .computeIfAbsent(levelUuid.toString(), (k) -> new HashSet<>())
                .add(fullName);
        }
        loadLegacyResumeBotInfo();
    }

    private void loadLegacyResumeBotInfo() {
        CompoundTag savedBotList = this.getManualSavedBotList().copy();
        for (String fullName : savedBotList.keySet()) {
            // Legacy format saved fullName as the key
            CompoundTag nbt = savedBotList.getCompound(fullName).orElseThrow();
            if (!nbt.getBoolean("resume").orElse(false)) {
                continue;
            }
            UUID levelUuid = BotUtil.getBotLevel(fullName, this.manualSaveDataStorage);
            if (levelUuid == null) {
                LOGGER.warn("Bot {} has no world UUID, skipping loading.", fullName);
                continue;
            }
            this.legacyBotsNameByWorldUuid
                .computeIfAbsent(levelUuid.toString(), (k) -> new HashSet<>())
                .add(fullName);
        }
    }

    public void loadResume(String worldUuid) {
        if (!org.magconch.bot.BotConfig.enable || !org.magconch.bot.BotConfig.canResident) {
            return;
        }
        new HashSet<>(this.botsNameByWorldUuid.getOrDefault(worldUuid, new HashSet<>())).forEach(this::loadNewResumeBot);
        new HashSet<>(this.legacyBotsNameByWorldUuid.getOrDefault(worldUuid, new HashSet<>())).forEach(this::loadNewManualSavedBot);
    }

    public void updateBotLevel(@NotNull ServerBot bot, @NotNull ServerLevel level) {
        String prevUuid = bot.level().uuid.toString();
        String newUuid = level.uuid.toString();
        this.botsNameByWorldUuid
            .computeIfAbsent(newUuid, (k) -> new HashSet<>())
            .add(bot.getBukkitEntity().getName());
        this.botsNameByWorldUuid
            .computeIfAbsent(prevUuid, (k) -> new HashSet<>())
            .remove(bot.getBukkitEntity().getName());
    }

    public void networkTick() {
        this.bots.forEach(ServerBot::networkTick);
        if (++this.tickCounter % 6000 == 0) {
            this.checkOfflineTimeouts();
        }
    }

    private void checkOfflineTimeouts() {
        int timeoutHours = BotConfig.getTimeoutHours();
        if (timeoutHours <= 0) {
            return;
        }

        for (ServerBot bot : this.bots) {
            if (this.isCreatorTimedOut(bot.createPlayer, timeoutHours)) {
                LOGGER.info("Bot {} was removed due to creator offline timeout ({}h)", bot.getScoreboardName(), timeoutHours);
                this.removeBot(bot, BotRemoveEvent.RemoveReason.INTERNAL, null, true, true);
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

    public int countByCreator(final @Nullable UUID creatorUUID) {
        if (creatorUUID == null) return 0;
        int count = 0;
        for (ServerBot bot : this.bots) {
            if (creatorUUID.equals(bot.createPlayer)) {
                count++;
            }
        }
        return count;
    }

    @Nullable
    public ServerBot getBot(@NotNull UUID uuid) {
        return this.botsByUUID.get(uuid);
    }

    @Nullable
    public ServerBot getBotByName(@NotNull String name) {
        return this.botsByLowerName.get(name.toLowerCase(Locale.ROOT));
    }

    public CompoundTag getManualSavedBotList() {
        return this.getSavedBotList(this.manualSaveDataStorage);
    }

    public CompoundTag getResumeBotList() {
        return this.getSavedBotList(this.resumeDataStorage);
    }

    public CompoundTag getSavedBotList(@NotNull BotDataStorage storage) {
        return storage.getSavedBotList();
    }

    private void migrateLegacyBotsJsonIfPresent() {
        Path legacyPath = Path.of("bots.json");
        if (!Files.isRegularFile(legacyPath)) {
            return;
        }
        LOGGER.info("Detected legacy bots.json, starting migration...");
        try (Reader reader = Files.newBufferedReader(legacyPath, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed.isJsonArray()) {
                int count = 0;
                for (JsonElement elem : parsed.getAsJsonArray()) {
                    if (!elem.isJsonObject()) continue;
                    JsonObject obj = elem.getAsJsonObject();
                    String name = obj.get("name").getAsString();
                    String worldId = obj.has("world") ? obj.get("world").getAsString() : "minecraft:overworld";
                    double x = obj.has("x") ? obj.get("x").getAsDouble() : 0.0;
                    double y = obj.has("y") ? obj.get("y").getAsDouble() : 64.0;
                    double z = obj.has("z") ? obj.get("z").getAsDouble() : 0.0;
                    float yaw = obj.has("yaw") ? obj.get("yaw").getAsFloat() : 0.0f;
                    float pitch = obj.has("pitch") ? obj.get("pitch").getAsFloat() : 0.0f;

                    org.bukkit.World bWorld = null;
                    try {
                        ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.Identifier.parse(worldId));
                        ServerLevel sLevel = MinecraftServer.getServer().getLevel(key);
                        if (sLevel != null) {
                            bWorld = sLevel.getWorld();
                        }
                    } catch (Exception ignored) {
                    }
                    if (bWorld == null) {
                        bWorld = Bukkit.getWorld(worldId.replace("minecraft:", ""));
                    }
                    if (bWorld == null) {
                        bWorld = Bukkit.getWorlds().getFirst();
                    }
                    Location loc = new Location(bWorld, x, y, z, yaw, pitch);

                    String[] skin = null;
                    if (obj.has("skin") && obj.get("skin").isJsonArray()) {
                        JsonArray sArr = obj.getAsJsonArray("skin");
                        if (sArr.size() >= 2) {
                            skin = new String[]{sArr.get(0).getAsString(), sArr.get(1).getAsString()};
                        }
                    }

                    BotCreateState state = BotCreateState.builder(name, loc)
                        .skin(skin)
                        .build();

                    ServerBot bot = this.createNewBot(state);
                    if (bot != null) {
                        if (obj.has("creator")) {
                            bot.createPlayer = UUID.fromString(obj.get("creator").getAsString());
                        }
                        if (obj.has("health")) {
                            bot.setHealth(Math.min(obj.get("health").getAsFloat(), bot.getMaxHealth()));
                        }
                        this.resumeDataStorage.save(bot);
                        count++;
                    }
                }
                LOGGER.info("Successfully migrated {} legacy bot(s) to new storage.", count);
            }
            Path backupPath = Path.of("bots.json.migrated");
            Files.move(legacyPath, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            LOGGER.error("Failed to migrate legacy bots.json", e);
        }
    }

    @Contract("_, _, _ -> new")
    public static @NotNull GameProfile createBotProfile(UUID uuid, String name, String[] skin) {
        GameProfile profile = new GameProfile(uuid, name, new MutablePropertyMap());
        profile.properties().put("is_bot", new Property("is_bot", "true"));
        if (skin != null) {
            profile.properties().put("textures", new Property("textures", skin[0], skin[1]));
        }
        return profile;
    }
}
