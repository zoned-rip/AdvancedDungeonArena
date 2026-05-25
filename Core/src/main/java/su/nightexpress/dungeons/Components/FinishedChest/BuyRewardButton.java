package su.nightexpress.dungeons.Components.FinishedChest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import su.nightexpress.dungeons.ComponentUtilities.ComponentButton;
import su.nightexpress.dungeons.DungeonPlugin;
import su.nightexpress.dungeons.dungeon.DungeonManager;
import su.nightexpress.dungeons.dungeon.game.DungeonInstance;
import su.nightexpress.dungeons.dungeon.player.DungeonGamer;
import su.nightexpress.dungeons.dungeon.reward.*;

import java.util.List;

import static su.nightexpress.dungeons.dungeon.reward.ChestLockManager.lockPlayerChestInteraction;

public class BuyRewardButton extends ComponentButton {

    public BuyRewardButton(Inventory inv, int slot, ItemStack item, String baseKey) {
        super(inv, slot, item, baseKey);
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player player)) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        var pdc = meta.getPersistentDataContainer();

        String dungeonId = pdc.get(new NamespacedKey(DungeonPlugin.instance, "dungeon_id"), PersistentDataType.STRING);
        Integer x        = pdc.get(new NamespacedKey(DungeonPlugin.instance, "chest_x"),    PersistentDataType.INTEGER);
        Integer y        = pdc.get(new NamespacedKey(DungeonPlugin.instance, "chest_y"),    PersistentDataType.INTEGER);
        Integer z        = pdc.get(new NamespacedKey(DungeonPlugin.instance, "chest_z"),    PersistentDataType.INTEGER);
        String worldName = pdc.get(new NamespacedKey(DungeonPlugin.instance, "chest_world"), PersistentDataType.STRING);
        String rarity    = pdc.get(new NamespacedKey(DungeonPlugin.instance, "chest_rarity"), PersistentDataType.STRING);

        if (dungeonId == null || x == null || y == null || z == null || worldName == null || rarity == null) return;

        Location chestLocation = new Location(Bukkit.getWorld(worldName), x, y, z);

        DungeonManager dungeonManager = DungeonPlugin.instance.getDungeonManager();

        DungeonGamer playerData = dungeonManager.getDungeonPlayer(player.getUniqueId());
        if (playerData == null) return;

        DungeonInstance playerDungeonInstance = dungeonManager.getInstance(player);
        if (playerDungeonInstance == null) return;

        // Double-check at click time — another player may have bought it
        if (playerDungeonInstance.isChestBoughtByPlayers(chestLocation)) {
            player.closeInventory();
            player.sendMessage("§cSomeone else already purchased this chest!");
            return;
        }

        // Check affordability first, before touching anything
        if (!ChestPurchaseHandler.canAfford(player, rarity)) {
            player.closeInventory();
            player.sendMessage("§cYou cannot afford this chest! Cost: §e$" + ChestPurchaseHandler.getCost(rarity));
            player.sendMessage("§7Your balance: §e$" + ChestPurchaseHandler.getBalance(player));
            return;
        }

        // Record purchase
        playerDungeonInstance.addBoughtChestToInstance(player.getUniqueId(), chestLocation);

        // Charge
        ChestPurchaseHandler.PurchaseResult result = ChestPurchaseHandler.tryPurchase(player, rarity);
        if (!result.isSuccess()) {
            player.closeInventory();
            player.sendMessage(result.getErrorMessage());
            return;
        }

        lockPlayerChestInteraction(player, dungeonId);

        // Roll rewards
        RewardManager rewardManager     = DungeonPlugin.instance.getRewardManager();
        RewardChestConfig chestConfig   = FinishChestRewardManager.getConfig();
        List<ItemStack> rewards         = rewardManager.rollRewards(dungeonId, rarity, chestConfig);

        // Populate a fresh inventory
        Inventory rewardInventory = Bukkit.createInventory(null, 54, "§6§lYour Rewards");

        // Fill rewards starting from slot 0
        int slot = 0;
        for (ItemStack reward : rewards) {
            if (slot >= rewardInventory.getSize()) break;
            rewardInventory.setItem(slot++, reward);
        }

        // Save and open
        FinishChestRewardManager.playersSpecificRewardInventory.put(player.getUniqueId(), rewardInventory);
        player.openInventory(rewardInventory);
        FinishChestRewardManager.rewardChestOpenedMessage(player, rarity);
    }
}