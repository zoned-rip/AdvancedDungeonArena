    package su.nightexpress.dungeons.dungeon.game;
    
    import org.bukkit.*;
    import org.bukkit.attribute.Attribute;
    import org.bukkit.block.Block;
    import org.bukkit.entity.*;
    import org.bukkit.event.Cancellable;
    import org.bukkit.event.entity.EntityDeathEvent;
    import org.bukkit.inventory.ItemStack;
    import org.jetbrains.annotations.NotNull;
    import org.jetbrains.annotations.Nullable;
    import su.nightexpress.dungeons.DungeonPlugin;
    import su.nightexpress.dungeons.Placeholders;
    import su.nightexpress.dungeons.api.criteria.CriterionMob;
    import su.nightexpress.dungeons.api.dungeon.*;
    import su.nightexpress.dungeons.api.mob.MobIdentifier;
    import su.nightexpress.dungeons.api.mob.MobProvider;
    import su.nightexpress.dungeons.api.type.GameResult;
    import su.nightexpress.dungeons.api.type.GameState;
    import su.nightexpress.dungeons.api.type.MobFaction;
    import su.nightexpress.dungeons.config.Config;
    import su.nightexpress.dungeons.config.Lang;
    import su.nightexpress.dungeons.config.Perms;
    import su.nightexpress.dungeons.dungeon.DungeonManager;
    import su.nightexpress.dungeons.dungeon.Party.Party;
    import su.nightexpress.dungeons.dungeon.config.DungeonConfig;
    import su.nightexpress.dungeons.dungeon.criteria.registry.mob.MobCriterias;
    import su.nightexpress.dungeons.dungeon.event.DungeonEventReceiver;
    import su.nightexpress.dungeons.dungeon.event.game.*;
    import su.nightexpress.dungeons.dungeon.event.normal.DungeonStartedEvent;
    import su.nightexpress.dungeons.dungeon.feature.KillStreak;
    import su.nightexpress.dungeons.dungeon.feature.LevelRequirement;
    import su.nightexpress.dungeons.dungeon.feature.itemfilter.ItemFilterCriteria;
    import su.nightexpress.dungeons.dungeon.feature.itemfilter.ItemFilterMode;
    import su.nightexpress.dungeons.dungeon.level.Level;
    import su.nightexpress.dungeons.dungeon.lootchest.LootChest;
    import su.nightexpress.dungeons.dungeon.mob.DungeonMob;
    import su.nightexpress.dungeons.dungeon.player.DungeonGamer;
    import su.nightexpress.dungeons.dungeon.player.PlayerSnapshot;
    import su.nightexpress.dungeons.dungeon.player.QueueEntry;
    import su.nightexpress.dungeons.dungeon.reward.FinishChestRewardManager;
    import su.nightexpress.dungeons.dungeon.reward.GameReward;
    import su.nightexpress.dungeons.dungeon.reward.Reward;
    import su.nightexpress.dungeons.dungeon.spot.Spot;
    import su.nightexpress.dungeons.dungeon.spot.SpotState;
    import su.nightexpress.dungeons.dungeon.stage.Stage;
    import su.nightexpress.dungeons.dungeon.stage.StageTask;
    import su.nightexpress.dungeons.dungeon.stage.task.TaskProgress;
    import su.nightexpress.dungeons.dungeon.stats.DungeonStats;
    import su.nightexpress.dungeons.kit.KitUtils;
    import su.nightexpress.dungeons.kit.impl.Kit;
    import su.nightexpress.dungeons.registry.mob.MobRegistry;
    import su.nightexpress.dungeons.user.DungeonUser;
    import su.nightexpress.dungeons.util.DungeonUtils;
    import su.nightexpress.dungeons.util.ErrorHandler;
    import su.nightexpress.dungeons.util.MobUitls;
    import su.nightexpress.nightcore.integration.currency.EconomyBridge;
    import su.nightexpress.nightcore.locale.entry.MessageLocale;
    import su.nightexpress.nightcore.locale.message.LangMessage;
    import su.nightexpress.nightcore.util.EntityUtil;
    import su.nightexpress.nightcore.util.ItemUtil;
    import su.nightexpress.nightcore.util.Players;
    import su.nightexpress.nightcore.util.geodata.pos.BlockPos;
    import su.nightexpress.nightcore.util.placeholder.Replacer;
    import su.nightexpress.nightcore.util.random.Rnd;
    import su.nightexpress.nightcore.util.time.TimeFormatType;
    import su.nightexpress.nightcore.util.time.TimeFormats;
    import su.nightexpress.nightcore.util.wrapper.UniParticle;
    
    import java.util.*;
    import java.util.concurrent.TimeUnit;
    import java.util.function.BiConsumer;
    import java.util.function.Consumer;
    import java.util.function.Predicate;
    import java.util.function.UnaryOperator;
    import java.util.stream.Collectors;

    import static su.nightexpress.dungeons.dungeon.reward.ChestLockManager.unlockPlayerChestInteraction;

    public class DungeonInstance implements Dungeon {
    
        private final DungeonPlugin    plugin;
        private final DungeonConfig    config;
        private final DungeonStats     stats;
        private final DungeonVariables variables;
        private final DungeonManager manager;
    
        private final List<DungeonEventReceiver>   eventReceivers;
        private final Map<StageTask, TaskProgress> taskProgress;
        private final Map<UUID, DungeonGamer>      players;
        private final Map<UUID, DungeonMob>        mobByIdMap;
        private final Set<Item>                    groundItems;
        private final List<DungeonGamer> gamersSnapshot = new ArrayList<>(); // Snpshot of the current players for data viewing
        public Map<UUID, Location> chestBoughtByPlayers = new HashMap<>();




        private final String prefix;
    
        private World world;
        private long  tickCount;
    
        private GameState  state;
        private GameResult gameResult;
        private int  countdown;
        private long timeLeft;
    
        private Level level;
        private Stage stage;
        private boolean stageCompleted;

        private final Queue<QueueEntry> joinQueue = new LinkedList<>();
    
        public DungeonInstance(@NotNull DungeonPlugin plugin, @NotNull DungeonConfig config, @NotNull DungeonManager manager) {
            this.plugin = plugin;
            this.config = config;
            this.manager = manager;
            this.stats = new DungeonStats(this);
            this.variables = new DungeonVariables();
            this.eventReceivers = new ArrayList<>(); // List to keep receivers order.
            this.taskProgress = new LinkedHashMap<>(); // Linked to keep tasks order.
            this.players = new HashMap<>();
            this.mobByIdMap = new HashMap<>();
            this.groundItems = new HashSet<>();
    
            this.reset();
    
            this.prefix = this.replacePlaceholders().apply(config.getPrefix());
        }
    
        @NotNull
        public UnaryOperator<String> replacePlaceholders() {
            return str -> Placeholders.DUNGEON_INSTANCE.replacer(this).apply(this.replaceVariables().apply(str));
        }
    
        @NotNull
        public UnaryOperator<String> replaceVariables() {
            return this.variables.replacePlaceholders();
        }
    
        private void reset() {
            this.killGroundItems();
            this.killMobs();
            this.removeTasks();
            this.resetSpotStates();
            this.clearLootChests();
            this.joinQueue.clear();
    
            this.stats.clear();
            this.variables.clear();
    
            this.taskProgress.clear();
            this.players.clear();
            this.mobByIdMap.clear();
            this.tickCount = 0L;
            this.countdown = this.config.gameSettings().getLobbyTime();
            this.setTimeLeft(0L);
    
            this.level = null;
            this.stage = null;
            this.stageCompleted = false;
            this.state = GameState.WAITING;
            this.eventReceivers.clear();
            this.gameResult = null;
            this.chestBoughtByPlayers.clear();
        }

        public void stop() {
            FinishChestRewardManager.cleanupRewardChests(this);
            this.gamersSnapshot.clear();
            this.gamersSnapshot.addAll(this.getPlayers());

            this.getPlayers().forEach(this::leavePlayer);

            // Disband parties of all players in this dungeon
            for (DungeonGamer gamer : this.gamersSnapshot) {
                UUID playerId = gamer.getPlayer().getUniqueId();
                if (plugin.getPartyManager().hasParty(playerId)) {
                    Party party = plugin.getPartyManager().getPartyOf(playerId);
                    if (party != null && party.isLeader(playerId)) {
                        plugin.getPartyManager().disbandParty(playerId);
                    }
                }
            }

            // Save queue before reset clears it
            Queue<QueueEntry> pendingQueue = new LinkedList<>(this.joinQueue);
            this.reset();

            // Restore and process
            this.joinQueue.addAll(pendingQueue);
            this.moveToNextInQueue();
        }
    
        public boolean activate() {
            World world = Bukkit.getWorld(this.config.getWorldName());
            if (world == null) return false;
    
            this.activate(world);
            return true;
        }
    
        public void activate(@NotNull World world) {
            if (this.config.getWorldName().equalsIgnoreCase(world.getName())) {
                this.world = world;
                this.plugin.debug("Dungeon " + this.getId() + " activated.");
            }
        }
    
        public void deactivate(@NotNull World world) {
            if (this.config.getWorldName().equalsIgnoreCase(world.getName())) {
                this.deactivate();
            }
        }
    
        public void deactivate() {
            this.gameResult = GameResult.NONE;
            this.stop();
            this.world = null;
            this.plugin.debug("Dungeon " + this.getId() + " deactivated.");
        }
    
        @Override
        @NotNull
        public World getWorld() {
            if (this.world == null) throw new IllegalStateException("Dungeon world is not loaded! You must check Dungeon#isActive before calling this method.");
    
            return this.world;
        }
    
        public boolean isActive() {
            return this.world != null && !this.config.isBroken();
        }
    
        private void updateListeners() {
            this.eventReceivers.clear();
            if (this.level != null) {
                this.eventReceivers.add(this.level);
            }
            if (this.stage != null) {
                this.eventReceivers.add(this.stage);
            }
        }
    
        public void tick() {
            if (!this.isActive()) return;
    
            if (this.state == GameState.INGAME) {
                this.tickGame();
    
                if (this.state != GameState.INGAME) return;
    
                DungeonGameEvent tickEvent = new DungeonTickEvent(this);
                this.broadcastEvent(tickEvent);
    
                this.tickCount++;
            }
            else {
                this.tickLobby();
            }
    
            this.getPlayers().forEach(DungeonGamer::tick);
            this.showStatus();
        }

        public void tickLobby() {
            if (!joinQueue.isEmpty() && !hasSoloPlayer() && !hasPartyPlayer()) {
                int maxPlayers = this.config.gameSettings().getMaxPlayers();

                // In case of some weird bug just reset everything
                try {
                    while (!joinQueue.isEmpty()) {
                        QueueEntry entry = joinQueue.peek();

                        // remove offline players from entry
                        entry.players().removeIf(p -> !p.isOnline());
                        if (entry.players().isEmpty()) { joinQueue.poll(); continue; }

                        // check if fits
                        if (maxPlayers > 0 && this.countPlayers() + entry.players().size() > maxPlayers) break;


                        // solo check
                        boolean entrySolo = entry.isSolo() &&
                                plugin.getSoloManager().isSolo(entry.   firstPlayer().getUniqueId());

                        if (entrySolo && this.countPlayers() > 0) break;

                        joinQueue.poll();
                        for (Player p : entry.players()) {
                            if (this.manager.isPlaying(p)) continue;
                            this.manager.enterInstance(p, this, entry.kit(), false);
                        }

                        // Group random queue players from similar dungeons into this instance
                        if (!entrySolo && entry.players().size() == 1) {
                            this.manager.groupRandomQueuePlayers(this, this.config.getId());
                        }


                        if (entrySolo) break;
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("[DungeonInstance] Error processing join queue: " + e.getMessage());
                    joinQueue.clear(); // clear the queue to prevent repeated failures
                }
            }

            boolean readyToStart = this.isReadyToStart();

            if (this.state == GameState.WAITING) {
                if (readyToStart) {
                    this.state = GameState.READY;

                    // Only show the message if the raid
                    if (this.config.gameSettings().isStartAnnouncement() && !hasSoloPlayer() && !hasPartyPlayer()) {
                        Players.getOnline().forEach(player -> {
                            if (this.plugin.getDungeonManager().isPlaying(player)) return;
                            if (!this.hasPermission(player)) return;
                        });
                    }
                }

                return;
            }

            if (!readyToStart) {
                this.state = GameState.WAITING;
                this.setCountdown(this.config.gameSettings().getLobbyTime());
                return;
            }

            Set<DungeonGamer> players = this.getPlayers();
            boolean allReady = players.stream().allMatch(DungeonPlayer::isReady);

            int dropTo = Config.DUNGEON_LOBBY_DROP_TIMER.get();
            if (dropTo > 0 && this.countdown > dropTo && allReady) {
                this.setCountdown(dropTo);
            }

            if (this.countdown <= 0) {
                this.holdChunks();

                this.state = GameState.INGAME;
                this.setLevel(this.config.getStartLevel());
                this.setStage(this.config.getStartStage());
                players.forEach(this::spawnPlayer);
                this.countdown = -1;
                this.setTimeLeft(this.config.gameSettings().hasTimeleft() ? this.config.gameSettings().getTimeleft() * 60L : -1L);

                DungeonStartedEvent event = new DungeonStartedEvent(this);
                this.plugin.getPluginManager().callEvent(event);
                return;
            }

            this.countdown--;
        }
    
        public void tickGame() {
            if (!this.config.gameSettings().isItemPickupAllowed()) {
                this.burnGroundItems();
            }
    
            this.eliminateDeadMobs();
    
            if (this.isAboutToEnd()) {
                if (this.countdown-- <= 0) {
                    this.stop();
                }
                return;
            }
    
            if (!this.hasAlivePlayers()) {
                long lastDeathTime = this.getDeadPlayers().stream().mapToLong(DungeonGamer::getDeathTime).max().orElse(0L);
                if (System.currentTimeMillis() - lastDeathTime > Config.DUNGEON_TIME_TO_REVIVE.get() * 1000L) {
                    this.setCountdown(Config.DUNGEON_COUNTDOWN_DEFEAT.get(), GameResult.DEFEAT);
                    this.broadcast(Lang.DUNGEON_END_ALL_DEAD, replacer -> replacer.replace(this.replacePlaceholders()));
                }
                return;
            }
    
            if (this.timeLeft > 0) {
                if (--this.timeLeft == 0) {
                    this.setCountdown(Config.DUNGEON_COUNTDOWN_DEFEAT.get(), GameResult.DEFEAT);
                    this.broadcast(Lang.DUNGEON_END_TIMEOUT, replacer -> replacer.replace(this.replacePlaceholders()));
                    return;
                }
            }
    
            if (this.isTasksCompleted() && !this.stageCompleted) {
                this.handleStageEnd();
            }
        }
    
        private void holdChunks() {
            this.config.getCuboid().getIntersectingChunks(this.world).forEach(chunk -> {
                if (chunk.addPluginChunkTicket(this.plugin)) {
                    //this.plugin.info("Chunk ticket added: " + ChunkPos.from(chunk));
                }
            });
        }
    
        private void unholdChunks() {
            this.config.getCuboid().getIntersectingChunks(this.world).forEach(chunk -> {
                if (chunk.removePluginChunkTicket(this.plugin)) {
                    //this.plugin.info("Chunk ticket removed: " + ChunkPos.from(chunk));
                }
            });
        }
    
        private void broadcastEvent(@NotNull DungeonGameEvent event) {
            this.plugin.getPluginManager().callEvent(event);
            if (event instanceof Cancellable cancellable && cancellable.isCancelled()) return;
    
            // Copy to prevent new stage/levels to handle that event if they were changed during it.
            List<DungeonEventReceiver> receivers = new ArrayList<>(this.eventReceivers);
    
            receivers.forEach(receiver -> receiver.onDungeonEventBroadcastReceive(event, event.getType(), this));
    
            // Extra set to call finish events for only tasks completed in the first loop
            // and to prevent ConcurrentModificationException since TaskFinishEvent may produce new tasks added by script actions.
            Set<StageTask> completedTasks = new HashSet<>();
    
            this.taskProgress.forEach((stageTask, progress) -> {
                if (progress.isCompleted()) return;
    
                stageTask.getTask().progress(event, this, stageTask, progress);
    
                if (progress.isCompleted()) completedTasks.add(stageTask);
            });
    
            // Call task finish events in a different loop to prevent event-in-event recursion and associated issues.
            completedTasks.forEach(stageTask -> {
                var progress = this.taskProgress.get(stageTask);
                if (progress == null) return;
    
                this.broadcastEvent(new DungeonTaskFinishedEvent(this, stageTask, progress));
                this.broadcast(Lang.DUNGEON_TASK_COMPLETED_INFO, replacer -> replacer
                    .replace(this.replacePlaceholders())
                    .replace(Placeholders.GENERIC_NAME, stageTask.getParams().getDisplay())
                );
            });
        }
    
        public void broadcast(@NotNull MessageLocale locale, @NotNull Consumer<Replacer> consumer) {
            this.getPlayers().forEach(player -> this.getPrefixed(locale).send(player.getPlayer(), consumer));
        }
    
        public void broadcast(@NotNull MessageLocale locale, @NotNull BiConsumer<Player, Replacer> consumer) {
            this.getPlayers().forEach(gamer -> this.getPrefixed(locale).send(gamer.getPlayer(), replacer -> consumer.accept(gamer.getPlayer(), replacer)));
        }
    
        public void broadcast(@NotNull String message) {
            this.getPlayers().forEach(gamer -> Players.sendMessage(gamer.getPlayer(), message));
        }
    
        public void sendMessage(@NotNull Player player, @NotNull MessageLocale locale, @NotNull Consumer<Replacer> consumer) {
            this.getPrefixed(locale).send(player, consumer);
        }
    
        public void runCommand(@NotNull List<String> commands, @NotNull DungeonTarget target, @Nullable DungeonGameEvent event) {
            if (target == DungeonTarget.GLOBAL) {
                commands.forEach(command -> {
                    this.plugin.getServer().dispatchCommand(this.plugin.getServer().getConsoleSender(), command);
                });
                return;
            }
    
            this.runForPlayers(target, event, gamer -> Players.dispatchCommands(gamer.getPlayer(), commands));
        }
    
        public void giveReward(@NotNull GameReward reward, boolean instant, @NotNull DungeonTarget target, @Nullable DungeonGameEvent event) {
            if (target == DungeonTarget.GLOBAL) {
                ErrorHandler.error("Reward must have player-specific target, not " + target.name() + ".", this);
                return;
            }
    
            this.runForPlayers(target, event, gamer -> this.giveReward(gamer, reward, instant));
        }
    
        public void giveReward(@NotNull DungeonGamer gamer, @NotNull GameReward gameReward, boolean instant) {
            Reward reward = gameReward.getReward();
            Player player = gamer.getPlayer();
    
            if (instant) reward.give(this, gamer);
            else gamer.addReward(gameReward);
    
            this.sendMessage(player, Lang.DUNGEON_GAME_REWARD_RECEIVED, replacer -> replacer.replace(this.replacePlaceholders()).replace(reward.replacePlaceholders()));
        }
    
        private void runForPlayers(@NotNull DungeonTarget target, @Nullable DungeonGameEvent event, @NotNull Consumer<DungeonGamer> consumer) {
            if (target == DungeonTarget.EVENT_PLAYER) {
                if (!(event instanceof GamerEvent gamerEvent)) return;
    
                DungeonGamer gamer = gamerEvent.getGamer();
                if (gamer != null) {
                    consumer.accept(gamer);
                }
                return;
            }
    
            this.getPlayers().forEach(gamer -> {
                if (!gamer.isDead() || target == DungeonTarget.ALL_PLAYERS) {
                    consumer.accept(gamer);
                }
            });
        }
    
        @NotNull
        private LangMessage getPrefixed(@NotNull MessageLocale locale) {
            return locale.withPrefix(this.prefix);
        }
    
        private void showStatus() {
            switch (this.state) {
                case WAITING -> this.broadcast(Lang.DUNGEON_STATUS_LOBBY_WAITING, replacer -> replacer
                    .replace(Placeholders.GENERIC_CURRENT, String.valueOf(this.countPlayers()))
                    .replace(Placeholders.GENERIC_MIN, String.valueOf(this.config.gameSettings().getMinPlayers()))
                );
                case READY -> {
                    boolean isClose = this.countdown <= 10;
                    this.broadcast((isClose ? Lang.DUNGEON_STATUS_LOBBY_READY_CLOSE : Lang.DUNGEON_STATUS_LOBBY_READY_FAR), replacer -> replacer
                        .replace(Placeholders.GENERIC_TIME, TimeFormats.toDigitalShort(TimeUnit.SECONDS.toMillis(this.countdown))));
                }
                case INGAME -> {
                    if (!this.isAboutToEnd()) return;
    
                    this.broadcast((this.gameResult == GameResult.VICTORY ? Lang.DUNGEON_STATUS_ENDING_VICTORY : Lang.DUNGEON_STATUS_ENDING_DEFEAT), replacer -> replacer
                        .replace(Placeholders.GENERIC_TIME, TimeFormats.toDigitalShort(TimeUnit.SECONDS.toMillis(this.countdown))));
                }
            }
        }
    
        private void spawnPlayer(@NotNull DungeonGamer gamer) {
            Player player = gamer.getPlayer();
    
            Kit kit = gamer.getKit();
            if (this.isKitsMode() && kit != null) {
                kit.applyPotionEffects(player);
                kit.applyAttributeModifiers(player);
            }
    
            gamer.teleport(this.getSpawnLocation()); // Teleport to current level's spawn.
            gamer.setState(GameState.INGAME);
            player.setHealth(EntityUtil.getAttribute(player, Attribute.MAX_HEALTH)); // Restore health.
    
            this.taskProgress.forEach((stageTask, progress) -> progress.onPlayerJoined(gamer)); // Adjust task progress for new players amount.
    
            Lang.DUNGEON_GAME_STARTED.message().send(player);
        }
    
        private void leavePlayer(@NotNull DungeonPlayer gamer) {
            this.plugin.getDungeonManager().leaveInstance((DungeonGamer) gamer);
        }
    
        public boolean hasPermission(@NotNull Player player) {
            if (!this.config.features().isPermissionRequired()) return true;
    
            return player.hasPermission(Perms.PREFIX_DUNGEON + this.getId()) || player.hasPermission(Perms.DUNGEON_ALL);
        }
    
        public boolean canAffordEntrance(@NotNull Player player) {
            if (!this.config.features().hasEntranceCost()) return true;
    
            return this.config.features().getEntranceCostMap().entrySet().stream().allMatch(entry -> EconomyBridge.hasEnough(player, entry.getKey(), entry.getValue()));
        }
    
        public boolean hasGoodLevel(@NotNull Player player) {
            LevelRequirement requirement = this.config.features().getLevelRequirement();
            if (!requirement.isRequired()) return true;
    
            return requirement.isGoodLevel(player);
        }
    
        public void payEntrance(@NotNull Player player) {
            this.config.features().getEntranceCostMap().forEach((id, price) -> {
                EconomyBridge.withdraw(player, id, price);
            });
        }
    
        public void refundEntrance(@NotNull Player player) {
            this.config.features().getEntranceCostMap().forEach((id, price) -> {
                EconomyBridge.deposit(player, id, price);
            });
        }
    
        public void confiscateBadItems(@NotNull Player player, @NotNull List<ItemStack> confiscate) {
            ItemFilterMode mode = this.config.features().getItemFilterMode();
            if (mode == ItemFilterMode.NONE) return;
    
            ItemFilterCriteria criteria = this.config.features().getItemFilterCriteria();
    
            for (ItemStack itemStack : player.getInventory().getContents()) {
                if (itemStack == null) continue;
    
                boolean matched = criteria.matches(itemStack);
                boolean needConfiscated = (mode == ItemFilterMode.BAN_SPECIFIC && matched) || (mode == ItemFilterMode.ALLOW_SPECIFIC && !matched);
    
                if (needConfiscated) {
                    confiscate.add(new ItemStack(itemStack));
                    itemStack.setAmount(0);
                }
            }
    
            if (!confiscate.isEmpty()) {
                this.getPrefixed(Lang.DUNGEON_CONFISACATE_INFO).send(player, replacer -> replacer
                    .replace(this.replacePlaceholders())
                    .replace(Placeholders.GENERIC_ITEM, confiscate.stream().map(ItemUtil::getNameSerialized).collect(Collectors.joining(", ")))
                );
            }
        }
    
        public boolean canJoin(@NotNull Player player, boolean force, boolean notify) {
            if (!this.isActive()) {
                if (notify) this.sendMessage(player, Lang.DUNGEON_ENTER_ERROR_INACTIVE, replacer -> replacer.replace(this.replacePlaceholders()));
                return false;
            }
    
            if (this.isAboutToEnd()) {
                if (notify) this.sendMessage(player, Lang.DUNGEON_ENTER_ERROR_ENDING, replacer -> replacer.replace(this.replacePlaceholders()));
                return false;
            }



            if (this.players.size() >= 1) { // someone is already in the dungeon
                DungeonGamer firstPlayer = this.players.values().iterator().next();
                if (plugin.getSoloManager().isSolo(firstPlayer.getPlayer().getUniqueId())) {
                    if (!this.isInQueue(player)) {
                        this.addToQueue(player, null);
                        player.sendMessage(net.kyori.adventure.text.Component.text("This dungeon has a solo player, you have been added to the queue.").color(net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                    }
                    return false; // stops the join entirely
                }

                // Check if player in game is on party
                if (plugin.getPartyManager().hasParty(firstPlayer.getPlayer().getUniqueId())) {
                    Party party = plugin.getPartyManager().getPartyOf(firstPlayer.getPlayer().getUniqueId());
                    if (party != null && !party.isMember(player.getUniqueId())) {
                        if (!this.isInQueue(player)) {
                            this.addToQueue(player, null);
                            player.sendMessage(net.kyori.adventure.text.Component.text("This dungeon is reserved for a party, you have been added to the queue.").color(net.kyori.adventure.text.format.NamedTextColor.YELLOW));
                        }
                        return false;
                    }
                }


            }
    
    
            if (!force) {
                if (this.config.features().isPermissionRequired() && !this.hasPermission(player)) {
                    if (notify) this.sendMessage(player, Lang.DUNGEON_ENTER_ERROR_PERMISSION, replacer -> replacer.replace(this.replacePlaceholders()));
                    return false;
                }
    
                if (!player.hasPermission(Perms.BYPASS_DUNGEON_COOLDOWN)) {
                    DungeonUser user = this.plugin.getUserManager().getOrFetch(player);
                    if (user.isOnCooldown(this)) {
                        if (notify) this.sendMessage(player, Lang.DUNGEON_ENTER_ERROR_COOLDOWN, replacer -> replacer
                            .replace(Placeholders.GENERIC_TIME, TimeFormats.formatDuration(user.getArenaCooldown(this), TimeFormatType.LITERAL))
                            .replace(this.replacePlaceholders()));
                        return false;
                    }
                }
    
                if (this.state == GameState.INGAME && !player.hasPermission(Perms.BYPASS_DUNGEON_JOIN_STARTED)) {
                    if (notify) this.sendMessage(player, Lang.DUNGEON_ENTER_ERROR_STARTED, replacer -> replacer.replace(this.replacePlaceholders()));
                    return false;
                }
    
                int playerMax = this.config.gameSettings().getMaxPlayers();
                if (playerMax > 0 && this.countPlayers() >= playerMax) {
                    if (notify) this.sendMessage(player, Lang.DUNGEON_ENTER_ERROR_MAX_PLAYERS, replacer -> replacer.replace(this.replacePlaceholders()));
                    return false;
                }
    
                if (!player.hasPermission(Perms.BYPASS_DUNGEON_ENTRANCE_COST)) {
                    if (!this.canAffordEntrance(player)) {
                        if (notify) this.sendMessage(player, Lang.DUNGEON_ENTER_ERROR_COST, replacer -> replacer.replace(this.replacePlaceholders()));
                        return false;
                    }
                }
    
                if (!player.hasPermission(Perms.BYPASS_DUNGEON_ENTRANCE_LEVEL)) {
                    if (!this.hasGoodLevel(player)) {
                        if (notify) this.sendMessage(player, Lang.DUNGEON_ENTER_ERROR_LEVEL, replacer -> replacer.replace(this.replacePlaceholders()));
                        return false;
                    }
                }
            }
    
            return true;
        }
    
        @Override
        public void handlePlayerJoin(@NotNull DungeonPlayer dungeonPlayer, boolean forced) {
            Player player = dungeonPlayer.getPlayer();
            DungeonGamer gamer = (DungeonGamer) dungeonPlayer;
    
            Kit kit = gamer.getKit();
    
            // Save the player inventory, effects, game modes, etc. before teleporting to the arena.
            PlayerSnapshot snapshot = PlayerSnapshot.doSnapshot(player);
    
            if (!forced) {
                // Pay for the entrance (and kit) before cleaning.
                if (!player.hasPermission(Perms.BYPASS_DUNGEON_ENTRANCE_COST)) {
                    this.payEntrance(player);
                }
                if (kit != null && KitUtils.isRentMode() && kit.hasCost() && !player.hasPermission(Perms.BYPASS_KIT_COST)) {
                    kit.takeCosts(player);
                }
            }
    
            // Now clear all player's active effects, god modes, etc.
            gamer.teleport(this.getLobbyLocation());
            player.setGameMode(this.getGameMode());
            PlayerSnapshot.clear(player);
            Players.dispatchCommands(player, this.config.features().getEntranceCommands());
            UniParticle.of(Particle.CLOUD).play(player.getLocation(), 0.25, 0.15, 30);
    
            if (this.isKitsMode() && kit != null) {
                player.getInventory().clear();
                kit.give(player);
            }
            else {
                this.confiscateBadItems(player, snapshot.getConfiscate()); // TODO Permission?
            }
    
            this.sendMessage(player, Lang.DUNGEON_JOIN_LOBBY, replacer -> replacer.replace(this.replacePlaceholders()));
            this.broadcast(Lang.DUNGEON_JOIN_NOTIFY, replacer -> replacer.replace(this.replacePlaceholders()).replace(gamer.replacePlaceholders()));
    
            this.players.put(player.getUniqueId(), gamer);
    
            // Disable external Scoreboard and God mode.
            gamer.manageExternalBoard(boardPlugin -> boardPlugin.disableBoard(player));
            gamer.manageExternalGod(godPlugin -> godPlugin.disableGod(player));
    
            if (this.config.gameSettings().isScoreboardEnabled() && DungeonUtils.hasPacketLibrary()) {
                gamer.addBoard();
            }
            // Add orbs
            String visualClass = plugin.getClassManager().getClass(player);
            if (visualClass != null && !visualClass.isEmpty()) {
                plugin.getOrbManager().giveOrbs(player, visualClass);
            }


            // this.updateSigns();
    
            if (this.state == GameState.INGAME) {
                gamer.setDead(true);
                player.setGameMode(GameMode.SPECTATOR);
                gamer.teleport(this.getSpawnLocation());
            }



        }
    
        @Override
        public void handlePlayerLeave(@NotNull DungeonPlayer dungeonPlayer) {
            DungeonGamer gamer = (DungeonGamer) dungeonPlayer;
            Player player = gamer.getPlayer();


            // Disband party if leader, or leave party if member
            UUID playerId = player.getUniqueId();
            if (plugin.getPartyManager().hasParty(playerId)) {
                plugin.getPartyManager().leaveParty(playerId); // handles both leader (disband) and member (leave)
            }

            plugin.getOrbManager().confiscateAll(player);
            player.closeInventory();
            gamer.removeBoard();
            this.players.remove(player.getUniqueId());
            plugin.getSoloManager().clearOnLeave(player.getUniqueId()); // If solo mode, then remove from solo mode
            unlockPlayerChestInteraction(player);
            FinishChestRewardManager.playersSpecificRewardInventory.remove(player.getUniqueId()); // Remove player from chest reward inventories if they have one open


            System.out.println("GameState: " + this.state);
            // Note: It goes "READY" when player leaves while the state is on lobby waiting, so we check if player leaves and solo os we can move to next queue
            if (this.state == GameState.READY && this.players.isEmpty()) {
                this.moveToNextInQueue();
            }
    
            if (this.state == GameState.INGAME && this.gameResult == null && gamer.isInGame()) {
                this.taskProgress.forEach((stageTask, progress) -> progress.onPlayerLeft(gamer));
            }
    
            if (!this.isAboutToEnd() || this.gameResult == GameResult.DEFEAT) {
                gamer.takeDefeatRewards();
            }
    
            // Remove kit effects.
            Kit kit = gamer.getKit();
            if (kit != null) {
                kit.resetPotionEffects(player);
                kit.resetAttributeModifiers(player);
            }
    
            // Restore player data.
            PlayerSnapshot.restore(gamer);
            Players.dispatchCommands(player, this.config.features().getExitCommands());
    
            // Refund payments.
            if (this.state != GameState.INGAME) {
                if (!player.hasPermission(Perms.BYPASS_DUNGEON_ENTRANCE_COST)) {
                    this.refundEntrance(player);
                }
                if (kit != null && KitUtils.isRentMode() && kit.hasCost() && !player.hasPermission(Perms.BYPASS_KIT_COST)) {
                    kit.refundCosts(player);
                }
            }
            else {
                gamer.getRewards().forEach(reward -> reward.getReward().give(this, gamer));
    
                // Set cooldown only if dungeon have been started.
                if (!player.hasPermission(Perms.BYPASS_DUNGEON_COOLDOWN)) {
                    this.plugin.getDungeonManager().setJoinCooldown(player, this);
                }
            }
    
            // Enable back external Scoreboard and God mode.
            gamer.manageExternalBoard(boardPlugin -> boardPlugin.enableBoard(player));
            gamer.manageExternalGod(godPlugin -> godPlugin.enableGod(player));
        }
    
        public void handlePlayerDeath(@NotNull DungeonGamer gamer) {
            boolean hasExtraLives = gamer.hasExtraLives();
    
            gamer.handleDeath();
    
            if (!hasExtraLives && this.config.gameSettings().isLeaveOnDeath()) {
                this.plugin.runTask(task -> this.leavePlayer(gamer));
            }
    
            DungeonPlayerDeathEvent event = new DungeonPlayerDeathEvent(this, gamer);
            this.broadcastEvent(event);
        }
    
        public void handleMobDeath(@NotNull DungeonEntity mob, @NotNull EntityDeathEvent event) {
            if (!this.config.gameSettings().isMobsDropLoot()) {
                event.getDrops().clear();
            }
            if (!this.config.gameSettings().isMobsDropXP()) {
                event.setDroppedExp(0);
            }
    
            DungeonMobKilledEvent mobDeathEvent = new DungeonMobKilledEvent(this, mob);
    
            LivingEntity entity = mob.getBukkitEntity();
            Player killer = entity.getKiller();
            DungeonGamer gamer = killer == null ? null : this.getPlayer(killer.getUniqueId());
            if (killer != null && gamer != null) {
                mobDeathEvent.setGamer(gamer);
    
                int streak = gamer.getKillStreak() + 1;
                gamer.addKill();
                gamer.setKillStreak(streak);
                gamer.setKillStreakDecay(Config.KILL_STREAKS_DECAY_TIME.get());
    
                if (DungeonUtils.isKillStreaksEnabled()) {
                    KillStreak killStreak = DungeonUtils.getKillStreak(gamer.getKillStreak());
                    if (killStreak != null) {
                        killStreak.run(this, gamer);
                    }
                }
            }
    
            this.broadcastEvent(mobDeathEvent);
            this.eliminateMob(mob);
        }
    
        public void handleMobSpawn(@NotNull LivingEntity entity) {
            if (MobUitls.isPet(entity)) {
                if (!this.config.gameSettings().isPetsAllowed()) {
                    entity.remove();
                }
                return;
            }
    
            if (this.hasMob(entity.getUniqueId())) return;
    
            MobProvider provider = MobRegistry.getProvider(entity);
            String mobId = provider == null ? null : provider.getMobId(entity);
            if (provider == null || mobId == null) {
                if (Config.MOBS_REMOVE_UNKNOWN_MOBS.get()) {
                    entity.remove();
                }
                return;
            }
    
            boolean isTamed = entity instanceof Tameable tameable && tameable.isTamed();
    
            MobIdentifier identifier = MobIdentifier.from(provider, mobId);
            MobFaction faction = MobUitls.isExternalAlly(identifier) || isTamed ? MobFaction.ALLY : MobFaction.ENEMY;
    
            DungeonMob dungeonMob = new DungeonMob(this, entity, faction, provider, mobId);
            this.addMob(dungeonMob);
        }
    
        public void handleStageEnd() {
            this.stageCompleted = true;
            this.broadcastEvent(new DungeonStageFinishEvent(this, this.stage));
        }
    
        public boolean isReadyToStart() {
            return this.hasMinimumPlayers();
        }
    
        public boolean hasMinimumPlayers() {
            return this.countPlayers() >= this.config.gameSettings().getMinPlayers();
        }
    
        public void setLevel(@NotNull Level level) {
            if (this.isLevel(level)) return;
    
            this.level = level;
            this.updateListeners();
            this.broadcastEvent(new DungeonLevelStartEvent(this, level));
            this.broadcast(Lang.DUNGEON_GAME_LEVEL_CHANGED, replacer -> replacer.replace(this.replacePlaceholders()).replace(level.replacePlaceholders()));
        }
    
        public boolean isLevel(@NotNull Level level) {
            return this.level == level;
        }
    
        public void setStage(@NotNull Stage stage) {
            if (this.isStage(stage)) return;
    
            this.removeTasks();
            this.stage = stage;
            this.stageCompleted = false;
            this.updateListeners();
            this.addTasks(stage);
            this.broadcastEvent(new DungeonStageStartEvent(this, stage));
            this.broadcast(Lang.DUNGEON_GAME_STAGE_CHANGED, replacer -> replacer.replace(this.replacePlaceholders()).replace(stage.replacePlaceholders()));
        }
    
        public boolean isStage(@NotNull Stage stage) {
            return this.stage == stage;
        }
    
    
    
        public boolean isAboutToEnd() {
            return this.state == GameState.INGAME && this.countdown >= 0 && this.gameResult != null;
        }
    
    
    
        private void eliminateDeadMobs() {
            this.getMobs().forEach(mob -> {
                if (mob.isDead()) {
                    this.eliminateMob(mob);
                }
            });
        }
    
        @Override
        public void killMobs() {
            this.getMobs().forEach(dungeonMob -> dungeonMob.getBukkitEntity().remove());
            this.getMobs().forEach(this::eliminateMob);
            this.mobByIdMap.clear();
        }
    
        @Override
        public boolean isAllyMob(@NotNull LivingEntity entity) {
            return this.getMobFaction(entity) == MobFaction.ALLY;
        }
    
        @Override
        public boolean isEnemyMob(@NotNull LivingEntity entity) {
            return this.getMobFaction(entity) == MobFaction.ENEMY;
        }
    
        @Override
        public boolean hasAllyMobs() {
            return this.hasMobsOfFaction(MobFaction.ALLY);
        }
    
        @Override
        public boolean hasEnemyMobs() {
            return this.hasMobsOfFaction(MobFaction.ENEMY);
        }
    
        @Override
        public boolean hasMobsOfFaction(@NotNull MobFaction faction) {
            return this.getMobs().stream().anyMatch(mob -> mob.isFaction(faction));
        }
    
        @Override
        @Nullable
        public MobFaction getMobFaction(@NotNull LivingEntity entity) {
            DungeonMob mob = this.getMob(entity);
            return mob == null ? null : mob.getFaction();
        }
    
        @Override
        public void eliminateMob(@NotNull DungeonEntity mob) {
            mob.getBukkitEntity().getLocation().getChunk(); // Load chunk to remove entity lol.
            mob.getBukkitEntity().setPersistent(false);
            if (mob.isAlive()) {
                mob.getBukkitEntity().remove();
            }
            this.stats.addMobKill(mob);
            this.removeMob(mob);
            this.broadcastEvent(new DungeonMobEliminatedEvent(this, mob));
            DungeonEntityBridge.removeHolder(mob);
        }
    
        public boolean spawnAllyMob(@NotNull EntityType entityType, @NotNull Location location, int level) {
            MobIdentifier identifier = MobUitls.getEggAllyIdentifier(entityType);
            if (identifier == null) return false;
    
            MobProvider provider = MobRegistry.getProviderByName(identifier.getProviderId());
            if (provider == null) return false;
    
            String mobId = identifier.getMobId();
            MobFaction faction = MobFaction.ALLY;
    
            this.spawnMob(provider, mobId, faction, location, level);
            return true;
        }
    
        @Override
        public void spawnMob(@NotNull MobProvider provider, @NotNull String mobId, @NotNull MobFaction faction, @NotNull DungeonSpawner spawner, int level, int amount) {
            if (spawner.isEmpty()) {
                ErrorHandler.error("Could not spawn mob '" + provider.getName() + ":" + mobId + "' at empty spawner '" + spawner.getId() + "'!", this);
                return;
            }
    
            for (int i = 0; i < amount; i++) {
                Location location = spawner.getRandomPosition().toLocation(this.world)
                    .add(MobUitls.getRandomSpawnOffset(), 0, MobUitls.getRandomSpawnOffset());
    
                this.spawnMob(provider, mobId, faction, location, level);
            }
        }
    
        @Override
        public void spawnMob(@NotNull MobProvider provider, @NotNull String mobId, @NotNull MobFaction faction, @NotNull Location location, int level) {
            LivingEntity mob = provider.spawn(this, mobId, faction, location, level, entity -> {
                // Probably don't need to store providerId and mobId, since these values are already stored in a DungeonMob.
            });
            if (mob == null) {
                ErrorHandler.error("Could not spawn mob '" + provider.getName() + ":" + mobId + "', spawned entity is null!", this);
                return;
            }
    
            DungeonMob dungeonMob = new DungeonMob(this, mob, faction, provider, mobId);
    
            this.addMob(dungeonMob);
            this.broadcastEvent(new DungeonMobSpawnedEvent(this, dungeonMob));
        }
    
        @Override
        public void addMob(@NotNull DungeonEntity mob) {
            this.mobByIdMap.put(mob.getUniqueId(), (DungeonMob) mob);
            this.stats.addMobSpawn(mob);
    
            LivingEntity entity = mob.getBukkitEntity();
    
            MobUitls.setDungeonId(entity, this);
            entity.setPersistent(true);
            entity.setRemoveWhenFarAway(false);
            DungeonEntityBridge.addHolder(mob);
        }
    
        @Override
        public void removeMob(@NotNull DungeonEntity mob) {
            this.removeMob(mob.getUniqueId());
        }
    
        @Override
        public void removeMob(@NotNull UUID mobId) {
            this.mobByIdMap.remove(mobId);
        }
    
        @Override
        public boolean hasMob(@NotNull UUID mobId) {
            return this.mobByIdMap.containsKey(mobId);
        }
    
        @Override
        @Nullable
        public DungeonMob getMob(@NotNull LivingEntity entity) {
            return this.getMobById(entity.getUniqueId());
        }
    
        @Override
        @Nullable
        public DungeonMob getMobById(@NotNull UUID mobId) {
            return this.mobByIdMap.get(mobId);
        }
    
        @Override
        @NotNull
        public Set<DungeonMob> getAllyMobs() {
            return this.getMobs(MobFaction.ALLY);
        }
    
        @Override
        @NotNull
        public Set<DungeonMob> getEnemyMobs() {
            return this.getMobs(MobFaction.ENEMY);
        }
    
        @Override
        @NotNull
        public Set<DungeonMob> getMobs() {
            return new HashSet<>(this.mobByIdMap.values());
        }
    
        @Override
        @NotNull
        public Set<DungeonMob> getMobs(@NotNull MobFaction faction) {
            return this.queryMobs(mob -> mob.isFaction(faction));
        }
    
        @NotNull
        public Set<DungeonMob> queryMobs(@NotNull Predicate<CriterionMob> predicate) {
            return this.getMobs().stream().filter(predicate).collect(Collectors.toSet());
        }
    
        public int countMobs(@NotNull MobFaction faction) {
            return this.countMobs(mob -> MobCriterias.FACTION.predicate(faction).test(mob));
        }
    
        public int countMobs(@NotNull Predicate<CriterionMob> predicate) {
            return this.queryMobs(predicate).size();
        }
    
    
    
        public void addTasks(@NotNull Stage stage) {
            stage.getTasks().forEach(stageTask -> {
                if (stageTask.getParams().isAutoAdd()) {
                    this.addTask(stageTask);
                }
            });
        }
    
        public void addTask(@NotNull StageTask stageTask) {
            TaskProgress progress = stageTask.createProgress(this);
            if (progress.isEmpty()) return;
    
            stageTask.getTask().onTaskAdd(this, stageTask, progress);
    
            this.taskProgress.put(stageTask, progress);
            this.stageCompleted = false;
    
            boolean isPersonal = stageTask.getParams().isPerPlayer();
    
            this.broadcast((isPersonal ? Lang.DUNGEON_TASK_CREATED_PERSONAL : Lang.DUNGEON_TASK_CREATED_GLOBAL), (player, replacer) -> replacer
                .replace(this.replacePlaceholders())
                .replace(Placeholders.GENERIC_NAME, stageTask.getParams().getDisplay())
                .replace(Placeholders.GENERIC_VALUE, progress.format(isPersonal ? player : null)));
    
            this.broadcastEvent(new DungeonTaskCreatedEvent(this, stageTask, progress));
        }
    
        public void removeTask(@NotNull StageTask stageTask) {
            TaskProgress progress = this.taskProgress.remove(stageTask);
            if (progress == null) return;
    
            stageTask.getTask().onTaskRemove(this, stageTask, progress);
        }
    
        public void removeTasks() {
            this.getTasks().forEach(this::removeTask);
        }
    
        @NotNull
        public Set<StageTask> getTasks() {
            return new HashSet<>(this.taskProgress.keySet());
        }
    
        public boolean hasTask(@NotNull StageTask stageTask) {
            return this.taskProgress.containsKey(stageTask);
        }
    
        public boolean hasTasks() {
            return !this.taskProgress.isEmpty();
        }
    
        public boolean isTaskCompleted(@NotNull StageTask stageTask) {
            TaskProgress progress = this.taskProgress.get(stageTask);
            return progress != null && progress.isCompleted();
        }
    
        public boolean isTasksCompleted() {
            return !this.taskProgress.isEmpty() && this.taskProgress.values().stream().allMatch(TaskProgress::isCompleted);
        }
    
    
    
    
    
        public void refillLootChests() {
            this.config.getLootChests().forEach(this::refillLootChest);
        }
    
        public void refillLootChest(@NotNull LootChest lootChest) {
            lootChest.generateLoot(this);
        }
    
        public void clearLootChests() {
            this.config.getLootChests().forEach(this::clearLootChest);
        }
    
        public void clearLootChest(@NotNull LootChest lootChest) {
            lootChest.clearLoot(this);
        }
    
    
    
    
    
        public boolean isKitsMode() {
            return this.config.gameSettings().isKitsEnabled();
        }
    
        public boolean isKitAllowed(@NotNull Kit kit) {
            return this.config.gameSettings().isKitAllowed(kit.getId());
        }
    
        public boolean isKitAvailable(@NotNull Kit kit) {
            if (!this.isKitAllowed(kit)) {
                return false;
            }
    
            return !this.isKitLimitReached(kit);
        }
    
        public boolean isKitLimitReached(@NotNull Kit kit) {
            return this.countKitFreeSlots(kit) == 0;
        }
    
        public int getKitLimit(@NotNull Kit kit) {
            return this.config.gameSettings().getKitLimit(kit.getId());
        }
    
        public int countKitInUse(@NotNull Kit kit) {
            return (int) this.getPlayers().stream().filter(gamer -> gamer.isKit(kit)).count();
        }
    
        public int countKitFreeSlots(@NotNull Kit kit) {
            int limit = this.getKitLimit(kit);
            if (limit < 0) return -1;
    
            return limit - this.countKitInUse(kit);
        }
    
    
        @Override
        public boolean hasPlayer(@NotNull UUID playerId) {
            return this.players.containsKey(playerId);
        }
    
        @Override
        public boolean hasAlivePlayers() {
            return this.players.values().stream().anyMatch(DungeonPlayer::isAlive);
        }
    
        @Override
        public int countPlayers() {
            return this.players.size();
        }
    
        @Override
        public int countAlivePlayers() {
            return this.getAlivePlayers().size();
        }
    
        @Override
        public int countDeadPlayers() {
            return this.getDeadPlayers().size();
        }
    
        @Override
        @NotNull
        public DungeonGamer getRandomAlivePlayer() {
            return Rnd.get(this.getPlayers());
        }
    
        @Override
        @Nullable
        public DungeonGamer getPlayer(@NotNull UUID playerId) {
            return this.players.get(playerId);
        }
    
        @Override
        @NotNull
        public Set<DungeonGamer> getPlayers() {
            return new HashSet<>(this.players.values());
        }
    
        @NotNull
        public Set<DungeonGamer> getAlivePlayers() {
            return this.players.values().stream().filter(DungeonPlayer::isAlive).collect(Collectors.toSet());
        }
    
        @NotNull
        public Set<DungeonGamer> getDeadPlayers() {
            return this.players.values().stream().filter(DungeonPlayer::isDead).collect(Collectors.toSet());
        }
    
    
    
    
        public void addGroundItem(@NotNull Item item) {
            this.groundItems.removeIf(other -> !other.isValid());
            this.groundItems.add(item);
    
            if (this.state == GameState.INGAME && !this.config.gameSettings().isItemPickupAllowed()) {
                item.setPickupDelay(Short.MAX_VALUE);
                item.setOwner(UUID.randomUUID());
            }
        }
    
        public void burnGroundItems() {
            UniParticle particle = UniParticle.of(Particle.SMOKE);
            this.groundItems.removeIf(item -> {
                if (!item.isValid()) return true;
                if (!item.isOnGround()) return false;
    
                particle.play(item.getLocation(), 0.1, 0.05, 15);
                item.remove();
                return true;
            });
        }
    
        public void killGroundItems() {
            this.groundItems.forEach(Entity::remove);
            this.groundItems.clear();
        }
    
        @NotNull
        public Set<Item> getGroundItems() {
            return this.groundItems;
        }
    
    
    
    
        public void resetSpotStates() {
            this.config.getSpots().forEach(this::resetSpotState);
        }
    
        public void resetSpotState(@NotNull Spot spot) {
            SpotState state = spot.getDefaultState();
            if (state == null) return;
    
            this.setSpotState(spot, state);
        }
    
        public void setSpotState(@NotNull Spot spot, @NotNull SpotState state) {
            if (!this.isActive()) return;
    
            spot.build(this.world, state);
            spot.setLastState(state);
    
            this.broadcastEvent(new DungeonSpotChangeEvent(this, spot, state));
        }
    
    
    
    
        @Override
        public boolean contains(@NotNull BlockPos blockPos) {
            return this.config.isInProtection(blockPos);
        }
    
        @Override
        public boolean contains(@NotNull Block block) {
            return this.config.isInProtection(block);
        }
    
        @Override
        public boolean contains(@NotNull Location location) {
            return this.config.isInProtection(location);
        }
    
        @Override
        public boolean contains(@NotNull Entity entity) {
            return this.config.isInProtection(entity);
        }
    
    
    
        @Override
        public long getTickCount() {
            return this.tickCount;
        }
    
        @Override
        @NotNull
        public String getId() {
            return this.config.getId();
        }
    
        @NotNull
        public DungeonConfig getConfig() {
            return this.config;
        }
    
        @NotNull
        public DungeonStats getStats() {
            return this.stats;
        }
    
        @NotNull
        public DungeonVariables getVariables() {
            return this.variables;
        }
    
        @Override
        @NotNull
        public GameState getState() {
            return this.state;
        }
    
        @NotNull
        public Location getLobbyLocation() {
            return this.config.getLobbyPos().toLocation(this.world);
        }
    
        /**
         *
         * @return Spawn location of the current dungeon level.
         */
        @NotNull
        public Location getSpawnLocation() {
            return this.level.getSpawnLocation(this.world);
        }
    
        public int getCountdown() {
            return this.countdown;
        }
    
        public void setCountdown(int countdown) {
            this.countdown = countdown;
        }
    
        public void setCountdown(int countdown, @NotNull GameResult gameResult) {
            this.countdown = countdown;
            if (this.state == GameState.INGAME) {
                this.gameResult = gameResult;
            }
        }
    
        public long getTimeLeft() {
            return this.timeLeft;
        }
    
        public void setTimeLeft(long timeLeft) {
            this.timeLeft = timeLeft;
        }
    
        @NotNull
        public Level getLevel() {
            return this.level;
        }
    
        @NotNull
        public Stage getStage() {
            return this.stage;
        }
    
        @NotNull
        public GameMode getGameMode() {
            return this.config.gameSettings().isAdventureMode() ? GameMode.ADVENTURE : GameMode.SURVIVAL;
        }
    
        @NotNull
        public List<DungeonEventReceiver> getEventReceivers() {
            return this.eventReceivers;
        }
    
        @NotNull
        public Map<StageTask, TaskProgress> getTaskProgress() {
            return this.taskProgress;
        }

        // Handles the changing of queues
        private void moveToNextInQueue() {
            System.out.println("GAME ENDED: MOVE TO NEXT QUEUE");

            int maxPlayers = this.config.gameSettings().getMaxPlayers();

            while (!joinQueue.isEmpty()) {
                QueueEntry entry = joinQueue.peek();

                entry.players().removeIf(p -> !p.isOnline());
                if (entry.players().isEmpty()) { joinQueue.poll(); continue; }

                if (maxPlayers > 0 && this.countPlayers() + entry.players().size() > maxPlayers) break;

                boolean entrySolo = entry.isSolo() &&
                        plugin.getSoloManager().isSolo(entry.firstPlayer().getUniqueId());

                if (entrySolo && this.countPlayers() > 0) break;

                // If dungeon has a party inside, stop processing queue
                if (!this.players.isEmpty()) {
                    DungeonGamer firstPlayer = this.players.values().iterator().next();
                    UUID firstId = firstPlayer.getPlayer().getUniqueId();
                    if (plugin.getPartyManager().hasParty(firstId)) break;
                }

                joinQueue.poll();
                for (Player p : entry.players()) {
                    if (this.manager.isPlaying(p)) continue;
                    this.manager.enterInstance(p, this, entry.kit(), false);
                }



                if (entrySolo) break;
            }
        }

        private void sendDungeonEndReport() {
            StringBuilder report = new StringBuilder();
            report.append("§6[Dungeon Ended]\n");
            report.append("§eDungeon: §f").append(this.getConfig().getId()).append("\n");
            report.append("§ePlayers:\n");

            for (DungeonGamer gamer : gamersSnapshot) {
                Player player = gamer.getPlayer();

                player.sendMessage("§eDungeon ended checking queue");

                report.append(" §7- §f")
                        .append(player.getName())
                        .append(" §8(Lives: ").append(gamer.getLives())
                        .append(", Kills: ").append(gamer.getKills())
                        .append(", Score: ").append(gamer.getScore())
                        .append(")\n");
            }

            this.plugin.getLogger().info(report.toString());
        }

        public boolean isInQueue(@NotNull Player player) {
            return joinQueue.stream().anyMatch(e -> e.players().stream()
                    .anyMatch(p -> p.getUniqueId().equals(player.getUniqueId())));
        }

        public boolean isInQueue(@NotNull UUID playerId) {
            return joinQueue.stream().anyMatch(e -> e.players().stream()
                    .anyMatch(p -> p.getUniqueId().equals(playerId)));
        }

        public void addToQueue(@NotNull Player player, @Nullable Kit kit) {
            joinQueue.add(QueueEntry.ofPlayer(player, kit));
        }

        public void addPartyToQueue(@NotNull Party party, @Nullable Kit kit) {
            joinQueue.add(QueueEntry.ofParty(party, kit));
        }

        public int getQueuePosition(@NotNull Player player) {
            int pos = 1;
            for (QueueEntry entry : joinQueue) {
                if (entry.players().stream().anyMatch(p -> p.getUniqueId().equals(player.getUniqueId()))) return pos;
                pos++;
            }
            return -1;
        }

        public int getPartyQueuePosition(@NotNull Party party) {
            int pos = 1;
            for (QueueEntry entry : joinQueue) {
                if (entry.players().stream().anyMatch(p -> party.getAllMembers().contains(p.getUniqueId()))) return pos;
                pos++;
            }
            return -1;
        }

        public boolean removeFromQueue(@NotNull Player player) {
            return joinQueue.removeIf(e -> e.players().stream()
                    .anyMatch(p -> p.getUniqueId().equals(player.getUniqueId())));
        }

        public boolean hasSoloPlayer() {
            if (this.players.isEmpty()) return false;
            DungeonGamer firstPlayer = this.players.values().iterator().next();
            return plugin.getSoloManager().isSolo(firstPlayer.getPlayer().getUniqueId());
        }

        public boolean hasPartyPlayer() {
            if (this.players.isEmpty()) return false;
            DungeonGamer firstPlayer = this.players.values().iterator().next();
            return plugin.getPartyManager().hasParty(firstPlayer.getPlayer().getUniqueId());
        }

        public int getQueueLength() {
            return joinQueue.size();
        }

        public boolean isQueueHeadSolo() {
            QueueEntry head = joinQueue.peek();
            if (head == null) return false;
            Player first = head.firstPlayer();
            if (first == null || !first.isOnline()) return false;
            return head.isSolo() && plugin.getSoloManager().isSolo(first.getUniqueId());
        }

        public boolean isQueueHeadParty() {
            QueueEntry head = joinQueue.peek();
            if (head == null) return false;
            Player first = head.firstPlayer();
            if (first == null || !first.isOnline()) return false;
            return plugin.getPartyManager().hasParty(first.getUniqueId());
        }

        public void drainRandomQueueInto(@NotNull DungeonInstance target) {
            Iterator<QueueEntry> it = joinQueue.iterator();
            while (it.hasNext()) {
                QueueEntry entry = it.next();
                if (entry.isSolo()) continue; // skip solo entries
                if (entry.players().size() > 1) continue; // skip party entries

                it.remove();
                for (Player p : entry.players()) {
                    if (manager.isPlaying(p)) continue;
                    manager.enterInstance(p, target, entry.kit(), false);
                }
            }
        }

        public void addBoughtChestToInstance(@NotNull UUID playerUUID,
                                             @NotNull Location chestLocation) {
            this.chestBoughtByPlayers.put(playerUUID, chestLocation);
        }

        public void removeBoughtChestFromInstance(@NotNull UUID playerUUID) {
            this.chestBoughtByPlayers.remove(playerUUID);
        }

        public boolean isChestBoughtByPlayers(@NotNull Location chestLocation) {
            return this.chestBoughtByPlayers.containsValue(chestLocation);
        }

        public boolean isChestBuyer(UUID playerUUID, Location chestLocation) {
            Location playerBoughtLocation = this.chestBoughtByPlayers.get(playerUUID);
            return chestLocation.equals(playerBoughtLocation);
        }

    }
