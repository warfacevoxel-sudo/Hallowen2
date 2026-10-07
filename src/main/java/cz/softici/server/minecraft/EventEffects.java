package cz.softici.server.minecraft;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * v3.4.0 - The actual scare effects, extracted from the per-player tasks so they can be fired from the
 * scheduled tasks AND from /halloween trigger. Each method performs the effect immediately for one player
 * (no eligibility checks - callers decide) and records the statistic.
 */
public class EventEffects {
    public static final String[] SCARE_EVENTS = {"pumpkin", "slenderman", "fire", "lightning", "darkness", "herobrine"};

    private final HalloweenPlugin plugin;

    public EventEffects(HalloweenPlugin plugin) {
        this.plugin = plugin;
    }

    /** Pumpkin curse - pumpkin helmet (message + surprise handled inside applyPumpkinIfPossible). */
    public void pumpkin(Player player) {
        plugin.applyPumpkinIfPossible(player, true);
        plugin.getStats().increment(player, "scares");
    }

    /** Enderman appears right in front of the player for half a second. */
    public void slenderman(Player player) {
        Location loc = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(1));
        Enderman enderman = player.getWorld().spawn(loc, Enderman.class);
        plugin.sendEventMessage(player, plugin.getMessages().getRandomMessage("slenderman", player.getName()));
        plugin.playSpookySound(player);
        Bukkit.getScheduler().runTaskLater(plugin, enderman::remove, 10L); // remove after 0.5 sec
        plugin.getStats().increment(player, "scares");
    }

    /** Harmless (or configurable damaging) burst of fire. */
    public void fire(Player player) {
        boolean damageEnabled = plugin.getConfig().getBoolean("events.fire.damage_enabled", false);
        plugin.ignitePlayerHarmlessly(player, 2, damageEnabled);
        plugin.sendEventMessage(player, plugin.getMessages().getRandomMessage("fire", player.getName()));
        plugin.playSpookySound(player);
        plugin.getStats().increment(player, "scares");
    }

    /** Lightning strike (effect only unless events.lightning.damage_enabled). */
    public void lightning(Player player) {
        Location loc = player.getLocation();
        boolean damageEnabled = plugin.getConfig().getBoolean("events.lightning.damage_enabled", false);
        if (damageEnabled) {
            player.getWorld().strikeLightning(loc);
        } else {
            player.getWorld().strikeLightningEffect(loc);
        }
        plugin.sendEventMessage(player, plugin.getMessages().getRandomMessage("lightning", player.getName()));
        plugin.playSpookySound(player);
        plugin.getStats().increment(player, "scares");
    }

    /** Short blindness with creaking trapdoor sounds. */
    public void darkness(Player player) {
        int durationSeconds = plugin.getConfig().getInt("events.darkness.duration_seconds", 2);
        int durationTicks = durationSeconds * 20;
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, durationTicks, 0, false, false, true));

        // Play multiple trapdoor sounds for creepy effect
        player.playSound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1.0f, 0.5f);
        delayedSound(player, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.9f, 0.6f, 3L);
        delayedSound(player, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.8f, 0.4f, 6L);
        delayedSound(player, Sound.BLOCK_IRON_TRAPDOOR_OPEN, 1.0f, 0.5f, 9L);
        // Play ambient cave sound for extra spookiness
        delayedSound(player, Sound.AMBIENT_CAVE, 0.4f, 0.5f, 5L);

        plugin.sendEventMessage(player, plugin.getMessages().getRandomMessage("darkness", player.getName()));
        plugin.getStats().increment(player, "scares");
    }

    private void delayedSound(Player player, Sound sound, float volume, float pitch, long delay) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }, delay);
    }
}
