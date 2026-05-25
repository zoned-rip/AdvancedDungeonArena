package su.nightexpress.dungeons.dungeon.reward;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.math.BigDecimal;

public class ChestPurchaseHandler {

    private static Essentials getEssentials() {
        return (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");
    }

    public static double getCost(String rarity) {
        RewardChestConfig config = FinishChestRewardManager.getConfig();
        if (config == null) return 500.0; // fallback

        double cost = config.getChestCost(rarity);
        return cost > 0 ? cost : 500.0; // fallback if rarity not found
    }

    public static PurchaseResult tryPurchase(Player player, String rarity) {
        Essentials ess = getEssentials();
        if (ess == null) {
            return PurchaseResult.failure("§cEconomy service is unavailable. Please contact an admin.");
        }

        User user = ess.getUser(player);
        if (user == null) {
            return PurchaseResult.failure("§cCould not load your economy profile. Please contact an admin.");
        }

        BigDecimal cost    = BigDecimal.valueOf(getCost(rarity));
        BigDecimal balance = user.getMoney();

        if (balance.compareTo(cost) < 0) {
            return PurchaseResult.failure("§cYou need §e$" + cost.toPlainString() + " §cto open this chest!");
        }

        try {
            user.takeMoney(cost, null);
            player.sendMessage("§7[Debug] Deducted §e$" + cost.toPlainString()
                    + " §7| New balance: §e$" + user.getMoney().toPlainString());
        } catch (Exception ex) {
            return PurchaseResult.failure("§cPayment failed: " + ex.getMessage());
        }

        return PurchaseResult.success(cost.doubleValue(), "$" + cost.toPlainString());
    }

    public static boolean canAfford(Player player, String rarity) {
        Essentials ess = getEssentials();
        if (ess == null) return false;

        User user = ess.getUser(player);
        if (user == null) return false;

        BigDecimal balance = user.getMoney();
        BigDecimal cost    = BigDecimal.valueOf(getCost(rarity));

        player.sendMessage("§7[Debug] Checking balance for §e" + player.getName());
        player.sendMessage("§7[Debug] Cost: §e$" + cost.toPlainString() + " §7| Balance: §e$" + balance.toPlainString());

        return balance.compareTo(cost) >= 0;
    }

    public static double getBalance(Player player) {
        Essentials ess = getEssentials();
        if (ess == null) return 0;

        User user = ess.getUser(player);
        if (user == null) return 0;

        return user.getMoney().doubleValue();
    }

    // -------------------------------------------------------------------------

    public static class PurchaseResult {

        private final boolean success;
        private final String  errorMessage;
        private final double  amountCharged;
        private final String  formattedCost;

        private PurchaseResult(boolean success, String errorMessage,
                               double amountCharged, String formattedCost) {
            this.success       = success;
            this.errorMessage  = errorMessage;
            this.amountCharged = amountCharged;
            this.formattedCost = formattedCost;
        }

        public static PurchaseResult success(double amount, String formatted) {
            return new PurchaseResult(true, null, amount, formatted);
        }

        public static PurchaseResult failure(String reason) {
            return new PurchaseResult(false, reason, 0, null);
        }

        public boolean isSuccess()        { return success; }
        public String  getErrorMessage()  { return errorMessage; }
        public double  getAmountCharged() { return amountCharged; }
        public String  getFormattedCost() { return formattedCost; }
    }
}