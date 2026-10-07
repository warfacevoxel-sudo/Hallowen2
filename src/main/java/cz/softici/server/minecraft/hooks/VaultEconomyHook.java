package cz.softici.server.minecraft.hooks;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/** v3.4.0 - Vault economy access. Only loaded when Vault is present (see HookManager). */
class VaultEconomyHook {
    private Economy economy;

    boolean setup() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    String getName() { return economy.getName(); }

    boolean deposit(OfflinePlayer player, double amount) {
        if (amount <= 0) return true;
        EconomyResponse r = economy.depositPlayer(player, amount);
        return r.transactionSuccess();
    }

    String format(double amount) { return economy.format(amount); }
}
