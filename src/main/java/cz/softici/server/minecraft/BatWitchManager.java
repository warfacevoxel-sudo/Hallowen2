package cz.softici.server.minecraft;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Bat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Witch;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * v3.0.0 - Bat Transformation Event
 * When players kill bats, there's a configurable chance a witch spawns at the death location
 */
public class BatWitchManager implements Listener {
    private final HalloweenPlugin plugin;
    private final Set<UUID> transformedWitches = new HashSet<>();

    public BatWitchManager(HalloweenPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBatDeath(EntityDeathEvent event) {
        // Check if event is enabled
        if (!plugin.getConfig().getBoolean("bat_transformation.enabled", true)) {
            return;
        }

        // Check if entity is a bat
        if (!(event.getEntity() instanceof Bat)) {
            return;
        }

        Bat bat = (Bat) event.getEntity();
        
        // Check if player killed it
        if (bat.getKiller() == null) {
            return;
        }

        // Check global Halloween enabled
        if (!plugin.getData().isGlobalEnabled()) {
            return;
        }

        // Check if player has Halloween disabled
        if (plugin.getData().isDisabled(bat.getKiller().getUniqueId())) {
            return;
        }

        // Get chance from config
        double chance = plugin.getConfig().getDouble("bat_transformation.chance", 0.33);
        
        // Roll the dice
        if (Math.random() > chance) {
            return;
        }

        spawnTransformedWitch(bat.getLocation(), bat.getKiller());
    }

    /** v3.4.0 - Spawn a transformed witch at a location for a player (shared by the bat death handler and /halloween trigger). */
    public void spawnTransformedWitch(Location spawnLoc, Player killer) {
        Witch witch = (Witch) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.WITCH);
        
        // Give witch a pumpkin helmet
        witch.getEquipment().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
        
        // Apply buffs from config
        double healthMultiplier = plugin.getConfig().getDouble("bat_transformation.health_multiplier", 1.5);
        double maxHealth = witch.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue();
        witch.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(maxHealth * healthMultiplier);
        witch.setHealth(maxHealth * healthMultiplier);
        
        // Make it glow (spooky!)
        witch.setGlowing(true);
        
        // Track this witch
        transformedWitches.add(witch.getUniqueId());
        
        // Send message to player
        String message = plugin.getConfig().getString("bat_transformation.message", 
            "§5§l✦ §dThe bat transforms into a witch! §5§l✦");
        // v3.3.0 - config messages get the same & colour / %player% handling as messages.yml
        killer.sendMessage(MessageManager.colorize(
            message.replace("%PLAYER%", killer.getName()).replace("%player%", killer.getName())));
        
        // Play spooky sound
        plugin.playSpookySound(killer);
    }

    @EventHandler
    public void onTransformedWitchDeath(EntityDeathEvent event) {
        // Check if this is one of our transformed witches
        if (!(event.getEntity() instanceof Witch)) {
            return;
        }

        Witch witch = (Witch) event.getEntity();
        if (!transformedWitches.contains(witch.getUniqueId())) {
            return;
        }

        // Remove from tracking
        transformedWitches.remove(witch.getUniqueId());

        // Clear default drops
        event.getDrops().clear();
        event.setDroppedExp(1); // Minimum valid value

        // Give custom rewards from config
        if (witch.getKiller() != null) {
            int pumpkinMin = plugin.getConfig().getInt("bat_transformation.rewards.pumpkins_min", 3);
            int pumpkinMax = plugin.getConfig().getInt("bat_transformation.rewards.pumpkins_max", 8);
            int pumpkinAmount = 0;
            
            // v3.0.3 - Safety check: if both min and max are 0, skip dropping pumpkins
            if (pumpkinMin > 0 || pumpkinMax > 0) {
                pumpkinAmount = pumpkinMin + plugin.getRandom().nextInt(pumpkinMax - pumpkinMin + 1);
                if (pumpkinAmount > 0) {
                    event.getDrops().add(new ItemStack(Material.CARVED_PUMPKIN, pumpkinAmount));
                }
            }
            
            // Optional: Add potion rewards (random useful effect)
            if (plugin.getConfig().getBoolean("bat_transformation.rewards.potion_drop", true)) {
                ItemStack potion = new ItemStack(Material.POTION);
                org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) potion.getItemMeta();
                
                // Random potion selection (1.21.1 compatible names)
                org.bukkit.potion.PotionType[] potionTypes = {
                    org.bukkit.potion.PotionType.STRENGTH,
                    org.bukkit.potion.PotionType.SWIFTNESS,
                    org.bukkit.potion.PotionType.REGENERATION,
                    org.bukkit.potion.PotionType.HEALING,
                    org.bukkit.potion.PotionType.FIRE_RESISTANCE
                };
                org.bukkit.potion.PotionType randomType = potionTypes[plugin.getRandom().nextInt(potionTypes.length)];
                
                meta.setBasePotionType(randomType);
                potion.setItemMeta(meta);
                
                event.getDrops().add(potion);
            }

            // Give XP reward
            int xpReward = plugin.getConfig().getInt("bat_transformation.xp_reward", 5);
            plugin.giveSmartXpReward(witch.getKiller(), xpReward, 1.0);
            plugin.getStats().increment(witch.getKiller(), "halloween_mob_kills"); // v3.4.0
            plugin.getCandy().dropFor("bat_witch", witch.getKiller(), event.getDrops());
            
            // Send reward message
            if (pumpkinAmount > 0) {
                String msg = plugin.getMessages().getRandomMessage("batWitchDefeatedWithPumpkins", "")
                    .replace("%PUMPKINS%", String.valueOf(pumpkinAmount));
                witch.getKiller().sendMessage(msg);
            } else {
                String msg = plugin.getMessages().getRandomMessage("batWitchDefeatedNoPumpkins", "");
                witch.getKiller().sendMessage(msg);
            }
        }
    }

    /**
     * Clean up all transformed witches (called on plugin disable)
     */
    public void despawnAllWitches() {
        for (UUID witchId : new HashSet<>(transformedWitches)) {
            for (LivingEntity entity : plugin.getServer().getWorlds().get(0).getLivingEntities()) {
                if (entity.getUniqueId().equals(witchId)) {
                    entity.remove();
                    break;
                }
            }
        }
        transformedWitches.clear();
    }
}
