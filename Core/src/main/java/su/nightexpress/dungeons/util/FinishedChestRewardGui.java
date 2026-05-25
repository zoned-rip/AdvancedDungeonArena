package su.nightexpress.dungeons.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import su.nightexpress.dungeons.ComponentUtilities.StaticComponentManager;
import su.nightexpress.dungeons.Components.FinishedChest.BuyRewardButton;
import su.nightexpress.dungeons.Components.FinishedChest.CloseButton;
import su.nightexpress.dungeons.DungeonPlugin;
import su.nightexpress.dungeons.dungeon.reward.FinishChestRewardManager;
import su.nightexpress.dungeons.dungeon.reward.RewardChestConfig;
import su.nightexpress.dungeons.gui.Utils.GUIConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FinishedChestRewardGui {

    // -----------------------------------------------------------------------
    // Layout (54-slot chest)
    //
    //  Row 0 (0-8):   border
    //  Row 1 (9-17):  border | drop slots 10-16 | border
    //  Row 2 (18-26): border | drop slots 19-25 | border
    //  Row 3 (27-35): border | drop slots 28-34 | border
    //  Row 4 (36-44): border | drop slots 37-43 | border
    //  Row 5 (45-53): border | [BUY] 48 | [CLOSE] 50 | border
    // -----------------------------------------------------------------------

    private static final int SIZE = 54;

    private static final int[] BORDER_SLOTS = {
            0,  1,  2,  3,  4,  5,  6,  7,  8,
            9,                                17,
            18,                               26,
            27,                               35,
            36,                               44,
            45, 46, 47,       49,       51, 52, 53
    };

    private static final int[] DROP_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final int SLOT_BUY   = 48;
    private static final int SLOT_CLOSE = 50;

    // -----------------------------------------------------------------------
    // Hardcoded possible drops
    // -----------------------------------------------------------------------
    private static final PossibleDrop[] POSSIBLE_DROPS = {
            new PossibleDrop(Material.DIAMOND,           "§b§lDiamond",              "§7A rare gem.",                        85),
            new PossibleDrop(Material.EMERALD,           "§a§lEmerald",              "§7Currency of the villagers.",          70),
            new PossibleDrop(Material.GOLD_INGOT,        "§6§lGold Ingot",           "§7Shiny and valuable.",                 60),
            new PossibleDrop(Material.IRON_INGOT,        "§7§lIron Ingot",           "§7A sturdy metal.",                     55),
            new PossibleDrop(Material.NETHERITE_INGOT,   "§4§lNetherite Ingot",      "§7Forged in the depths of the Nether.", 20),
            new PossibleDrop(Material.GOLDEN_SWORD,      "§6§lGolden Sword",         "§7Glimmers with power.",                50),
            new PossibleDrop(Material.DIAMOND_SWORD,     "§b§lDiamond Sword",        "§7Sharp and deadly.",                   30),
            new PossibleDrop(Material.NETHERITE_SWORD,   "§4§lNetherite Sword",      "§7The pinnacle of blade-craft.",        10),
            new PossibleDrop(Material.DIAMOND_CHESTPLATE,"§b§lDiamond Chestplate",   "§7Solid protection.",                   25),
            new PossibleDrop(Material.DIAMOND_HELMET,    "§b§lDiamond Helmet",       "§7Guards your mind.",                   25),
            new PossibleDrop(Material.DIAMOND_LEGGINGS,  "§b§lDiamond Leggings",     "§7Swift and sturdy.",                   25),
            new PossibleDrop(Material.DIAMOND_BOOTS,     "§b§lDiamond Boots",        "§7Light on your feet.",                 25),
            new PossibleDrop(Material.BOW,               "§e§lEnchanted Bow",        "§7Precise at any range.",               45),
            new PossibleDrop(Material.CROSSBOW,          "§6§lCrossbow",             "§7Locks and loads quickly.",            40),
            new PossibleDrop(Material.ENDER_PEARL,       "§5§lEnder Pearl",          "§7Teleport at will.",                   35),
            new PossibleDrop(Material.GOLDEN_APPLE,      "§6§lGolden Apple",         "§7Restores and fortifies.",             50),
            new PossibleDrop(Material.ENCHANTED_GOLDEN_APPLE, "§c§lNotch Apple",     "§7§oLegendary healing.",               5),
            new PossibleDrop(Material.EXPERIENCE_BOTTLE, "§2§lXP Bottle",            "§7Grants experience.",                  65),
            new PossibleDrop(Material.TOTEM_OF_UNDYING,  "§e§lTotem of Undying",     "§7Cheats death once.",                  8),
            new PossibleDrop(Material.NETHER_STAR,       "§f§l✦ Nether Star",        "§7§oPower beyond measure.",             3),
            new PossibleDrop(Material.BLAZE_ROD,         "§6§lBlaze Rod",            "§7Burns with fiery potential.",         55),
            new PossibleDrop(Material.ECHO_SHARD,        "§3§lEcho Shard",           "§7Resonates with lost voices.",         15),
            new PossibleDrop(Material.ANCIENT_DEBRIS,    "§4§lAncient Debris",       "§7Survived the ages.",                  12),
            new PossibleDrop(Material.MUSIC_DISC_PIGSTEP,"§d§lMusic Disc",           "§7A groovy tune.",                      7),
            new PossibleDrop(Material.TRIDENT,           "§9§lTrident",              "§7Commands the seas.",                  18),
            new PossibleDrop(Material.ELYTRA,            "§f§lElytra",               "§7Take to the skies.",                  6),
            new PossibleDrop(Material.SHULKER_SHELL,     "§5§lShulker Shell",        "§7Hollow and durable.",                 22),
            new PossibleDrop(Material.WITHER_SKELETON_SKULL, "§8§lWither Skull",     "§7Dark trophy of a cursed warrior.",    4),
    };

    // -----------------------------------------------------------------------

    public static void open(Player player, String dungeonId, String rarity, Location location) {

        GUIConfigManager cfg = DungeonPlugin.instance.getGUIConfigManager();

        String diffKey = rarity.toLowerCase(); // COMMON→common, RARE→rare, LEGENDARY→legendary

        String rawTitle = cfg.getString("finished-chest.title." + diffKey);
        String title = (rawTitle != null) ? rawTitle.replace("&", "§") : "§aDungeon Reward Chest";

        Inventory gui = Bukkit.createInventory(null, SIZE, title);

        StaticComponentManager.createBorder(gui, BORDER_SLOTS);
        placeDrops(gui);
        placeBuyButton(gui, cfg, dungeonId, location, rarity);
        placeCloseButton(gui, cfg);

        player.openInventory(gui);
    }

    // -----------------------------------------------------------------------
    // Drop placement — pick a random subset and show rarity glow
    // -----------------------------------------------------------------------
    private static void placeDrops(Inventory gui) {

        // Shuffle a copy so the preview feels fresh each open
        List<PossibleDrop> shuffled = new ArrayList<>(List.of(POSSIBLE_DROPS));
        Random rng = new Random();
        java.util.Collections.shuffle(shuffled, rng);

        int limit = Math.min(shuffled.size(), DROP_SLOTS.length);

        for (int i = 0; i < limit; i++) {
            PossibleDrop drop = shuffled.get(i);
            int slot = DROP_SLOTS[i];

            ItemStack item = new ItemStack(drop.material());
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                meta.displayName(Component.text(drop.displayName()));

                List<Component> lore = new ArrayList<>();
                lore.add(Component.text(" "));
                lore.add(Component.text(drop.description()));
                lore.add(Component.text(" "));
                lore.add(Component.text("§7Drop Chance: " + formatChance(drop.weight())));
                lore.add(Component.text(" "));

                if (drop.weight() <= 10) {
                    lore.add(Component.text("§4§l★ LEGENDARY"));
                    meta.setEnchantmentGlintOverride(true);
                } else if (drop.weight() <= 25) {
                    lore.add(Component.text("§5§lEPIC"));
                    meta.setEnchantmentGlintOverride(true);
                } else if (drop.weight() <= 50) {
                    lore.add(Component.text("§9RARE"));
                } else {
                    lore.add(Component.text("§7COMMON"));
                }

                meta.lore(lore);
                item.setItemMeta(meta);
            }

            gui.setItem(slot, item);
        }
    }

    // -----------------------------------------------------------------------
    // Buy button (slot 48)
    // -----------------------------------------------------------------------
    private static void placeBuyButton(Inventory gui, GUIConfigManager cfg, String dungeonId, Location location, String rarity) {

        ItemStack item = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("§6§l✦ Purchase Reward"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(" "));
            lore.add(Component.text("§7Click to spend your dungeon tokens"));
            lore.add(Component.text("§7and claim your reward drops."));
            lore.add(Component.text(" "));
            lore.add(Component.text("§eCost: §650 Dungeon Tokens"));
            lore.add(Component.text(" "));
            lore.add(Component.text("§aClick to Purchase"));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(true);

            // Store dungeonId in PCD
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(DungeonPlugin.instance, "dungeon_id"),
                    PersistentDataType.STRING,
                    dungeonId
            );

            NamespacedKey dungeonKey = new NamespacedKey(DungeonPlugin.instance, "dungeon_id");
            NamespacedKey locXKey   = new NamespacedKey(DungeonPlugin.instance, "chest_x");
            NamespacedKey locYKey   = new NamespacedKey(DungeonPlugin.instance, "chest_y");
            NamespacedKey locZKey   = new NamespacedKey(DungeonPlugin.instance, "chest_z");
            NamespacedKey locWKey   = new NamespacedKey(DungeonPlugin.instance, "chest_world");
            NamespacedKey rarityKey   = new NamespacedKey(DungeonPlugin.instance, "chest_rarity");


            meta.getPersistentDataContainer().set(dungeonKey, PersistentDataType.STRING, dungeonId);
            meta.getPersistentDataContainer().set(locXKey,    PersistentDataType.INTEGER, location.getBlockX());
            meta.getPersistentDataContainer().set(locYKey,    PersistentDataType.INTEGER, location.getBlockY());
            meta.getPersistentDataContainer().set(locZKey,    PersistentDataType.INTEGER, location.getBlockZ());
            meta.getPersistentDataContainer().set(locWKey,    PersistentDataType.STRING,  location.getWorld().getName());
            meta.getPersistentDataContainer().set(rarityKey,   PersistentDataType.STRING,  rarity);


            item.setItemMeta(meta);
        }

        new BuyRewardButton(gui, SLOT_BUY, item, "chest_buy");
    }

    // -----------------------------------------------------------------------
    // Close button (slot 50)
    // -----------------------------------------------------------------------
    private static void placeCloseButton(Inventory gui, GUIConfigManager cfg) {

        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("§c§lClose"));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(" "));
            lore.add(Component.text("§7Close this menu."));
            meta.lore(lore);
            item.setItemMeta(meta);
        }

        new CloseButton(gui, SLOT_CLOSE, item, "chest_close");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Converts a raw weight (out of ~85 max) to a rough percentage string.
     */
    private static String formatChance(int weight) {
        // Total weight pool
        int total = 0;
        for (PossibleDrop d : POSSIBLE_DROPS) total += d.weight();
        double pct = (weight / (double) total) * 100.0;
        return String.format("%.1f%%", pct);
    }

    // -----------------------------------------------------------------------
    // Inner record — one possible drop entry
    // -----------------------------------------------------------------------
    private record PossibleDrop(
            Material material,
            String displayName,
            String description,
            int weight          // higher = more common
    ) {}
}