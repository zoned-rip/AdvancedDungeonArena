    package su.nightexpress.dungeons.command.impl;
    
    import org.bukkit.entity.Player;
    import org.jetbrains.annotations.NotNull;
    import su.nightexpress.dungeons.DungeonPlugin;
    import su.nightexpress.dungeons.Placeholders;
    import su.nightexpress.dungeons.api.type.GameState;
    import su.nightexpress.dungeons.command.CommandArguments;
    import su.nightexpress.dungeons.command.CommandFlags;
    import su.nightexpress.dungeons.config.Lang;
    import su.nightexpress.dungeons.config.Perms;
    import su.nightexpress.dungeons.dungeon.Party.Party;
    import su.nightexpress.dungeons.dungeon.Party.PartyManager;
    import su.nightexpress.dungeons.dungeon.config.DungeonConfig;
    import su.nightexpress.dungeons.dungeon.game.DungeonInstance;
    import su.nightexpress.dungeons.dungeon.level.Level;
    import su.nightexpress.dungeons.dungeon.player.SoloManager;
    import su.nightexpress.dungeons.dungeon.reward.FinishChestRewardManager;
    import su.nightexpress.dungeons.dungeon.spot.Spot;
    import su.nightexpress.dungeons.dungeon.spot.SpotState;
    import su.nightexpress.dungeons.dungeon.stage.Stage;
    import su.nightexpress.dungeons.gui.PartyFinderGUI;
    import su.nightexpress.dungeons.kit.impl.Kit;
    import su.nightexpress.dungeons.selection.SelectionType;
    import su.nightexpress.dungeons.util.CooldownManager;
    import su.nightexpress.nightcore.commands.Arguments;
    import su.nightexpress.nightcore.commands.Commands;
    import su.nightexpress.nightcore.commands.builder.HubNodeBuilder;
    import su.nightexpress.nightcore.commands.context.CommandContext;
    import su.nightexpress.nightcore.commands.context.ParsedArguments;
    import su.nightexpress.nightcore.core.config.CoreLang;

    import java.util.*;

    public class BaseCommands {
    
        public static void load(@NotNull DungeonPlugin plugin, @NotNull HubNodeBuilder root) {
            root.branch(Commands.literal("reload")
                .description(CoreLang.COMMAND_RELOAD_DESC)
                .permission(Perms.COMMAND_RELOAD)
                .executes((context, arguments) -> {
                    plugin.doReload(context.getSender());
                    plugin.getGUIConfigManager().reload();
                    plugin.getOrbManager().reload();
                    FinishChestRewardManager.reload();
                    return true;
                })
            );
    
            root.branch(Commands.literal(Placeholders.ALIAS_WAND)
                .playerOnly()
                .description(Lang.COMMAND_WAND_DESC)
                .permission(Perms.COMMAND_WAND)
                .withArguments(CommandArguments.forSelectionType(plugin))
                .executes((context, arguments) -> getWand(plugin, context, arguments))
            );
    
            root.branch(Commands.literal("browse")
                .description(Lang.COMMAND_BROWSE_DESC)
                .permission(Perms.COMMAND_BROWSE)
                .withArguments(Arguments.player(CommandArguments.PLAYER).permission(Perms.COMMAND_BROWSE_OTHERS).optional())
                .executes((context, arguments) -> browseDungeons(plugin, context, arguments))
            );
    
            root.branch(Commands.literal("createparty")
                    .playerOnly()
                    .description(Lang.COMMAND_CREATE_PARTY_DESC)
                    .permission(Perms.COMMAND_CREATE_PARTY)
                    .executes((context, arguments) -> createParty(plugin, context, arguments))
            );
    
    
            root.branch(Commands.literal(Placeholders.ALIAS_JOIN)
                .description(Lang.COMMAND_JOIN_DESC)
                .permission(Perms.COMMAND_JOIN)
                .withArguments(CommandArguments.forDungeon(plugin))
                .executes((context, arguments) -> joinDungeon(plugin, context, arguments))
            );
    
            root.branch(Commands.literal("send")
                .description(Lang.COMMAND_SEND_DESC)
                .permission(Perms.COMMAND_SEND)
                .withArguments(
                    Arguments.player(CommandArguments.PLAYER),
                    CommandArguments.forDungeon(plugin),
                    CommandArguments.forKit(plugin).optional()
                )
                .withFlags(CommandFlags.FORCE)
                .executes((context, arguments) -> sendToDungeon(plugin, context, arguments))
            );
    
            root.branch(Commands.literal("leave")
                .playerOnly()
                .description(Lang.COMMAND_LEAVE_DESC)
                .permission(Perms.COMMAND_LEAVE)
                .executes((context, arguments) -> leaveDungeon(plugin, context))
            );
    
            root.branch(Commands.literal("setstage")
                .playerOnly()
                .description(Lang.COMMAND_SET_STAGE_DESC)
                .permission(Perms.COMMAND_SET_STAGE)
                .withArguments(Arguments.string(CommandArguments.STAGE).localized(Lang.COMMAND_ARGUMENT_NAME_STAGE)
                    .suggestions((reader, context) -> {
                        DungeonInstance instance = CommandArguments.getDungeonInstance(plugin, context);
                        return instance == null ? Collections.emptyList() : new ArrayList<>(instance.getConfig().getStageByIdMap().keySet());
                    }))
                .executes((context, arguments) -> setStage(plugin, context, arguments))
            );


    
            root.branch(Commands.literal("setlevel")
                .playerOnly()
                .description(Lang.COMMAND_SET_LEVEL_DESC)
                .permission(Perms.COMMAND_SET_LEVEL)
                .withArguments(Arguments.string(CommandArguments.LEVEL).localized(Lang.COMMAND_ARGUMENT_NAME_LEVEL)
                    .suggestions((reader, context) -> {
                        DungeonInstance instance = CommandArguments.getDungeonInstance(plugin, context);
                        return instance == null ? Collections.emptyList() : new ArrayList<>(instance.getConfig().getLevelByIdMap().keySet());
                    }))
                .executes((context, arguments) -> setLevel(plugin, context, arguments))
            );
    
            root.branch(Commands.literal("setspot")
                .description(Lang.COMMAND_SET_SPOT_DESC)
                .permission(Perms.COMMAND_SET_SPOT)
                .withArguments(
                    CommandArguments.forDungeon(plugin),
                    Arguments.string(CommandArguments.SPOT).localized(Lang.COMMAND_ARGUMENT_NAME_SPOT)
                        .suggestions((reader, context) -> {
                            DungeonConfig config = CommandArguments.getDungeonConfig(plugin, context);
                            return config == null ? Collections.emptyList() : new ArrayList<>(config.getSpotByIdMap().keySet());
                        }),
                    Arguments.string(CommandArguments.STATE).localized(Lang.COMMAND_ARGUMENT_NAME_STATE)
                        .suggestions((reader, context) -> {
                            Spot spot = CommandArguments.getSpot(plugin, context);
                            return spot == null ? Collections.emptyList() : new ArrayList<>(spot.getStateByIdMap().keySet());
                        })
                )
                .executes(BaseCommands::setSpotState)
            );
    
            root.branch(Commands.literal("start")
                .description(Lang.COMMAND_START_DESC)
                .permission(Perms.COMMAND_START)
                .withArguments(CommandArguments.forDungeon(plugin).optional())
                .executes((context, arguments) -> startGame(plugin, context, arguments))
            );
    
            root.branch(Commands.literal("stop")
                .description(Lang.COMMAND_STOP_DESC)
                .permission(Perms.COMMAND_STOP)
                .withArguments(CommandArguments.forDungeon(plugin).optional())
                .executes((context, arguments) -> stopGame(plugin, context, arguments))
            );
    
    
            //root.branch(Commands.literal("solomode")
                    //.playerOnly()
                    //.description(Lang.COMMAND_SOLOMODE_DESC)
                    //.permission(Perms.COMMAND_SOLOMODE)
                    //.withArguments(CommandArguments.forDungeon(plugin).optional())
                    //.executes((context, arguments) -> triggerSoloMode(plugin, context, arguments))
            //);
    
            root.branch(Commands.literal("queue")
                    .playerOnly()
                    .permission("dungeons.command.queue")
                    .withArguments(CommandArguments.forDungeon(plugin))
                    .executes((context, arguments) -> joinQueue(plugin, context, arguments))
            );
    
            root.branch(Commands.literal("soloqueue")
                    .playerOnly()
                    .permission("dungeons.command.queue")
                    .withArguments(CommandArguments.forDungeon(plugin))
                    .executes((context, arguments) -> joinSoloQueue(plugin, context, arguments))
            );
    
            root.branch(Commands.literal("leavequeue")
                    .playerOnly()
                    .permission("dungeons.command.queue")
                    .executes((context, arguments) -> leaveQueue(plugin, context, arguments))
            );
    
    
            root.branch(Commands.literal("invite")
                    .playerOnly()
                    .permission("dungeons.command.party")
                    .withArguments(Arguments.player(CommandArguments.PLAYER))
                    .executes((context, arguments) -> invitePlayer(plugin, context, arguments))
            );
    
            root.branch(Commands.literal("kick")
                    .playerOnly()
                    .permission("dungeons.command.party")
                    .withArguments(Arguments.player(CommandArguments.PLAYER))
                    .executes((context, arguments) -> kickPlayer(plugin, context, arguments))
            );
    
    
            root.branch(Commands.literal("accept")
                    .playerOnly()
                    .permission("dungeons.command.party")
                    .withArguments(Arguments.player(CommandArguments.PLAYER))
                    .executes((context, arguments) -> acceptInvite(plugin, context, arguments))
            );
    
            root.branch(Commands.literal("decline")
                    .playerOnly()
                    .permission("dungeons.command.party")
                    .withArguments(Arguments.player(CommandArguments.PLAYER))
                    .executes((context, arguments) -> declineInvite(plugin, context, arguments))
            );
    
            root.branch(Commands.literal("partyleave")
                    .playerOnly()
                    .permission("dungeons.command.party")
                    .executes((context, arguments) -> leaveParty(plugin, context, arguments))
            );

    
            root.branch(Commands.literal("ready")
                    .playerOnly()
                    .permission("dungeons.command.party")
                    .executes((context, arguments) -> toggleReady(plugin, context, arguments))
            );
    
            root.branch(Commands.literal("partyinfo")
                    .playerOnly()
                    .permission("dungeons.command.party")
                    .executes((context, arguments) -> partyInfo(plugin, context, arguments))
            );
    
            root.branch(Commands.literal("createopenparty")
                    .playerOnly()
                    .description(Lang.COMMAND_CREATE_PARTY_DESC)
                    .permission(Perms.COMMAND_CREATE_PARTY)
                    .executes((context, arguments) -> createOpenParty(plugin, context, arguments))
            );
    
            root.branch(Commands.literal("joinparty")
                    .playerOnly()
                    .permission("dungeons.command.party")
                    .withArguments(Arguments.player(CommandArguments.PLAYER))
                    .executes((context, arguments) -> joinOpenParty(plugin, context, arguments))
            );
    
    
    
            root.branch(Commands.literal("partyfinder")
                    .playerOnly()
                    .permission("dungeons.command.open")
                    .withArguments(Arguments.player(CommandArguments.PLAYER).permission(Perms.COMMAND_BROWSE_OTHERS).optional())
                    .executes((context, arguments) -> openPartyFinder(plugin, context, arguments))
            );

            root.branch(Commands.literal("setclass")
                    .permission("dungeons.command.class.admin")
                    .withArguments(
                            Arguments.player("player").optional(),
                            Arguments.string("class")
                    )
                    .executes((context, args) -> setClassAdmin(plugin, context, args))
            );

            root.branch(Commands.literal("resetclass")
                    .permission("dungeons.command.class.admin")
                    .withArguments(
                            Arguments.player("player")
                    )
                    .executes((context, args) -> resetClassAdmin(plugin, context, args))
            );

            root.branch(Commands.literal("listclasses")
                    .permission("dungeons.command.class.admin")
                    .executes((context, args) -> listClasses(plugin, context, args))
            );


            root.branch(Commands.literal("giveorb")
                    .permission("dungeons.command.orb.admin")
                    .withArguments(
                            Arguments.player("player"),
                            Arguments.string("class")
                    )
                    .executes((context, args) -> giveOrb(plugin, context, args))
            );


            root.branch(Commands.literal("savereward")
                    .playerOnly()
                    .permission("dungeons.command.reward.admin")
                    .withArguments(
                            Arguments.string("rarity"),
                            Arguments.string("name"),
                            Arguments.integer("weight").optional()
                    )
                    .executes((context, arguments) -> saveReward(plugin, context, arguments))
            );
        }
    
        private static boolean getWand(@NotNull DungeonPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            Player player = context.getPlayerOrThrow();
            SelectionType type = arguments.get(CommandArguments.TYPE, SelectionType.class);
    
            plugin.getSelectionManager().startSelection(player, type);
            return true;
        }
    
        private static boolean setStage(@NotNull DungeonPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            Player player = context.getPlayerOrThrow();
            DungeonInstance instance = plugin.getDungeonManager().getInstance(player);
            if (instance == null) {
                context.send(Lang.DUNGEON_ERROR_MUST_BE_IN);
                return false;
            }
    
            String stageId = arguments.getString(CommandArguments.STAGE);
            Stage stage = instance.getConfig().getStageById(stageId);
            if (stage == null) {
                context.send(Lang.ERROR_COMMAND_INVALID_STAGE_ARGUMENT, replacer -> replacer.replace(Placeholders.GENERIC_VALUE, stageId));
                return false;
            }
    
            instance.setStage(stage);
            context.send(Lang.DUNGEON_ADMIN_SET_STAGE, replacer -> replacer.replace(instance.replacePlaceholders()).replace(stage.replacePlaceholders()));
            return true;
        }
    
        private static boolean setLevel(@NotNull DungeonPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            Player player = context.getPlayerOrThrow();
            DungeonInstance instance = plugin.getDungeonManager().getInstance(player);
            if (instance == null) {
                context.send(Lang.DUNGEON_ERROR_MUST_BE_IN);
                return false;
            }
    
            String levelId = arguments.getString(CommandArguments.LEVEL);
            Level level = instance.getConfig().getLevelById(levelId);
            if (level == null) {
                context.send(Lang.ERROR_COMMAND_INVALID_LEVEL_ARGUMENT, replacer -> replacer.replace(Placeholders.GENERIC_VALUE, levelId));
                return false;
            }
    
            instance.setLevel(level);
            context.send(Lang.DUNGEON_ADMIN_SET_LEVEL, replacer -> replacer.replace(instance.replacePlaceholders()).replace(level.replacePlaceholders()));
            return true;
        }
    
        private static boolean setSpotState(@NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            DungeonConfig config = arguments.get(CommandArguments.DUNGEON, DungeonConfig.class);
    
            String spotId = arguments.getString(CommandArguments.SPOT);
            Spot spot = config.getSpotById(spotId);
            if (spot == null) {
                context.send(Lang.ERROR_COMMAND_INVALID_SPOT_ARGUMENT, replacer -> replacer.replace(Placeholders.GENERIC_VALUE, spotId));
                return false;
            }
    
            String stateId = arguments.getString(CommandArguments.STATE);
            SpotState state = spot.getState(stateId);
            if (state == null) {
                context.send(Lang.ERROR_COMMAND_INVALID_STATE_ARGUMENT, replacer -> replacer.replace(Placeholders.GENERIC_VALUE, stateId));
                return false;
            }
    
            DungeonInstance dungeon = config.getInstance();
    
            if (dungeon.getState() == GameState.INGAME) {
                dungeon.setSpotState(spot, state);
            }
            else if (dungeon.isActive()) {
                spot.build(dungeon.getWorld(), state);
            }
    
            context.send(Lang.DUNGEON_ADMIN_SET_SPOT, replacer -> replacer
                .replace(config.replacePlaceholders())
                .replace(spot.replacePlaceholders())
                .replace(state.replacePlaceholders())
            );
            return true;
        }
    
        private static boolean browseDungeons(@NotNull DungeonPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            if (!arguments.contains(CommandArguments.PLAYER) && !context.isPlayer()) {
                context.errorPlayerOnly();
                return false;
            }
    
            Player player = context.isPlayer() ? context.getPlayerOrThrow() : arguments.getPlayer(CommandArguments.PLAYER);
            plugin.getDungeonManager().browseDungeons(player);
            return true;
        }
    
        private static boolean joinDungeon(@NotNull DungeonPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            if (!arguments.contains(CommandArguments.PLAYER) && !context.isPlayer()) {
                context.errorPlayerOnly();
                return false;
            }
    
            Player player = context.isPlayer() ? context.getPlayerOrThrow() : arguments.getPlayer(CommandArguments.PLAYER);
            DungeonConfig config = arguments.get(CommandArguments.DUNGEON, DungeonConfig.class);
            plugin.getDungeonManager().prepareForInstance(player, config.getInstance());
            return true;
        }
    
    
        public static boolean createParty(@NotNull DungeonPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            Player player = context.getPlayerOrThrow(); // safe — .playerOnly() on the branch guards this
    
            PartyManager partyManager = plugin.getPartyManager();
    
            if (CooldownManager.isOnCooldown(player)) return false;
    
            if (plugin.getDungeonManager().isPlaying(player)) {
                player.sendMessage("§cYou are already in a dungeon.");
                return false;
            }
    
            if (partyManager.hasParty(player.getUniqueId())) {
                context.send(Lang.PARTY_ERROR_ALREADY_IN_PARTY);
                return false;
            }
    
    
            partyManager.createParty(player.getUniqueId());
            context.send(Lang.PARTY_CREATED);
    
            //partyManager.sendPartyInfo(player.getUniqueId());
    
            return true;
        }
    
        private static boolean sendToDungeon(@NotNull DungeonPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            Player player = arguments.getPlayer(CommandArguments.PLAYER);
            DungeonConfig config = arguments.get(CommandArguments.DUNGEON, DungeonConfig.class);
            Kit kit = arguments.contains(CommandArguments.KIT) ? arguments.get(CommandArguments.KIT, Kit.class) : null;
            boolean force = context.hasFlag(CommandFlags.FORCE);
            DungeonInstance instance = config.getInstance();
    
            boolean result = plugin.getDungeonManager().enterInstance(player, instance, kit, force);
            context.send((result ? Lang.DUNGEON_SEND_SENT : Lang.DUNGEON_SEND_FAIL), replacer -> replacer
                .replace(instance.replacePlaceholders())
                .replace(Placeholders.forPlayer(player))
            );
            return true;
        }
    
        private static boolean leaveDungeon(@NotNull DungeonPlugin plugin, @NotNull CommandContext context) {
            Player player = context.getPlayerOrThrow();
    
            plugin.getDungeonManager().leaveInstance(player);
            return true;
        }
    
        private static boolean startGame(@NotNull DungeonPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            DungeonInstance dungeon;
    
            if (arguments.contains(CommandArguments.DUNGEON)) {
                DungeonConfig config = arguments.get(CommandArguments.DUNGEON, DungeonConfig.class);
                dungeon = config.getInstance();
            }
            else {
                if (!context.isPlayer()) {
                    context.errorPlayerOnly();
                    return false;
                }
    
                Player player = context.getPlayerOrThrow();
                dungeon = plugin.getDungeonManager().getInstance(player);
                if (dungeon == null) {
                    context.send(Lang.DUNGEON_ERROR_MUST_BE_IN);
                    return false;
                }
            }
    
            if (!dungeon.isReadyToStart()) {
                context.send(Lang.DUNGEON_ERROR_NOT_READY_TO_GAME, replacer -> replacer.replace(dungeon.replacePlaceholders()));
                return false;
            }
    
            dungeon.setCountdown(0);
            context.send(Lang.DUNGEON_START_DONE, replacer -> replacer.replace(dungeon.replacePlaceholders()));
            return true;
        }
    
        private static boolean stopGame(@NotNull DungeonPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            DungeonInstance dungeon;
    
            if (arguments.contains(CommandArguments.DUNGEON)) {
                DungeonConfig config = arguments.get(CommandArguments.DUNGEON, DungeonConfig.class);
                dungeon = config.getInstance();
            }
            else {
                if (!context.isPlayer()) {
                    context.errorPlayerOnly();
                    return false;
                }
    
                Player player = context.getPlayerOrThrow();
                dungeon = plugin.getDungeonManager().getInstance(player);
                if (dungeon == null) {
                    context.send(Lang.DUNGEON_ERROR_MUST_BE_IN);
                    return false;
                }
            }
    
            if (dungeon.getState() != GameState.INGAME || dungeon.isAboutToEnd()) {
                context.send(Lang.DUNGEON_ERROR_NOT_IN_GAME, replacer -> replacer.replace(dungeon.replacePlaceholders()));
                return false;
            }
    
            dungeon.stop();
            context.send(Lang.DUNGEON_ADMIN_STOP, replacer -> replacer.replace(dungeon.replacePlaceholders()));
            return true;
        }
    
    
        private static boolean triggerSoloMode(@NotNull DungeonPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            Player player = context.getPlayerOrThrow();
    
            if (plugin.getDungeonManager().isPlaying(player.getUniqueId())) {
                player.sendMessage("You're ingame, you can't toggle solo mode");
                return false;
            }
    
    
            SoloManager soloManager = plugin.getSoloManager();
            soloManager.toggleSoloOption(player.getUniqueId());
    
            player.sendMessage(soloManager.isSolo(player.getUniqueId())
                    ? "SOLO MODE ON"
                    : "SOLO MODE OFF");
    
            return true;
        }
    
    
        private static boolean joinSoloQueue(@NotNull DungeonPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            Player player = context.getPlayerOrThrow();
    
            if (CooldownManager.isOnCooldown(player)) return false;
    
            if (plugin.getDungeonManager().isPlaying(player)) {
                player.sendMessage("§cYou are already in a dungeon.");
                return false;
            }
    
            PartyManager partyManager = plugin.getPartyManager();
            if (partyManager.hasParty(player.getUniqueId())) {
                player.sendMessage("§cYou are in a party");
                return false;
            }


            DungeonConfig config = arguments.get(CommandArguments.DUNGEON, DungeonConfig.class);
            DungeonInstance instance = getBestInstance(plugin, config);
    
            if (!instance.isActive()) {
                player.sendMessage("§cThat dungeon is not active.");
                return false;
            }
    
            if (instance.isInQueue(player)) {
                player.sendMessage("§cYou are already queued for this dungeon.");
                return false;
            }
    
            plugin.getSoloManager().makePlayerSoloOptionOn(player.getUniqueId());
            instance.addToQueue(player, null);
            player.sendMessage("§aSolo mode enabled! You joined the queue! Position: §f#" + instance.getQueuePosition(player));
            return true;
        }
    
        private static boolean leaveQueue(@NotNull DungeonPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            Player player = context.getPlayerOrThrow();
    
            boolean removed = plugin.getDungeonManager().removeFromQueue(player);
    
            if (!removed) {
                player.sendMessage("§cYou are not in any queue.");
                return false;
            }
    
            player.sendMessage("§aYou left the queue.");
            return true;
        }
    
        private static boolean joinQueue(@NotNull DungeonPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            Player player = context.getPlayerOrThrow();
            PartyManager partyManager = plugin.getPartyManager();
    
    
            if (CooldownManager.isOnCooldown(player)) return false;
    
            if (plugin.getDungeonManager().isPlaying(player)) {
                player.sendMessage("§cYou are already in a dungeon.");
                return false;
            }

            DungeonConfig config = arguments.get(CommandArguments.DUNGEON, DungeonConfig.class);
            DungeonInstance instance = getBestInstance(plugin, config);
    
            if (!instance.isActive()) {
                player.sendMessage("§cThat dungeon is not active.");
                return false;
            }
    
            if (partyManager.hasParty(player.getUniqueId())) {
                Party party = partyManager.getPartyOf(player.getUniqueId());
    
                if (!party.isLeader(player.getUniqueId())) {
                    player.sendMessage("§cOnly the party leader can queue.");
                    return false;
                }
    
                partyManager.removeOfflineMembers(player.getUniqueId());
                party = partyManager.getPartyOf(player.getUniqueId());
                if (party == null) {
                    player.sendMessage("§cYour party was disbanded (all members offline).");
                    return false;
                }

                //If only 1 member, treat as solo queue
                if (party.getAllMembers().size() == 1) {
                    partyManager.leaveParty(player.getUniqueId());
                    CooldownManager.clearCooldown(player); // bypass cooldown since this is automatic
                    return joinSoloQueue(plugin, context, arguments);
                }
    
                for (UUID memberId : party.getAllMembers()) {
                    if (plugin.getDungeonManager().isPlaying(memberId)) {
                        player.sendMessage("§cA party member is already in a dungeon.");
                        return false;
                    }
                }
    
                partyManager.setPendingQueue(player.getUniqueId(), config.getId());
                partyManager.resetReady(player.getUniqueId());




                // Party leader auto ready since he started it
                if (party.isLeader(player.getUniqueId())) {
                    partyManager.toggleReady(player.getUniqueId());
                }
                party.openReadyCheckGUI();
    
                player.sendMessage("§aReady request sent to your party!");
                return true;
            }
    
            if (instance.isInQueue(player)) {
                player.sendMessage("§cYou are already queued for this dungeon.");
                return false;
            }
    
            instance.addToQueue(player, null);
            player.sendMessage("§aYou joined the queue! Position: §f#" + instance.getQueuePosition(player));
            return true;
        }
    
        private static boolean kickPlayer(@NotNull DungeonPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            Player player = context.getPlayerOrThrow();
            PartyManager partyManager = plugin.getPartyManager();

            if (CooldownManager.isOnCooldown(player)) return false;


            if (!partyManager.hasParty(player.getUniqueId())) {
                player.sendMessage("§cYou are not in a party.");
                return false;
            }
    
            Party party = partyManager.getPartyOf(player.getUniqueId());
            if (party == null) return false;
    
            if (!party.isLeader(player.getUniqueId())) {
                player.sendMessage("§cOnly the party leader can kick members.");
                return false;
            }
    
            Player target = arguments.getPlayer(CommandArguments.PLAYER);
            if (target == null) {
                player.sendMessage("§cPlayer not found.");
                return false;
            }
    
            if (target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage("§cYou cannot kick yourself.");
                return false;
            }
    
            if (!party.isMember(target.getUniqueId())) {
                player.sendMessage("§cThat player is not in your party.");
                return false;
            }
    
            partyManager.kickMember(player.getUniqueId(), target.getUniqueId());
            player.sendMessage("§aYou kicked §f" + target.getName() + " §afrom the party.");
            return true;
        }
    
        private static boolean invitePlayer(@NotNull DungeonPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            Player player = context.getPlayerOrThrow();
            PartyManager partyManager = plugin.getPartyManager();

            if (CooldownManager.isOnCooldown(player)) return false;


            if (!partyManager.hasParty(player.getUniqueId())) {
                player.sendMessage("§cYou are not in a party.");
                return false;
            }
    
            Party party = partyManager.getPartyOf(player.getUniqueId());
            if (!party.isLeader(player.getUniqueId())) {
                player.sendMessage("§cOnly the party leader can invite players.");
                return false;
            }
    
            if (party.isMaxParty()) {
                player.sendMessage("§cYour party is full.");
                return false;
            }
    
            Player target = arguments.getPlayer(CommandArguments.PLAYER);
    
            if (target.getUniqueId().equals(player.getUniqueId())) {
                player.sendMessage("§cYou cannot invite yourself.");
                return false;
            }
    
            if (partyManager.hasParty(target.getUniqueId())) {
                player.sendMessage("§cThat player is already in a party.");
                return false;
            }
    
            if (plugin.getDungeonManager().isPlaying(target)) {
                player.sendMessage("§cThat player is currently in a dungeon.");
                return false;
            }
    
            if (party.hasPendingInvite(target.getUniqueId())) {
                player.sendMessage("§cThat player already has a pending invite.");
                return false;
            }
    
            partyManager.invitePlayer(player.getUniqueId(), target.getUniqueId());
            player.sendMessage("§aInvite sent to §f" + target.getName() + "§a.");
            return true;
        }
    
        private static boolean acceptInvite(@NotNull DungeonPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            Player player = context.getPlayerOrThrow();
            PartyManager partyManager = plugin.getPartyManager();


            if (CooldownManager.isOnCooldown(player)) return false;


            if (!partyManager.hasPendingInvite(player.getUniqueId())) {
                player.sendMessage("§cYou have no pending party invites.");
                return false;
            }
    
            if (partyManager.hasParty(player.getUniqueId())) {
                player.sendMessage("§cYou are already in a party.");
                return false;
            }
    
            Player leader = arguments.getPlayer(CommandArguments.PLAYER);
            partyManager.acceptInvite(player.getUniqueId(), leader.getUniqueId());
            return true;
        }
    
        private static boolean declineInvite(@NotNull DungeonPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            Player player = context.getPlayerOrThrow();
            PartyManager partyManager = plugin.getPartyManager();


            if (CooldownManager.isOnCooldown(player)) return false;


            if (!partyManager.hasPendingInvite(player.getUniqueId())) {
                player.sendMessage("§cYou have no pending party invites.");
                return false;
            }
    
            Player leader = arguments.getPlayer(CommandArguments.PLAYER);
            partyManager.declineInvite(player.getUniqueId(), leader.getUniqueId());
            return true;
        }
    
        private static boolean leaveParty(@NotNull DungeonPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            Player player = context.getPlayerOrThrow();
            PartyManager partyManager = plugin.getPartyManager();

            if (CooldownManager.isOnCooldown(player)) return false;


            if (!partyManager.hasParty(player.getUniqueId())) {
                player.sendMessage("§cYou are not in a party.");
                return false;
            }
    
            partyManager.leaveParty(player.getUniqueId());
            return true;
        }


        private static boolean toggleReady(@NotNull DungeonPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            Player player = context.getPlayerOrThrow();
            PartyManager partyManager = plugin.getPartyManager();

            if (CooldownManager.isOnCooldown(player)) return false;


            if (!partyManager.hasParty(player.getUniqueId())) {
                player.sendMessage("§cYou are not in a party.");
                return false;
            }
    
            partyManager.toggleReady(player.getUniqueId());
    
    
            Party party = partyManager.getPartyOf(player.getUniqueId());

            // Open gui for all members to ready up
            if (party != null) {
                party.openReadyCheckGUI();
            }


            if (partyManager.isPartyReady(party.getLeader())) {
                String dungeonId = partyManager.getPendingQueue(party.getLeader());
                if (dungeonId != null) {
                    // Dungeon decided by player leadesr
                    DungeonInstance instancePickedByPartyLeader = plugin.getDungeonManager().getInstanceById(dungeonId);
                    if (instancePickedByPartyLeader != null) {
                        // The best dungeon for them in terms of availability
                        DungeonInstance instance = getBestInstance(plugin, instancePickedByPartyLeader.getConfig());
                        partyManager.broadcastToParty(party, "§aAll members ready! Joining queue for §f" + dungeonId + "§a.");
                        instance.addPartyToQueue(party, null);
                        party.closeInventoryForAllMembers();
                        partyManager.clearPendingQueue(party.getLeader());
                        partyManager.resetReady(party.getLeader());
                    }
                }
            }
    
    
    
            return true;
        }
    
        private static boolean partyInfo(@NotNull DungeonPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            Player player = context.getPlayerOrThrow();
            PartyManager partyManager = plugin.getPartyManager();
    
            if (!partyManager.hasParty(player.getUniqueId())) {
                player.sendMessage("§cYou are not in a party.");
                return false;
            }
    
            partyManager.sendPartyInfo(player.getUniqueId());
            return true;
        }
    
        public static boolean createOpenParty(@NotNull DungeonPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            Player player = context.getPlayerOrThrow();
            PartyManager partyManager = plugin.getPartyManager();
    
            if (CooldownManager.isOnCooldown(player)) return false;
    
            if (plugin.getDungeonManager().isPlaying(player)) {
                player.sendMessage("§cYou are already in a dungeon.");
                return false;
            }
    
            if (partyManager.hasParty(player.getUniqueId())) {
                context.send(Lang.PARTY_ERROR_ALREADY_IN_PARTY);
                return false;
            }
    
            partyManager.createParty(player.getUniqueId());
    
            Party party = partyManager.getPartyOf(player.getUniqueId());
            party.setOpen(true);
    
            //partyManager.sendPartyInfo(player.getUniqueId());
            return true;
        }
    
        private static boolean joinOpenParty(@NotNull DungeonPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            Player player = context.getPlayerOrThrow();
            PartyManager partyManager = plugin.getPartyManager();
    
    
    
            if (plugin.getDungeonManager().isPlaying(player)) {
                player.sendMessage("§cYou are already in a dungeon.");
                return false;
            }
    
            if (partyManager.hasParty(player.getUniqueId())) {
                player.sendMessage("§cYou are already in a party.");
                return false;
            }
    
            Player leader = arguments.getPlayer(CommandArguments.PLAYER);
    
            if (!partyManager.hasParty(leader.getUniqueId())) {
                player.sendMessage("§cThat player does not have a party.");
                return false;
            }
    
            Party party = partyManager.getPartyOf(leader.getUniqueId());
    
            if (!party.isLeader(leader.getUniqueId())) {
                player.sendMessage("§cYou must specify the party leader's name.");
                return false;
            }

            if (plugin.getDungeonManager().isPlaying(leader)) {
                player.sendMessage("§cThat party leader is already in a dungeon.");
                return false;
            }
    
            if (party.isMaxParty()) {
                player.sendMessage("§cThat party is full.");
                return false;
            }
    
            if (!party.isOpen()) {
                player.sendMessage("§cThat party is not open.");
                return false;
            }
    
    
            // Avoid leader joining his own party
            if (party.getLeader() == player.getUniqueId()) {
                return false;
            }
    
            partyManager.addMember(leader.getUniqueId(), player.getUniqueId());
            player.sendMessage("§aYou joined §f" + leader.getName() + "§a's party!");
            partyManager.broadcastToParty(party, "§f" + player.getName() + " §ajoined the party.");
            //partyManager.sendPartyInfo(player.getUniqueId());
            return true;
        }
    
        private static boolean openPartyFinder(@NotNull DungeonPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            Player player = context.getPlayerOrThrow();
    
    
            PartyFinderGUI.open(player);
            return true;
        }


        private static boolean setClassAdmin(@NotNull DungeonPlugin plugin,
                                             @NotNull CommandContext context,
                                             @NotNull ParsedArguments args) {

            Player target = args.getPlayer("player");
            String className = args.getString("class");

            if (!plugin.getClassManager().setClass(target, className)) {
                context.getSender().sendMessage("§cInvalid class: §f" + className);
                return false;
            }

            context.getSender().sendMessage("§aSet §f" + target.getName() + "§a's class to §e" + className);
            target.sendMessage("§aYour class has been set to §e" + className);
            return true;
        }

        private static boolean resetClassAdmin(@NotNull DungeonPlugin plugin,
                                               @NotNull CommandContext context,
                                               @NotNull ParsedArguments args) {

            Player target = args.getPlayer("player");

            plugin.getClassManager().removeClass(target);

            target.sendMessage("§eYour class has been reset.");
            return true;
        }

        private static boolean listClasses(@NotNull DungeonPlugin plugin,
                                           @NotNull CommandContext context,
                                           @NotNull ParsedArguments args) {

            Player sender = context.getPlayerOrThrow();

            sender.sendMessage("§aAvailable classes:");

            for (String c : plugin.getClassManager().getValidClasses()) {
                sender.sendMessage("§7- §e" + c);
            }

            return true;
        }


        private static boolean isTrulyFree(@NotNull DungeonPlugin plugin, @NotNull DungeonInstance inst) {
            // Has a solo player inside
            if (inst.hasSoloPlayer()) return false;
            // Has a party player inside
            if (inst.hasPartyPlayer()) return false;
            // Next in queue is a solo entry
            if (inst.isQueueHeadSolo()) return false;
            // Next in queue is a party entry
            if (inst.isQueueHeadParty()) return false;
            return true;
        }

        private static DungeonInstance getBestInstance(@NotNull DungeonPlugin plugin, @NotNull DungeonConfig requestedConfig) {
            String dungeonId = requestedConfig.getId();
            List<String> similar = plugin.getSimilarDungeonManager().getSimilar(dungeonId);

            DungeonInstance requested = requestedConfig.getInstance();

            // If no similar dungeons, just return requested
            if (similar.isEmpty()) return requested;

            // Build full candidate list
            List<DungeonInstance> candidates = new ArrayList<>();
            candidates.add(requested);
            for (String similarId : similar) {
                DungeonInstance inst = plugin.getDungeonManager().getInstanceById(similarId);
                if (inst != null && inst.isActive()) candidates.add(inst);
            }

            // 1st priority: requested is truly free
            if (isTrulyFree(plugin, requested) && requested.getState() != GameState.INGAME) {
                return requested;
            }

            // 2nd priority: any similar that is truly free and not in raid
            for (String similarId : similar) {
                DungeonInstance inst = plugin.getDungeonManager().getInstanceById(similarId);
                if (inst != null && inst.isActive() && isTrulyFree(plugin, inst) && inst.getState() != GameState.INGAME) {
                    return inst;
                }
            }

            // 3rd priority: not in raid, shortest queue
            Optional<DungeonInstance> notInRaid = candidates.stream()
                    .filter(inst -> inst.getState() != GameState.INGAME)
                    .min(Comparator.comparingInt(DungeonInstance::getQueueLength));

            if (notInRaid.isPresent()) return notInRaid.get();

            // 4th priority: all in raid, shortest queue
            return candidates.stream()
                    .min(Comparator.comparingInt(DungeonInstance::getQueueLength))
                    .orElse(requested);
        }

        private static boolean giveOrb(@NotNull DungeonPlugin plugin,
                                       @NotNull CommandContext context,
                                       @NotNull ParsedArguments args) {

            Player target = args.getPlayer("player");
            String className = args.getString("class");

            if (!plugin.getClassManager().getValidClasses().contains(className.toLowerCase())) {
                context.getSender().sendMessage("§cInvalid class: §f" + className);
                return false;
            }

            plugin.getOrbManager().giveOrbs(target, className);
            context.getSender().sendMessage("§aGave §f" + className + " §aorbs to §f" + target.getName() + "§a.");
            target.sendMessage("§aYou received orbs for class §e" + className + "§a.");
            return true;
        }

        private static boolean saveReward(@NotNull DungeonPlugin plugin, @NotNull CommandContext context, @NotNull ParsedArguments arguments) {
            Player player = context.getPlayerOrThrow();

            String rarity = arguments.getString("rarity");
            String name   = arguments.getString("name");
            int weight    = arguments.contains("weight") ? arguments.getInt("weight") : 10;

            plugin.getRewardManager().saveReward(player, rarity, name, weight);
            return true;
        }
    
    
    }


