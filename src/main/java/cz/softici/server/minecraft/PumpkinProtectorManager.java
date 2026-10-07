package cz.softici.server.minecraft;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class PumpkinProtectorManager implements Listener {
    private final HalloweenPlugin plugin;

    public PumpkinProtectorManager(HalloweenPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPumpkinPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("pumpkin_protector.enabled", true)) return;
        
        Block placed = event.getBlockPlaced();
        if (placed.getType() != Material.CARVED_PUMPKIN) return;

        // Check structure: Gold block, Gold block, Pumpkin (from bottom to top)
        Block middleBlock = placed.getRelative(BlockFace.DOWN);
        if (middleBlock.getType() != Material.GOLD_BLOCK) return;
        
        Block bottomBlock = middleBlock.getRelative(BlockFace.DOWN);
        if (bottomBlock.getType() != Material.GOLD_BLOCK) return;
        
        // Check for arms (gold blocks on sides of middle block)
        Block leftArm = middleBlock.getRelative(BlockFace.NORTH);
        Block rightArm = middleBlock.getRelative(BlockFace.SOUTH);
        
        boolean hasArms = false;
        if (leftArm.getType() == Material.GOLD_BLOCK && rightArm.getType() == Material.GOLD_BLOCK) {
            hasArms = true;
        } else {
            // Try other direction
            leftArm = middleBlock.getRelative(BlockFace.EAST);
            rightArm = middleBlock.getRelative(BlockFace.WEST);
            if (leftArm.getType() == Material.GOLD_BLOCK && rightArm.getType() == Material.GOLD_BLOCK) {
                hasArms = true;
            }
        }
        
        if (!hasArms) return;
        
        // Valid structure! Spawn Pumpkin Protector
        Player player = event.getPlayer();
        Location spawnLoc = bottomBlock.getLocation().add(0.5, 0, 0.5);
        
        Block finalLeftArm = leftArm;
        Block finalRightArm = rightArm;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Remove blocks
            placed.setType(Material.AIR);
            middleBlock.setType(Material.AIR);
            bottomBlock.setType(Material.AIR);
            finalLeftArm.setType(Material.AIR);
            finalRightArm.setType(Material.AIR);

            // Spawn protector
            spawnPumpkinProtector(spawnLoc, player);
        }, 2L);
    }

    private void spawnPumpkinProtector(Location loc, Player creator) {
        IronGolem golem = (IronGolem) loc.getWorld().spawnEntity(loc, EntityType.IRON_GOLEM);
        
        // Set custom name
        golem.setCustomName("§6§lPumpkin Protector");
        golem.setCustomNameVisible(true);
        
        // Set health
        double health = plugin.getConfig().getDouble("pumpkin_protector.health", 200.0);
        golem.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
        golem.setHealth(health);
        
        // Set damage boost
        double damageBoost = plugin.getConfig().getDouble("pumpkin_protector.damage_boost", 5.0);
        golem.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(
            golem.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue() + damageBoost
        );
        
        // Give pumpkin head
        if (golem.getEquipment() != null) {
            ItemStack glowingPumpkin = new ItemStack(Material.CARVED_PUMPKIN);
            golem.getEquipment().setHelmet(glowingPumpkin);
            golem.getEquipment().setHelmetDropChance(0.0f);
        }
        
        // Add glow effect if enabled
        if (plugin.getConfig().getBoolean("pumpkin_protector.glow_effect", true)) {
            golem.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false, false));
        }
        
        // Add regeneration
        golem.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0, false, false, true));
        
        // Make it player-created (won't attack creator)
        golem.setPlayerCreated(true);
        
        // Spawn effects
        loc.getWorld().playSound(loc, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.8f);
        loc.getWorld().playSound(loc, Sound.ENTITY_IRON_GOLEM_HURT, 1.0f, 0.7f);
        loc.getWorld().spawnParticle(Particle.FLAME, loc.add(0, 1, 0), 50, 0.5, 1.0, 0.5, 0.1);
        loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 30, 0.5, 1.0, 0.5, 0.1);
        
        // Orange/golden glow particles around the golem
        for (int i = 0; i < 360; i += 20) {
            double angle = Math.toRadians(i);
            double x = Math.cos(angle) * 1.5;
            double z = Math.sin(angle) * 1.5;
            loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(x, 1, z), 1, 0, 0, 0, 0);
        }
        
        // Message
        creator.sendMessage("§6§l✨ You've created a Pumpkin Protector! §eIt will defend you from hostile mobs!");
        
        // Broadcast if enabled
        if (plugin.getConfig().getBoolean("broadcast_events", false)) {
            plugin.broadcastAll("§6" + creator.getName() + " §ehas summoned a §6§lPumpkin Protector§e!");
        }
    }
}
