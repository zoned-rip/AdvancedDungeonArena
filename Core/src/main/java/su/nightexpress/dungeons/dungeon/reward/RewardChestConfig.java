package su.nightexpress.dungeons.dungeon.reward;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class RewardChestConfig {

    public enum Difficulty {
        EASY, MEDIUM, HARD;

        @NotNull
        public static Difficulty fromString(@NotNull String value, @NotNull Difficulty fallback) {
            for (Difficulty d : values()) {
                if (d.name().equalsIgnoreCase(value.trim())) return d;
            }
            return fallback;
        }
    }

    private static final Difficulty DEFAULT_DIFFICULTY = Difficulty.MEDIUM;

    private static final int[][] FALLBACK_WEIGHTS = {
            { 70, 25,  5 },  // EASY
            { 40, 40, 20 },  // MEDIUM
            { 15, 40, 45 },  // HARD
    };

    private final Plugin plugin;
    private final File   file;

    private FileConfiguration config;

    /** difficulty ordinal → { common, rare, legendary } */
    private final int[][] weights = new int[3][3];

    /** dungeonId (lower-case) → difficulty */
    private final Map<String, Difficulty> dungeonDifficulties = new HashMap<>();

    public RewardChestConfig(@NotNull Plugin plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "reward-chests.yml");

        for (Difficulty d : Difficulty.values()) {
            weights[d.ordinal()] = FALLBACK_WEIGHTS[d.ordinal()].clone();
        }

        load();
    }

    // -----------------------------------------------------------------------
    // Load / Save
    // -----------------------------------------------------------------------
    public void load() {
        if (!file.exists()) {
            writeDefaults();
        }

        config = YamlConfiguration.loadConfiguration(file);

        // --- Weight table ---
        for (Difficulty d : Difficulty.values()) {
            int ord  = d.ordinal();
            String p = "weights." + d.name().toLowerCase() + ".";
            weights[ord][0] = config.getInt(p + "common",    FALLBACK_WEIGHTS[ord][0]);
            weights[ord][1] = config.getInt(p + "rare",      FALLBACK_WEIGHTS[ord][1]);
            weights[ord][2] = config.getInt(p + "legendary", FALLBACK_WEIGHTS[ord][2]);
        }

        // --- Dungeon → difficulty mapping ---
        dungeonDifficulties.clear();
        for (Difficulty d : Difficulty.values()) {
            List<String> ids = config.getStringList("difficulty." + d.name().toLowerCase());
            for (String id : ids) {
                dungeonDifficulties.put(id.toLowerCase(), d);
            }
        }
    }

    public void save() {
        FileConfiguration cfg = new YamlConfiguration();

        for (Difficulty d : Difficulty.values()) {
            int ord  = d.ordinal();
            String p = "weights." + d.name().toLowerCase() + ".";
            cfg.set(p + "common",    weights[ord][0]);
            cfg.set(p + "rare",      weights[ord][1]);
            cfg.set(p + "legendary", weights[ord][2]);
        }

        Map<Difficulty, List<String>> inverse = new HashMap<>();
        for (Difficulty d : Difficulty.values()) inverse.put(d, new ArrayList<>());
        dungeonDifficulties.forEach((id, diff) -> inverse.get(diff).add(id));

        for (Difficulty d : Difficulty.values()) {
            cfg.set("difficulty." + d.name().toLowerCase(), inverse.get(d));
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save reward-chests.yml", e);
        }
    }

    private void writeDefaults() {
        FileConfiguration cfg = new YamlConfiguration();

        for (Difficulty d : Difficulty.values()) {
            int ord  = d.ordinal();
            String p = "weights." + d.name().toLowerCase() + ".";
            cfg.set(p + "common",    FALLBACK_WEIGHTS[ord][0]);
            cfg.set(p + "rare",      FALLBACK_WEIGHTS[ord][1]);
            cfg.set(p + "legendary", FALLBACK_WEIGHTS[ord][2]);
        }

        cfg.set("reward-count.common.min",    2);
        cfg.set("reward-count.common.max",    4);
        cfg.set("reward-count.rare.min",      4);
        cfg.set("reward-count.rare.max",      7);
        cfg.set("reward-count.legendary.min", 6);
        cfg.set("reward-count.legendary.max", 9);

        cfg.set("difficulty.easy",   List.of("dungeon1", "dungeon2"));
        cfg.set("difficulty.medium", List.of("dungeon3", "dungeon4"));
        cfg.set("difficulty.hard",   List.of("dungeon5", "dungeon6"));

        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create default reward-chests.yml", e);
        }
    }

    // -----------------------------------------------------------------------
    // Reload
    // -----------------------------------------------------------------------
    public void reload() {
        for (Difficulty d : Difficulty.values()) {
            weights[d.ordinal()] = FALLBACK_WEIGHTS[d.ordinal()].clone();
        }
        dungeonDifficulties.clear();
        load();
        plugin.getLogger().info("Reloaded reward-chests.yml");
    }

    // -----------------------------------------------------------------------
    // Difficulty resolution
    // -----------------------------------------------------------------------
    @NotNull
    public Difficulty getDifficulty(@NotNull String dungeonId) {
        return dungeonDifficulties.getOrDefault(dungeonId.toLowerCase(), DEFAULT_DIFFICULTY);
    }

    public boolean setDifficulty(@NotNull String dungeonId, @NotNull String difficultyName) {
        for (Difficulty d : Difficulty.values()) {
            if (d.name().equalsIgnoreCase(difficultyName.trim())) {
                dungeonDifficulties.put(dungeonId.toLowerCase(), d);
                save();
                return true;
            }
        }
        return false;
    }

    public void setDifficulty(@NotNull String dungeonId, @NotNull Difficulty difficulty) {
        dungeonDifficulties.put(dungeonId.toLowerCase(), difficulty);
        save();
    }

    // -----------------------------------------------------------------------
    // Reward count
    // -----------------------------------------------------------------------
    public int getMinRewardCount(@NotNull String dungeonId, @NotNull String rarity) {
        String key = "reward-count." + rarity.toLowerCase() + ".min";
        return config.getInt(key, 2);
    }

    public int getMaxRewardCount(@NotNull String dungeonId, @NotNull String rarity) {
        String key = "reward-count." + rarity.toLowerCase() + ".max";
        return config.getInt(key, 4);
    }

    // -----------------------------------------------------------------------
    // Weight helpers
    // -----------------------------------------------------------------------
    public int getCommonWeight(@NotNull Difficulty d)    { return weights[d.ordinal()][0]; }
    public int getRareWeight(@NotNull Difficulty d)      { return weights[d.ordinal()][1]; }
    public int getLegendaryWeight(@NotNull Difficulty d) { return weights[d.ordinal()][2]; }
    public int getTotalWeight(@NotNull Difficulty d) {
        int ord = d.ordinal();
        return weights[ord][0] + weights[ord][1] + weights[ord][2];
    }

    public int getCommonWeight(@NotNull String dungeonId)    { return getCommonWeight(getDifficulty(dungeonId)); }
    public int getRareWeight(@NotNull String dungeonId)      { return getRareWeight(getDifficulty(dungeonId)); }
    public int getLegendaryWeight(@NotNull String dungeonId) { return getLegendaryWeight(getDifficulty(dungeonId)); }
    public int getTotalWeight(@NotNull String dungeonId)     { return getTotalWeight(getDifficulty(dungeonId)); }
}