package cz.softici.server.minecraft;

import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class RandomPumpkinMobManager implements Listener {
    private final HalloweenPlugin plugin;
    private final Set<UUID> pumpkinMobs = new HashSet<>();

    public RandomPumpkinMobManager(HalloweenPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent event) {
        // Safety checks
        if (event.getEntity() == null) return;
        if (!plugin.getConfig().getBoolean("random_pumpkin_mobs.enabled", true)) return;
        
        // Skip if not a natural spawn or plugin spawn
        try {
            if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL &&
                event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CHUNK_GEN &&
                event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.DEFAULT &&
                event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER) {
                return;
            }
        } catch (Exception e) {
            return; // If spawn reason check fails, skip
        }
        
        LivingEntity entity = event.getEntity();
        
        // Only apply to hostile mobs that can wear helmets
        if (!(entity instanceof Zombie || entity instanceof Skeleton || 
              entity instanceof Creeper || entity instanceof Drowned ||
              entity instanceof Husk || entity instanceof Stray ||
              entity instanceof ZombieVillager || entity instanceof PiglinBrute ||
              entity instanceof Piglin || entity instanceof Pillager ||
              entity instanceof Vindicator)) {
            return;
        }
        
        double chance = plugin.getConfig().getDouble("random_pumpkin_mobs.chance", 0.05);
        if (plugin.getRandom().nextDouble() < chance) {
            // Give pumpkin head
            try {
                if (entity.getEquipment() != null) {
                    entity.getEquipment().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
                    entity.getEquipment().setHelmetDropChance(0.0f); // Don't drop the helmet
                    
                    // Track this mob
                    pumpkinMobs.add(entity.getUniqueId());
                }
            } catch (Exception e) {
                // Silently fail if entity doesn't support equipment
                plugin.getLogger().warning("Failed to add pumpkin to " + entity.getType() + ": " + e.getMessage());
            }
        }
    }

    @EventHandler
    public void onPumpkinMobDeath(EntityDeathEvent event) {
        if (!pumpkinMobs.contains(event.getEntity().getUniqueId())) return;
        
        pumpkinMobs.remove(event.getEntity().getUniqueId());
        
        if (event.getEntity().getKiller() != null) {
            plugin.getStats().increment(event.getEntity().getKiller(), "halloween_mob_kills"); // v3.4.0
            plugin.getCandy().dropFor("random_pumpkin_mob", event.getEntity().getKiller(), event.getDrops());
        }
        
        // Drop pumpkins
        int min = plugin.getConfig().getInt("random_pumpkin_mobs.pumpkin_drops.min", 5);
        int max = plugin.getConfig().getInt("random_pumpkin_mobs.pumpkin_drops.max", 10);
        
        // v3.0.3 - Safety check: if both min and max are 0, skip dropping rewards
        if (min == 0 && max == 0) {
            return;
        }
        
        int amount = min + plugin.getRandom().nextInt(max - min + 1);
        if (amount > 0) {
            event.getDrops().add(new ItemStack(Material.CARVED_PUMPKIN, amount));
        }
    }
}
