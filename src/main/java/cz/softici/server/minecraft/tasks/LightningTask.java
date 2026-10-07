package cz.softici.server.minecraft.tasks;

import cz.softici.server.minecraft.HalloweenPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class LightningTask {
    private final HalloweenPlugin plugin;
    private final Player player;
    private int taskId = -1;

    public LightningTask(HalloweenPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void scheduleNext() {
        // v3.0.0 - Check if event is enabled
        if (!plugin.getConfig().getBoolean("events.lightning.enabled", true)) {
            return;
        }
        
        // v3.0.0 - Read intervals from config.yml (events section)
        int min = plugin.getConfig().getInt("events.lightning.min", 10);
        int max = plugin.getConfig().getInt("events.lightning.max", 20);
        
        // Safety check: if interval is 0 0, the event is disabled
        if (min == 0 && max == 0) {
            return;
        }
        
        long delay = plugin.eventDelayTicks(player, min, max); // v3.4.0 - shared (witching hour multiplier)

        taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getData().isGlobalEnabled() && plugin.isEligible(player)) {
                plugin.getEffects().lightning(player); // v3.4.0 - effect body lives in EventEffects
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
