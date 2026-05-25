package su.nightexpress.dungeons.dungeon.reward;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class RewardManager {

    private final Plugin plugin;
    private final File rewardFile;
    private FileConfiguration rewardConfig;

    // rarity -> (name -> RewardEntry)
    private final Map<String, Map<String, RewardEntry>> rewardMap = new HashMap<>();

    // -----------------------------------------------------------------------
    // RewardEntry — holds item + weight
    // -----------------------------------------------------------------------
    public static class RewardEntry {
        private final ItemStack item;
        private final int weight;

        public RewardEntry(@NotNull ItemStack item, int weight) {
            this.item   = item;
            this.weight = Math.max(1, weight);
        }

        public @NotNull ItemStack getItem()  { return item; }
        public int                getWeight() { return weight; }
        public @NotNull ItemStack clone()    { return item.clone(); }
    }

    // -----------------------------------------------------------------------
    // Init
    // -----------------------------------------------------------------------
    public RewardManager(@NotNull Plugin plugin) {
        this.plugin = plugin;
        rewardFile  = new File(plugin.getDataFolder(), "reward.yml");

        if (!rewardFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                rewardFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create reward.yml: " + e.getMessage());
            }
        }

        rewardConfig = YamlConfiguration.loadConfiguration(rewardFile);
        loadRewards();
    }

    // -----------------------------------------------------------------------
    // Load
    // -----------------------------------------------------------------------
    private void loadRewards() {
        rewardMap.clear();

        for (String rarity : rewardConfig.getKeys(false)) {
            ConfigurationSection raritySection = rewardConfig.getConfigurationSection(rarity);
            if (raritySection == null) continue;

            for (String name : raritySection.getKeys(false)) {
                ConfigurationSection entry = raritySection.getConfigurationSection(name);
                if (entry == null) continue;

                String base64 = entry.getString("item");
                int    weight = entry.getInt("weight", 10);
                if (base64 == null) continue;

                ItemStack item = itemFromBase64(base64);
                if (item == null) continue;

                rewardMap
                        .computeIfAbsent(rarity.toLowerCase(), k -> new LinkedHashMap<>())
                        .put(name, new RewardEntry(item, weight));
            }
        }

        plugin.getLogger().info("[RewardManager] Loaded " + rewardMap.size() + " rarities.");
    }


    public void reload() {
        rewardConfig = YamlConfiguration.loadConfiguration(rewardFile);
        loadRewards();
        plugin.getLogger().info("[RewardManager] Reloaded " + rewardMap.size() + " rarities.");
    }

    // -----------------------------------------------------------------------
    // Save reward from player hand
    // -----------------------------------------------------------------------
    public void saveReward(@NotNull Player player, @NotNull String rarity, @NotNull String name) {
        saveReward(player, rarity, name, 10);
    }

    public void saveReward(@NotNull Player player, @NotNull String rarity, @NotNull String name, int weight) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType().isAir()) {
            player.sendMessage("§cYou must hold an item to save as a reward.");
            return;
        }

        String path = rarity.toLowerCase() + "." + name;

        if (rewardConfig.contains(path)) {
            player.sendMessage("§cA reward named §e" + name + "§c already exists under §e" + rarity + "§c.");
            return;
        }

        String base64 = itemToBase64(item);
        if (base64 == null) {
            player.sendMessage("§cFailed to serialize the item.");
            return;
        }

        rewardConfig.set(path + ".item",   base64);
        rewardConfig.set(path + ".weight", weight);

        try {
            rewardConfig.save(rewardFile);
        } catch (IOException e) {
            player.sendMessage("§cFailed to save reward.yml.");
            e.printStackTrace();
            return;
        }

        rewardMap
                .computeIfAbsent(rarity.toLowerCase(), k -> new LinkedHashMap<>())
                .put(name, new RewardEntry(item.clone(), weight));

        player.sendMessage("§aReward §e" + name + "§a saved under rarity §e" + rarity + "§a (weight: " + weight + ")!");
    }

    // -----------------------------------------------------------------------
    // Create by name
    // -----------------------------------------------------------------------
    @Nullable
    public ItemStack createReward(@NotNull String rarity, @NotNull String name) {
        Map<String, RewardEntry> rarityMap = rewardMap.get(rarity.toLowerCase());
        if (rarityMap == null) return null;

        RewardEntry entry = rarityMap.get(name);
        return entry != null ? entry.clone() : null;
    }

    // -----------------------------------------------------------------------
    // Random — single rarity (weighted)
    // -----------------------------------------------------------------------
    @Nullable
    public ItemStack getRandomReward(@NotNull String rarity) {
        Map<String, RewardEntry> rarityMap = rewardMap.get(rarity.toLowerCase());
        if (rarityMap == null || rarityMap.isEmpty()) return null;

        return weightedPick(new ArrayList<>(rarityMap.values()));
    }

    // -----------------------------------------------------------------------
    // Random — multiple rarities combined (weighted)
    // -----------------------------------------------------------------------
    @Nullable
    public ItemStack getRandomRewardFromRarities(@NotNull String... rarities) {
        List<RewardEntry> pool = new ArrayList<>();
        for (String rarity : rarities) {
            Map<String, RewardEntry> rarityMap = rewardMap.get(rarity.toLowerCase());
            if (rarityMap != null) pool.addAll(rarityMap.values());
        }

        if (pool.isEmpty()) return null;
        return weightedPick(pool);
    }

    // -----------------------------------------------------------------------
    // Roll reward list — Hypixel style
    //
    // COMMON    → N random items from common pool
    // RARE      → N random items from rare pool + some common
    // LEGENDARY → guaranteed items from ALL rarities:
    //               - at least 1 legendary
    //               - several rare
    //               - several common
    //             mimicking Hypixel dungeon S/S+ chest rewards
    // -----------------------------------------------------------------------
    @NotNull
    public List<ItemStack> rollRewards(@NotNull String dungeonId,
                                       @NotNull String rarity,
                                       @NotNull RewardChestConfig chestConfig) {

        RewardChestConfig.Difficulty difficulty = chestConfig.getDifficulty(dungeonId);
        double multiplier = getDifficultyMultiplier(difficulty);

        return switch (rarity.toUpperCase()) {
            case FinishChestRewardManager.RARITY_LEGENDARY -> rollLegendaryChest(dungeonId, chestConfig, multiplier);
            case FinishChestRewardManager.RARITY_RARE      -> rollRareChest(dungeonId, chestConfig, multiplier);
            default                                        -> rollCommonChest(dungeonId, chestConfig, multiplier);
        };
    }

    // COMMON chest — items from common pool only
    private List<ItemStack> rollCommonChest(@NotNull String dungeonId,
                                            @NotNull RewardChestConfig chestConfig,
                                            double multiplier) {
        int count = scaledCount(dungeonId, FinishChestRewardManager.RARITY_COMMON, chestConfig, multiplier);
        return pickMultiple(FinishChestRewardManager.RARITY_COMMON, count);
    }

    // RARE chest — rare items + bonus common items
    private List<ItemStack> rollRareChest(@NotNull String dungeonId,
                                          @NotNull RewardChestConfig chestConfig,
                                          double multiplier) {
        List<ItemStack> results = new ArrayList<>();

        int rareCount   = scaledCount(dungeonId, FinishChestRewardManager.RARITY_RARE,   chestConfig, multiplier);
        int commonBonus = scaledCount(dungeonId, FinishChestRewardManager.RARITY_COMMON, chestConfig, multiplier) / 2;

        results.addAll(pickMultiple(FinishChestRewardManager.RARITY_RARE,   rareCount));
        results.addAll(pickMultiple(FinishChestRewardManager.RARITY_COMMON, commonBonus));
        return results;
    }

    // LEGENDARY chest — Hypixel style: guaranteed spread across all rarities
    // At least 1 legendary, several rare, several common
    private List<ItemStack> rollLegendaryChest(@NotNull String dungeonId,
                                               @NotNull RewardChestConfig chestConfig,
                                               double multiplier) {
        List<ItemStack> results = new ArrayList<>();

        // Guaranteed: 1-2 legendary
        int legendaryCount = ThreadLocalRandom.current().nextInt(1, 3);
        results.addAll(pickMultiple(FinishChestRewardManager.RARITY_LEGENDARY, legendaryCount));

        // Several rare
        int rareCount = scaledCount(dungeonId, FinishChestRewardManager.RARITY_RARE, chestConfig, multiplier);
        results.addAll(pickMultiple(FinishChestRewardManager.RARITY_RARE, rareCount));

        // Common filler
        int commonCount = scaledCount(dungeonId, FinishChestRewardManager.RARITY_COMMON, chestConfig, multiplier);
        results.addAll(pickMultiple(FinishChestRewardManager.RARITY_COMMON, commonCount));

        // Shuffle so it doesn't go legendary → rare → common in order
        Collections.shuffle(results);
        return results;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    // Pick N weighted items from a rarity pool (with replacement)
    @NotNull
    private List<ItemStack> pickMultiple(@NotNull String rarity, int count) {
        List<ItemStack> results = new ArrayList<>();
        Map<String, RewardEntry> rarityMap = rewardMap.get(rarity.toLowerCase());
        if (rarityMap == null || rarityMap.isEmpty()) return results;

        List<RewardEntry> pool = new ArrayList<>(rarityMap.values());
        for (int i = 0; i < count; i++) {
            ItemStack picked = weightedPick(pool);
            if (picked != null) results.add(picked);
        }
        return results;
    }

    // Weighted random pick
    @Nullable
    private ItemStack weightedPick(@NotNull List<RewardEntry> pool) {
        int total = pool.stream().mapToInt(RewardEntry::getWeight).sum();
        if (total <= 0) return null;

        int roll = ThreadLocalRandom.current().nextInt(total);
        int cumulative = 0;

        for (RewardEntry entry : pool) {
            cumulative += entry.getWeight();
            if (roll < cumulative) return entry.clone();
        }

        return pool.get(pool.size() - 1).clone();
    }

    // Compute scaled reward count using min/max from config + difficulty multiplier
    private int scaledCount(@NotNull String dungeonId,
                            @NotNull String rarity,
                            @NotNull RewardChestConfig chestConfig,
                            double multiplier) {
        int min = chestConfig.getMinRewardCount(dungeonId, rarity);
        int max = chestConfig.getMaxRewardCount(dungeonId, rarity);

        int base = ThreadLocalRandom.current().nextInt(min, max + 1);
        return Math.max(1, (int) Math.round(base * multiplier));
    }

    private double getDifficultyMultiplier(@NotNull RewardChestConfig.Difficulty difficulty) {
        return switch (difficulty) {
            case EASY   -> 0.75;
            case HARD   -> 1.5;
            default     -> 1.0;
        };
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------
    @NotNull
    public Map<String, Map<String, RewardEntry>> getRewardMap() {
        return Collections.unmodifiableMap(rewardMap);
    }

    // -----------------------------------------------------------------------
    // Serialization
    // -----------------------------------------------------------------------
    @Nullable
    private String itemToBase64(@NotNull ItemStack item) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos)) {
            boos.writeObject(item);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Nullable
    private ItemStack itemFromBase64(@NotNull String base64) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
             BukkitObjectInputStream bois = new BukkitObjectInputStream(bais)) {
            return (ItemStack) bois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }
}