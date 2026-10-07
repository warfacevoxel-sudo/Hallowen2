package cz.softici.server.minecraft;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;



public class PumpkinMonsterManager implements Listener {
    private final HalloweenPlugin plugin;
    private final Map<UUID, String> pumpkinMonsters = new HashMap<>(); // Zombie UUID -> Creator name
    private final Map<UUID, Integer> teleportTasks = new HashMap<>(); // Boss UUID -> Task ID for teleportation
    private final Map<String, Long> lastBlockClick = new HashMap<>(); // Block location -> Last click time (for debounce)

    public PumpkinMonsterManager(HalloweenPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * v3.0.5 - Start anti-stacking teleportation for a boss
     */
    private void startBossTeleportation(UUID bossId) {
        // Random interval between 5-15 seconds (100-300 ticks)
        int initialDelay = 100 + plugin.getRandom().nextInt(200); // 5-15 seconds
        
        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Find the boss entity
            Zombie boss = null;
            for (World world : Bukkit.getWorlds()) {
                for (Zombie zombie : world.getEntitiesByClass(Zombie.class)) {
                    if (zombie.getUniqueId().equals(bossId)) {
                        boss = zombie;
                        break;
                    }
                }
                if (boss != null) break;
            }
            
            if (boss == null) {
                // Boss no longer exists, cancel task
                stopBossTeleportation(bossId);
                return;
            }
            
            // Find closest player within 30 blocks
            Player closestPlayer = null;
            double closestDistance = 30.0;
            
            for (Player player : boss.getWorld().getPlayers()) {
                double distance = boss.getLocation().distance(player.getLocation());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestPlayer = player;
                }
            }
            
            if (closestPlayer != null) {
                // Teleport boss to player's location (slightly offset to avoid being inside player)
                Location playerLoc = closestPlayer.getLocation();
                Location teleportLoc = playerLoc.clone().add(
                    (plugin.getRandom().nextDouble() - 0.5) * 4, // Random X offset ±2 blocks
                    0, 
                    (plugin.getRandom().nextDouble() - 0.5) * 4  // Random Z offset ±2 blocks
                );
                
                // Ensure teleport location is safe (not in blocks)
                teleportLoc = getSafeLocation(teleportLoc);
                boss.teleport(teleportLoc);
                
                // Visual/audio feedback
                boss.getWorld().spawnParticle(Particle.PORTAL, teleportLoc, 20);
                boss.getWorld().playSound(teleportLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
            }
            
        }, initialDelay, 100 + plugin.getRandom().nextInt(200)).getTaskId(); // Repeat every 5-15 seconds
        
        teleportTasks.put(bossId, taskId);
    }
    
    /**
     * v3.0.5 - Get safe teleport location (not inside blocks)
     */
    private Location getSafeLocation(Location loc) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        
        // Check current location and up to 5 blocks above
        for (int i = 0; i < 5; i++) {
            Location testLoc = new Location(world, x + 0.5, y + i, z + 0.5);
            if (world.getBlockAt(testLoc).getType().isAir() && 
                world.getBlockAt(testLoc.clone().add(0, 1, 0)).getType().isAir()) {
                return testLoc;
            }
        }
        
        // If no safe location found, return original with small Y offset
        return loc.clone().add(0, 1, 0);
    }
    
    /**
     * v3.0.5 - Stop teleportation task for a boss
     */
    private void stopBossTeleportation(UUID bossId) {
        Integer taskId = teleportTasks.remove(bossId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    @EventHandler
    public void onFlintAndSteelUse(PlayerInteractEvent event) {
        // Check if Halloween is globally enabled
        if (!plugin.getData().isGlobalEnabled()) return;
        if (!plugin.getConfig().getBoolean("pumpkin_zombie_boss.enabled", true)) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getItem() == null || event.getItem().getType() != Material.FLINT_AND_STEEL) return;
        
        Player player = event.getPlayer();
        
        // v3.1.0 - Check if player is in blacklisted world
        if (plugin.isWorldBlacklisted(player)) return;
        
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.CARVED_PUMPKIN) return;
        
        // Check if fence is below the pumpkin
        Block fenceBlock = clickedBlock.getRelative(BlockFace.DOWN);
        String typeName = fenceBlock.getType().name();
        if (!typeName.contains("FENCE")) return;
        
        // v3.4.0 - WorldGuard: halloween-events flag at the structure
        if (!plugin.isLocationAllowed(clickedBlock.getLocation())) return;
        
        // Check if player has Halloween enabled (after structure validation)
        if (plugin.getData().isDisabled(player.getUniqueId())) {
            String msg = plugin.getMessages().getRandomMessage("bossPlayerDisabled", player.getName(), plugin.getCommandPrefix());
            player.sendMessage(msg);
            event.setCancelled(true);
            return;
        }
        
        // Check cooldown
        int cooldownMinutes = plugin.getConfig().getInt("pumpkin_zombie_boss.cooldown_minutes", 10);
        if (!plugin.getData().canSpawnZombieBoss(player.getUniqueId(), cooldownMinutes)) {
            long remainingMs = plugin.getData().getRemainingZombieBossCooldown(player.getUniqueId(), cooldownMinutes);
            long remainingMinutes = remainingMs / (60 * 1000L);
            long remainingSeconds = (remainingMs % (60 * 1000L)) / 1000L;
            
            String msg = plugin.getMessages().getRandomMessage("bossCooldown", "")
                .replace("%MINUTES%", String.valueOf(remainingMinutes))
                .replace("%SECONDS%", String.valueOf(remainingSeconds))
                .replace("%BOSS_TYPE%", "Zombie");
            player.sendMessage(msg);
            event.setCancelled(true);
            return;
        }
        
        // v3.0.6 - Check for rapid-click exploit (debounce)
        String blockKey = fenceBlock.getLocation().toString();
        long now = System.currentTimeMillis();
        long lastClick = lastBlockClick.getOrDefault(blockKey, 0L);
        long timeSinceLastClick = now - lastClick;
        final int CLICK_DEBOUNCE_MS = 3000; // v3.0.6 - Always 3 second, not configurable
        
        if (timeSinceLastClick < CLICK_DEBOUNCE_MS) {
            String msg = plugin.getMessages().getRandomMessage("bossRapidClick", "");
            player.sendMessage(msg);
            event.setCancelled(true);
            return;
        }
        
        // Record this click
        lastBlockClick.put(blockKey, now);
        
        // Valid structure! Spawn Pumpkin Monster
        event.setCancelled(true); // Prevent fire from being placed
        Location spawnLoc = fenceBlock.getLocation().add(0.5, 0, 0.5);
        
        Block pumpkinBlock = clickedBlock;
        
        // Play fire ignite sound
        player.getWorld().playSound(pumpkinBlock.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, 1.0f, 1.0f);
        
        // Create fire particle effect
        pumpkinBlock.getWorld().spawnParticle(Particle.FLAME, pumpkinBlock.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0.02);
        
        // Damage flint and steel
        ItemStack item = event.getItem();
        if (item.getType() == Material.FLINT_AND_STEEL && item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable) {
            org.bukkit.inventory.meta.Damageable damageable = (org.bukkit.inventory.meta.Damageable) item.getItemMeta();
            damageable.setDamage(damageable.getDamage() + 1);
            
            // Check if item should break
            if (damageable.getDamage() >= Material.FLINT_AND_STEEL.getMaxDurability()) {
                player.getInventory().setItemInMainHand(null);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            } else {
                item.setItemMeta(damageable);
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Remove blocks with fire effect
            pumpkinBlock.getWorld().spawnParticle(Particle.LAVA, pumpkinBlock.getLocation().add(0.5, 0.5, 0.5), 10, 0.2, 0.2, 0.2, 0.1);
            pumpkinBlock.setType(Material.AIR);
            fenceBlock.setType(Material.AIR);

            // Spawn boss
            spawnPumpkinMonster(spawnLoc, player);
        }, 10L); // Half second delay for effect
    }

    private void spawnPumpkinMonster(Location loc, Player creator) {
        spawnPumpkinMonster(loc, creator, true);
    }

    /** v3.4.0 - Spawn the zombie boss anywhere (used by /halloween trigger); applyCooldown=false skips the creator cooldown. */
    public void spawnPumpkinMonster(Location loc, Player creator, boolean applyCooldown) {
        Zombie zombie = (Zombie) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
        
        // Set name
        zombie.setCustomName("§6§lPumpkin Monster");
        zombie.setCustomNameVisible(true);
        
        // v3.0.7 - Prevent boss from picking up items (fixes player armor loss)
        zombie.setCanPickupItems(false);
        
        // Equipment
        zombie.getEquipment().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
        zombie.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        zombie.getEquipment().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
        zombie.getEquipment().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
        zombie.getEquipment().setItemInMainHand(new ItemStack(Material.MACE));
        
        // Make equipment not drop
        zombie.getEquipment().setHelmetDropChance(0.0f);
        zombie.getEquipment().setChestplateDropChance(0.0f);
        zombie.getEquipment().setLeggingsDropChance(0.0f);
        zombie.getEquipment().setBootsDropChance(0.0f);
        zombie.getEquipment().setItemInMainHandDropChance(0.0f);
        
        // Stats
        double health = plugin.getConfig().getDouble("pumpkin_zombie_boss.health", 100.0);
        zombie.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
        zombie.setHealth(health);
        
        double damageBoost = plugin.getConfig().getDouble("pumpkin_zombie_boss.damage_boost", 2.0);
        zombie.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(
            zombie.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue() + damageBoost);
        
        double speedBoost = plugin.getConfig().getDouble("pumpkin_zombie_boss.speed_boost", 0.3);
        zombie.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(
            zombie.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue() + speedBoost);
        
        // Potion effects
        zombie.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 1, false, false));
        zombie.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        
        // Track this monster
        pumpkinMonsters.put(zombie.getUniqueId(), creator.getName());
        plugin.getVisuals().trackBoss(zombie, "bossBarZombie"); // v3.4.0
        plugin.getVisuals().titleNearby(loc, plugin.getVisuals().bossBarRadius(), "boss_spawn", "titleBossSpawnZombie", "%PLAYER%", creator.getName());
        
        // Set cooldown for spawner
        if (applyCooldown) {
            plugin.getData().setLastZombieBossSpawn(creator.getUniqueId());
            plugin.getData().saveAsync(); // v3.4.0 - persist cooldown immediately
        }
        plugin.getStats().increment(creator, "bosses_spawned"); // v3.4.0
        
        // v3.0.5 - Start anti-stacking teleportation
        startBossTeleportation(zombie.getUniqueId());
        
        // Broadcast spawn message
        plugin.broadcastAbout(creator, plugin.getMessages().getRandomMessage("pumpkinMonsterSpawn", creator.getName()));
        plugin.getDiscord().sendBossSpawn("Pumpkin Monster", creator.getName()); // v3.4.0
        
        // Sound effects
        loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.8f);
        loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 0.5f);
    }

    @EventHandler
    public void onPumpkinMonsterKill(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getDamager() instanceof Zombie)) return;
        
        Zombie zombie = (Zombie) event.getDamager();
        if (!pumpkinMonsters.containsKey(zombie.getUniqueId())) return;
        
        Player victim = (Player) event.getEntity();
        if (victim.getHealth() - event.getFinalDamage() <= 0) {
            // Player will die
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.sendEventMessage(victim, 
                    plugin.getMessages().getRandomMessage("pumpkinMonsterKill", victim.getName()));
            }, 1L);
        }
    }

    @EventHandler
    public void onPumpkinMonsterDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Zombie)) return;
        
        Zombie zombie = (Zombie) event.getEntity();
        if (!pumpkinMonsters.containsKey(zombie.getUniqueId())) return;
        
        String creatorName = pumpkinMonsters.remove(zombie.getUniqueId());
        plugin.getVisuals().untrackBoss(zombie.getUniqueId()); // v3.4.0
        stopBossTeleportation(zombie.getUniqueId()); // Stop teleportation task when boss dies
        Player killer = zombie.getKiller();
        
        if (killer != null) {
            // Clear default drops and XP (we handle rewards manually)
            event.getDrops().clear();
            event.setDroppedExp(1); // Minimum valid value (prevents error)
            
            // Give smart XP reward (scales with player level)
            // Base reward: 10 (equivalent to 10 levels at level 1)
            // Multiplier: 1.0 (normal boss)
            int baseXpReward = plugin.getConfig().getInt("pumpkin_zombie_boss.xp_reward", 10);
            plugin.giveSmartXpReward(killer, baseXpReward, 1.0);
            
            // Spawn reward chest at ground level (where zombie died)
            Location chestLoc = zombie.getLocation(); // Same level as death
            Block chestBlock = chestLoc.getBlock();
            
            // Clear the block first, then set to chest
            chestBlock.setType(Material.AIR);
            chestBlock.setType(Material.CHEST);
            
            // Wait a tick to ensure block is fully placed, then add items
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (chestBlock.getType() == Material.CHEST) {
                    org.bukkit.block.Chest chest = (org.bukkit.block.Chest) chestBlock.getState();
                    
                    // v3.4.0 - shared reward service (material/candy/command/money, chance respected)
                    plugin.getRewards().give(plugin.getConfig().getMapList("pumpkin_zombie_boss.rewards"),
                        new RewardService.Context("pumpkin_zombie_boss.rewards").forPlayer(killer).items(chest.getInventory()::addItem));
                    plugin.getCandy().dropFor("boss_defeat", killer, new java.util.ArrayList<>() {
                        @Override public boolean add(ItemStack it) { chest.getInventory().addItem(it); return true; }
                    });
                }
            }, 1L);
            
            // Broadcast victory
            plugin.broadcastAbout(killer, plugin.getMessages().getRandomMessage("pumpkinMonsterDefeat", killer.getName()));
            plugin.getStats().increment(killer, "bosses_defeated"); // v3.4.0
            plugin.getDiscord().sendBossDefeat("Pumpkin Monster", killer.getName());
            
            // Effects
            chestLoc.getWorld().playSound(chestLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            chestLoc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, chestLoc.add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
        }
    }

    /**
     * Despawn all active Pumpkin Monster bosses (called when Halloween is disabled)
     */
    public void despawnAllBosses() {
        for (UUID bossId : pumpkinMonsters.keySet()) {
            plugin.getVisuals().untrackBoss(bossId); // v3.4.0
            for (World world : Bukkit.getWorlds()) {
                for (Zombie zombie : world.getEntitiesByClass(Zombie.class)) {
                    if (zombie.getUniqueId().equals(bossId)) {
                        zombie.remove();
                        plugin.getLogger().info("Despawned Pumpkin Monster boss (Halloween disabled)");
                        break;
                    }
                }
            }
        }
        pumpkinMonsters.clear();
    }
}
