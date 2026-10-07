package cz.softici.server.minecraft.hooks;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import cz.softici.server.minecraft.HalloweenPlugin;
import org.bukkit.Location;

/**
 * v3.4.0 - WorldGuard region flag "halloween-events" (allow by default).
 * Set it to deny in a region (e.g. a market or spawn) and no scare events, bosses, graves or traders happen there.
 * Only loaded when WorldGuard is present (see HookManager).
 */
class WorldGuardHook {
    public static final String FLAG_NAME = "halloween-events";
    private final HalloweenPlugin plugin;
    private StateFlag flag;

    WorldGuardHook(HalloweenPlugin plugin) {
        this.plugin = plugin;
    }

    void registerFlag() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        Flag<?> existing = registry.get(FLAG_NAME);
        if (existing instanceof StateFlag) {
            flag = (StateFlag) existing; // already registered (plugin reload)
            return;
        }
        StateFlag created = new StateFlag(FLAG_NAME, true);
        registry.register(created);
        flag = created;
        plugin.getLogger().info("✓ Registered WorldGuard flag '" + FLAG_NAME + "'");
    }

    boolean allows(Location loc) {
        if (flag == null) return true;
        return WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
            .testState(BukkitAdapter.adapt(loc), null, flag);
    }
}
