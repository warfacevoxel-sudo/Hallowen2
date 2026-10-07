package cz.softici.server.minecraft.tasks;

import cz.softici.server.minecraft.HalloweenPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PumpkinTask {
    private final HalloweenPlugin plugin;
    private final Player player;
    private int taskId = -1;

    public PumpkinTask(HalloweenPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void scheduleNext() {
        // v3.0.0 - Check if event is enabled
        if (!plugin.getConfig().getBoolean("events.pumpkin.enabled", true)) {
            return;
        }
        
        // v3.0.0 - Read intervals from config.yml (events section)
        int min = plugin.getConfig().getInt("events.pumpkin.min", 5);
        int max = plugin.getConfig().getInt("events.pumpkin.max", 15);
        
        // Safety check: if interval is 0 0, the event is disabled
        if (min == 0 && max == 0) {
            return;
        }
        
        long delay = plugin.eventDelayTicks(player, min, max); // v3.4.0 - shared (witching hour multiplier)

        taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getData().isGlobalEnabled() && plugin.isEligible(player)) {
                plugin.getEffects().pumpkin(player); // v3.4.0
            }
            scheduleNext();
        }, delay).getTaskId();
    }

    public void cancel() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

}
