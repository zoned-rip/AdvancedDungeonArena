package su.nightexpress.dungeons.dungeon.reward;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.dungeons.DungeonsAPI;
import su.nightexpress.dungeons.dungeon.game.DungeonInstance;
import su.nightexpress.dungeons.dungeon.reward.RewardChestConfig.Difficulty;
import su.nightexpress.dungeons.util.FinishedChestRewardGui;
import su.nightexpress.nightcore.util.geodata.pos.BlockPos;

import java.util.*;

public class FinishChestRewardManager {

    public static final String RARITY_COMMON    = "COMMON";
    public static final String RARITY_RARE      = "RARE";
    public static final String RARITY_LEGENDARY = "LEGENDARY";

    private static final Map<String, TextColor> RARITY_COLORS = Map.of(
            RARITY_COMMON,    TextColor.color(0xAAAAAA),
            RARITY_RARE,      TextColor.color(0x5555FF),
            RARITY_LEGENDARY, TextColor.color(0xFFAA00)
    );

    private static final Random RNG           = new Random();
    private static final int    CHEST_COUNT   = 3;
    private static final int    CHEST_SPACING = 2;

    private static Plugin            plugin;
    private static NamespacedKey     chestKey;
    private static RewardChestConfig config;

    private static final Map<String, List<Entity>>   activeDisplays = new HashMap<>();
    private static final Map<String, List<Location>> activeChests   = new HashMap<>();
    public static Map<UUID, Inventory> playersSpecificRewardInventory = new HashMap<>();


    // -----------------------------------------------------------------------
    // Init / Teardown
    // -----------------------------------------------------------------------
    public static void init(@NotNull Plugin plugin) {
        FinishChestRewardManager.plugin = plugin;
        chestKey = new NamespacedKey(plugin, "finish_chest_rarity");
        config   = new RewardChestConfig(plugin);
    }

    public static void shutdown() {
        cleanupAll();
        chestKey = null;
        config   = null;
        plugin   = null;
    }

    // -----------------------------------------------------------------------
    // Config access
    // -----------------------------------------------------------------------
    @NotNull
    public static RewardChestConfig getConfig() {
        return config;
    }

    public static void reload() {
        config.reload();
    }

    public static boolean setDifficulty(@NotNull String dungeonId, @NotNull String difficultyName) {
        return config.setDifficulty(dungeonId, difficultyName);
    }

    public static boolean setDifficulty(@NotNull DungeonInstance dungeon, @NotNull String difficultyName) {
        return setDifficulty(dungeon.getConfig().getId(), difficultyName);
    }

    public static void setDifficulty(@NotNull DungeonInstance dungeon, @NotNull Difficulty difficulty) {
        config.setDifficulty(dungeon.getConfig().getId(), difficulty);
    }

    // -----------------------------------------------------------------------
    // Spawn
    // -----------------------------------------------------------------------
    public static void spawnRewardChests(@NotNull DungeonInstance dungeon) {
        ensureChestKey();

        BlockPos pos = dungeon.getConfig().getFinishChestPos();
        if (pos == null) {
            log("Finish chest position is null for dungeon: " + dungeon.getConfig().getId());
            return;
        }

        String dungeonId = dungeon.getConfig().getId();

        if (hasActiveChests(dungeon)) {
            log("Reward chests already active for dungeon: " + dungeonId + ", skipping spawn.");
            return;
        }

        Difficulty difficulty = config.getDifficulty(dungeonId);
        World      world      = dungeon.getWorld();
        Location   center     = pos.toLocation(world);

        List<Entity>   displays = new ArrayList<>();
        List<Location> chests   = new ArrayList<>();

        for (int i = 0; i < CHEST_COUNT; i++) {
            int      xOffset  = (i - CHEST_COUNT / 2) * CHEST_SPACING;
            Location chestLoc = center.clone().add(xOffset, 0, 0);
            String   rarity   = rollRarity(dungeonId);

            chestLoc.getBlock().setType(Material.CHEST);
            Chest chestState = (Chest) chestLoc.getBlock().getState();
            chestState.getPersistentDataContainer().set(chestKey, PersistentDataType.STRING, rarity);
            chestState.update();
            chests.add(chestLoc);

            TextColor   color   = RARITY_COLORS.getOrDefault(rarity, TextColor.color(0xFFFFFF));
            Location    holoLoc = chestLoc.clone().add(0.5, 1.5, 0.5);
            TextDisplay display = (TextDisplay) world.spawnEntity(holoLoc, EntityType.TEXT_DISPLAY);

            display.text(Component.text(rarity).color(color).decoration(TextDecoration.BOLD, true));
            display.setBillboard(Display.Billboard.CENTER);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setShadowed(true);
            display.setPersistent(false);
            display.setViewRange(24f);
            displays.add(display);

            log("[" + dungeonId + "] Chest " + (i + 1) + " → " + rarity
                    + " (difficulty: " + difficulty.name() + ")");
        }

        activeDisplays.put(dungeonId, displays);
        activeChests.put(dungeonId, chests);

        log("Spawned " + CHEST_COUNT + " reward chests for dungeon: " + dungeonId);
    }

    // -----------------------------------------------------------------------
    // Reward
    // -----------------------------------------------------------------------
    public static void onRewardChestOpened(@NotNull Player player, @NotNull String rarity, @NotNull Location location, @NotNull String dungeonId) {
        FinishedChestRewardGui.open(player, dungeonId, rarity, location);
    }

    public static void rewardChestOpenedMessage(@NotNull Player player, @NotNull String rarity) {
        TextColor color = RARITY_COLORS.getOrDefault(rarity, TextColor.color(0xFFFFFF));
        player.sendMessage(Component.text("You opened a " + rarity + " chest!").color(color));
    }

    // -----------------------------------------------------------------------
    // Cleanup
    // -----------------------------------------------------------------------
    public static void cleanupRewardChests(@NotNull DungeonInstance dungeon) {
        String dungeonId = dungeon.getConfig().getId();

        List<Entity> displays = activeDisplays.remove(dungeonId);
        if (displays != null) displays.forEach(Entity::remove);

        List<Location> chests = activeChests.remove(dungeonId);
        if (chests != null) chests.forEach(loc -> loc.getBlock().setType(Material.AIR));

        log("Cleaned up reward chests for dungeon: " + dungeonId);
    }

    public static void cleanupAll() {
        activeDisplays.values().forEach(list -> list.forEach(Entity::remove));
        activeDisplays.clear();
        activeChests.values().forEach(list -> list.forEach(loc -> loc.getBlock().setType(Material.AIR)));
        activeChests.clear();
    }

    // -----------------------------------------------------------------------
    // Util
    // -----------------------------------------------------------------------
    public static boolean hasActiveChests(@NotNull DungeonInstance dungeon) {
        return activeDisplays.containsKey(dungeon.getConfig().getId());
    }

    public static NamespacedKey getChestKey() {
        return chestKey;
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------
    private static String rollRarity(@NotNull String dungeonId) {
        int total  = config.getTotalWeight(dungeonId);
        int roll   = RNG.nextInt(total);
        int common = config.getCommonWeight(dungeonId);
        int rare   = config.getRareWeight(dungeonId);

        if (roll < common)          return RARITY_COMMON;
        if (roll < common + rare)   return RARITY_RARE;
        return RARITY_LEGENDARY;
    }

    private static void ensureChestKey() {
        if (chestKey == null) {
            chestKey = new NamespacedKey(resolvePlugin(), "finish_chest_rarity");
        }
    }


    @NotNull
    private static Plugin resolvePlugin() {
        return plugin != null ? plugin : DungeonsAPI.getPlugin();
    }

    private static void log(@NotNull String msg) {
        resolvePlugin().getLogger().info(msg);
    }

    public static Map<String, List<Location>> getActiveChests() {
        return activeChests;
    }
}