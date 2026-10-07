package cz.softici.server.minecraft.hooks;

import cz.softici.server.minecraft.HalloweenPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;

/**
 * v3.4.0 - Optional integrations:
 *
 * Vault economy
 * PlaceholderAPI
 * WorldGuard
 * Nexo
 *
 * Every integration is optional.
 */
public class HookManager {

    private final HalloweenPlugin plugin;

    private VaultEconomyHook economy;
    private WorldGuardHook worldGuard;
    private NexoHook nexo;

    private boolean placeholderApi;

    public HookManager(HalloweenPlugin plugin) {
        this.plugin = plugin;
    }

    /** Must run in onLoad(): WorldGuard only accepts flag registrations before WorldGuard enables. */
    public void registerEarly() {

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {

            try {

                worldGuard = new WorldGuardHook(plugin);

                worldGuard.registerFlag();

            } catch (Throwable t) {

                worldGuard = null;

                plugin.getLogger().warning(
                    "WorldGuard found but the halloween-events flag could not be registered: " +
                    t
                );
            }
        }
    }

    /** Runs in onEnable() after all soft-depends are enabled. */
    public void enable() {

        /*
         * Vault
         */
        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {

            try {

                VaultEconomyHook hook =
                    new VaultEconomyHook();

                if (hook.setup()) {

                    economy = hook;

                    plugin.getLogger().info(
                        "✓ Vault economy hooked (" +
                        hook.getName() +
                        ") - 'money:' rewards enabled"
                    );

                } else {

                    plugin.getLogger().warning(
                        "Vault is installed but no economy plugin registered - " +
                        "'money:' rewards will be skipped"
                    );
                }

            } catch (Throwable t) {

                plugin.getLogger().warning(
                    "Vault hook failed: " +
                    t
                );
            }
        }

        /*
         * PlaceholderAPI
         */
        placeholderApi =
            Bukkit.getPluginManager()
                .isPluginEnabled("PlaceholderAPI");

        if (placeholderApi) {

            try {

                if (new PlaceholderHook(plugin).register()) {

                    plugin.getLogger().info(
                        "✓ PlaceholderAPI expansion 'halloween' registered"
                    );
                }

            } catch (Throwable t) {

                placeholderApi = false;

                plugin.getLogger().warning(
                    "PlaceholderAPI hook failed: " +
                    t
                );
            }
        }

        /*
         * WorldGuard
         */
        if (
            worldGuard != null &&
            Bukkit.getPluginManager().isPluginEnabled("WorldGuard")
        ) {

            plugin.getLogger().info(
                "✓ WorldGuard hooked - " +
                "region flag 'halloween-events' available"
            );

        } else {

            worldGuard = null;
        }

        /*
         * Nexo
         *
         * Nexo is optional.
         *
         * The actual Nexo API class is isolated in NexoHook,
         * so Halloween can still load without Nexo installed.
         */
        if (Bukkit.getPluginManager().isPluginEnabled("Nexo")) {

            try {

                nexo = new NexoHook(plugin);

                plugin.getLogger().info(
                    "✓ Nexo hooked - 'nexo:' rewards enabled"
                );

            } catch (Throwable t) {

                nexo = null;

                plugin.getLogger().warning(
                    "Nexo hook failed: " +
                    t
                );
            }
        }
    }

    public boolean hasEconomy() {
        return economy != null;
    }

    /** Deposit money; returns false when no economy is available or the transaction failed. */
    public boolean deposit(
        OfflinePlayer player,
        double amount
    ) {
        return economy != null &&
               economy.deposit(
                   player,
                   amount
               );
    }

    public String formatMoney(double amount) {
        return economy != null
            ? economy.format(amount)
            : String.valueOf(amount);
    }

    public boolean hasPlaceholderApi() {
        return placeholderApi;
    }

    public boolean hasWorldGuard() {
        return worldGuard != null;
    }

    public boolean hasNexo() {
        return nexo != null;
    }

    public NexoHook getNexo() {
        return nexo;
    }

    /** True unless a WorldGuard region at this location sets halloween-events to deny. */
    public boolean isLocationAllowed(Location loc) {

        if (
            worldGuard == null ||
            loc == null
        ) {
            return true;
        }

        if (
            !plugin.getConfig()
                .getBoolean(
                    "worldguard.enabled",
                    true
                )
        ) {
            return true;
        }

        try {

            return worldGuard.allows(loc);

        } catch (Throwable t) {

            return true;
        }
    }
}