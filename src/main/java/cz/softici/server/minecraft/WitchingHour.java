package cz.softici.server.minecraft;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * v3.4.0 - A daily window in which scare events fire more often (interval multiplier), announced to players,
 * with optional random ambient horror sounds.
 *
 * config.yml:
 *   witching_hour: { enabled, mode: real|minecraft, start: "HH:mm", end: "HH:mm",
 *                    minecraft_start_tick, minecraft_end_tick, interval_multiplier, announce, ambient_sounds,
 *                    reschedule_on_start }
 */
public class WitchingHour {
    private final HalloweenPlugin plugin;
    private boolean lastActive = false;

    public WitchingHour(HalloweenPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("witching_hour.enabled", false);
    }

    public double intervalMultiplier() {
        double m = plugin.getConfig().getDouble("witching_hour.interval_multiplier", 0.5);
        return m <= 0 ? 1.0 : m;
    }

    /** Active right now for the given world (world only matters in "minecraft" mode). */
    public boolean isActive(World world) {
        if (!isEnabled()) return false;
        String mode = plugin.getConfig().getString("witching_hour.mode", "real");
        if ("minecraft".equalsIgnoreCase(mode)) {
            if (world == null) world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (world == null) return false;
            long start = plugin.getConfig().getLong("witching_hour.minecraft_start_tick", 18000);
            long end = plugin.getConfig().getLong("witching_hour.minecraft_end_tick", 22000);
            long t = world.getTime();
            return start <= end ? (t >= start && t <= end) : (t >= start || t <= end);
        }
        LocalTime start = parseTime("witching_hour.start", "20:00");
        LocalTime end = parseTime("witching_hour.end", "22:00");
        LocalTime now = LocalTime.now();
        if (!start.isAfter(end)) return !now.isBefore(start) && now.isBefore(end);
        return !now.isBefore(start) || now.isBefore(end); // wraps midnight
    }

    private LocalTime parseTime(String key, String def) {
        String v = plugin.getConfig().getString(key, def);
        try {
            return LocalTime.parse(v.trim(), DateTimeFormatter.ofPattern("H:mm"));
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid time '" + v + "' for " + key + " (expected HH:mm) - using " + def);
            return LocalTime.parse(def);
        }
    }

    /** Called every minute. Announces start/end, reschedules tasks, plays ambient sounds. */
    public void tick() {
        if (!isEnabled()) { lastActive = false; return; }
        boolean active = isActive(null);
        if (active != lastActive) {
            lastActive = active;
            if (plugin.getData().isGlobalEnabled()) {
                if (plugin.getConfig().getBoolean("witching_hour.announce", true)) {
                    plugin.broadcastAll(plugin.getMessages().get(active ? "witchingHourStart" : "witchingHourEnd"));
                }
                if (plugin.getConfig().getBoolean("witching_hour.reschedule_on_start", true)) {
                    plugin.restartAllTasks(); // apply the new multiplier immediately
                }
            }
        }
        if (active && plugin.getData().isGlobalEnabled() && plugin.getConfig().getBoolean("witching_hour.ambient_sounds", true)) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (plugin.isEligible(p) && plugin.getRandom().nextDouble() < 0.5) {
                    plugin.playRandomHorrorSound(p);
                }
            }
        }
    }

    public boolean isCurrentlyActive() { return lastActive; }
}
