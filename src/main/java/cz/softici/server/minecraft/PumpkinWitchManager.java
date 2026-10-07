package cz.softici.server.minecraft;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class PumpkinWitchManager implements Listener {
    private final HalloweenPlugin plugin;
    private final Map<UUID, String> pumpkinWitches = new HashMap<>(); // Boss UUID -> Creator name
    private final Map<UUID, WitchBossData> witchData = new HashMap<>(); // Boss UUID -> Boss data
    private final Map<UUID, Integer> teleportTasks = new HashMap<>(); // Boss UUID -> Task ID
    private final Map<String, Long> lastBlockClick = new HashMap<>(); // Block location -> Last click time (for debounce)
    
    public PumpkinWitchManager(HalloweenPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onFlintAndSteelUse(PlayerInteractEvent event) {
        // Check if Halloween is globally enabled
        if (!plugin.getData().isGlobalEnabled()) return;
        if (!plugin.getConfig().getBoolean("pumpkin_skeleton_boss.enabled", true)) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getItem() == null || event.getItem().getType() != Material.FLINT_AND_STEEL) return;
        
        Player player = event.getPlayer();
        
        // v3.1.0 - Check if player is in blacklisted world
        if (plugin.isWorldBlacklisted(player)) return;
        
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.CARVED_PUMPKIN) return;
        
        // Check if lapis lazuli block is below the pumpkin
        Block lapisBlock = clickedBlock.getRelative(BlockFace.DOWN);
        if (lapisBlock.getType() != Material.LAPIS_BLOCK) return;
        
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
        int cooldownMinutes = plugin.getConfig().getInt("pumpkin_skeleton_boss.cooldown_minutes", 10);
        if (!plugin.getData().canSpawnSkeletonBoss(player.getUniqueId(), cooldownMinutes)) {
            long remainingMs = plugin.getData().getRemainingSkeletonBossCooldown(player.getUniqueId(), cooldownMinutes);
            long remainingMinutes = remainingMs / (60 * 1000L);
            long remainingSeconds = (remainingMs % (60 * 1000L)) / 1000L;
            
            String msg = plugin.getMessages().getRandomMessage("bossCooldown", "")
                .replace("%MINUTES%", String.valueOf(remainingMinutes))
                .replace("%SECONDS%", String.valueOf(remainingSeconds))
                .replace("%BOSS_TYPE%", "Skeleton");
            player.sendMessage(msg);
            event.setCancelled(true);
            return;
        }
        
        // v3.0.6 - Check for rapid-click exploit (debounce)
        String blockKey = lapisBlock.getLocation().toString();
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
        
        // Valid structure! Spawn Pumpkin Witch
        event.setCancelled(true); // Prevent fire from being placed
        Location spawnLoc = lapisBlock.getLocation().add(0.5, 0, 0.5);
        
        Block pumpkinBlock = clickedBlock;
        
        // Play magical sound
        player.getWorld().playSound(pumpkinBlock.getLocation(), Sound.ENTITY_WITCH_AMBIENT, 1.0f, 0.5f);
        
        // Create magical particle effect
        pumpkinBlock.getWorld().spawnParticle(Particle.WITCH, pumpkinBlock.getLocation().add(0.5, 0.5, 0.5), 30, 0.3, 0.3, 0.3, 0.05);
        
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
            // Remove blocks with magical effect
            pumpkinBlock.getWorld().spawnParticle(Particle.SOUL, pumpkinBlock.getLocation().add(0.5, 0.5, 0.5), 15, 0.2, 0.2, 0.2, 0.1);
            pumpkinBlock.setType(Material.AIR);
            lapisBlock.setType(Material.AIR);

            // Spawn boss
            spawnPumpkinWitch(spawnLoc, player);
        }, 10L); // Half second delay for effect
    }

    private void spawnPumpkinWitch(Location loc, Player creator) {
        spawnPumpkinWitch(loc, creator, true);
    }

    /** v3.4.0 - Spawn the skeleton boss anywhere (used by /halloween trigger); applyCooldown=false skips the creator cooldown. */
    public void spawnPumpkinWitch(Location loc, Player creator, boolean applyCooldown) {
        Skeleton witch = (Skeleton) loc.getWorld().spawnEntity(loc, EntityType.SKELETON);
        
        // Set name
        witch.setCustomName("§6§lHalloween Boss");
        witch.setCustomNameVisible(true);
        
        // v3.0.7 - Prevent boss from picking up items (fixes player armor loss)
        witch.setCanPickupItems(false);
        
        // Equipment - Full Netherite armor + Pumpkin helmet
        witch.getEquipment().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
        witch.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        witch.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
        witch.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
        witch.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
        
        // Make equipment not drop
        witch.getEquipment().setHelmetDropChance(0.0f);
        witch.getEquipment().setChestplateDropChance(0.0f);
        witch.getEquipment().setLeggingsDropChance(0.0f);
        witch.getEquipment().setBootsDropChance(0.0f);
        witch.getEquipment().setItemInMainHandDropChance(0.0f);
        
        // Stats - Same as zombie boss
        double health = plugin.getConfig().getDouble("pumpkin_skeleton_boss.health", 100.0);
        witch.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
        witch.setHealth(health);
        
        double damageBoost = plugin.getConfig().getDouble("pumpkin_skeleton_boss.damage_boost", 3.0);
        witch.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(
            witch.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue() + damageBoost);
        
        double speedBoost = plugin.getConfig().getDouble("pumpkin_skeleton_boss.speed_boost", 0.3);
        witch.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(
            witch.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue() + speedBoost);
        
        // Potion effects
        witch.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 1, false, false));
        witch.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 1, false, false));
        
        // Track this witch
        pumpkinWitches.put(witch.getUniqueId(), creator.getName());
        plugin.getVisuals().trackBoss(witch, "bossBarSkeleton"); // v3.4.0
        plugin.getVisuals().titleNearby(loc, plugin.getVisuals().bossBarRadius(), "boss_spawn", "titleBossSpawnSkeleton", "%PLAYER%", creator.getName());
        
        // Set cooldown for spawner
        if (applyCooldown) {
            plugin.getData().setLastSkeletonBossSpawn(creator.getUniqueId());
            plugin.getData().saveAsync(); // v3.4.0 - persist cooldown immediately
        }
        plugin.getStats().increment(creator, "bosses_spawned"); // v3.4.0
        
        // Create boss data and start minion spawning
        WitchBossData bossData = new WitchBossData(witch);
        witchData.put(witch.getUniqueId(), bossData);
        startMinionSpawning(witch, bossData);
        
        // Start anti-stacking teleportation
        startBossTeleportation(witch);
        
        // Broadcast spawn message
        plugin.broadcastAbout(creator, plugin.getMessages().getRandomMessage("pumpkinWitchSpawn", creator.getName()));
        plugin.getDiscord().sendBossSpawn("Halloween Boss", creator.getName()); // v3.4.0
        
        // Sound effects
        loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.5f);
        loc.getWorld().playSound(loc, Sound.ENTITY_WITCH_CELEBRATE, 1.0f, 0.7f);
        
        // Spawn particles
        loc.getWorld().spawnParticle(Particle.WITCH, loc.add(0, 1, 0), 100, 0.5, 1.0, 0.5, 0.1);
    }

    private void startMinionSpawning(Skeleton witch, WitchBossData bossData) {
        int batInterval = plugin.getConfig().getInt("pumpkin_skeleton_boss.bat_spawn_interval", 10);
        int phantomInterval = plugin.getConfig().getInt("pumpkin_skeleton_boss.phantom_spawn_interval", 60);
        
        // Bat spawning task (every 10 seconds)
        bossData.batTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (witch.isDead() || !witch.isValid()) {
                stopMinionSpawning(witch.getUniqueId());
                return;
            }
            
            // Check if boss is in combat (damaged recently or has target)
            if (witch.getTarget() != null && witch.getTarget() instanceof Player) {
                spawnBatMinion(witch, bossData);
            }
        }, batInterval * 20L, batInterval * 20L);
        
        // Phantom spawning task (every 60 seconds)
        bossData.phantomTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (witch.isDead() || !witch.isValid()) {
                stopMinionSpawning(witch.getUniqueId());
                return;
            }
            
            // Check if boss is in combat
            if (witch.getTarget() != null && witch.getTarget() instanceof Player) {
                spawnPhantomMinion(witch, bossData);
            }
        }, phantomInterval * 20L, phantomInterval * 20L);
    }

    private void spawnBatMinion(Skeleton witch, WitchBossData bossData) {
        // ALWAYS despawn old bat first (if exists)
        if (bossData.currentBat != null && !bossData.currentBat.isDead() && bossData.currentBat.isValid()) {
            bossData.currentBat.remove();
        }
        
        // ALWAYS spawn new bat at EXACT boss coordinates
        Location spawnLoc = witch.getLocation().clone();
        
        Bat bat = (Bat) witch.getWorld().spawnEntity(spawnLoc, EntityType.BAT);
        bat.setCustomName("§8Cursed Bat");
        bat.setCustomNameVisible(false);
        
        // Make bat aggressive (target boss's target)
        if (witch.getTarget() instanceof Player) {
            // Bats can't naturally attack, but we track it for the boss
        }
        
        bossData.currentBat = bat;
        
        // Play sound
        witch.getWorld().playSound(witch.getLocation(), Sound.ENTITY_BAT_AMBIENT, 1.0f, 0.8f);
    }

    private void spawnPhantomMinion(Skeleton witch, WitchBossData bossData) {
        // ALWAYS despawn old phantom first (if exists)
        if (bossData.currentPhantom != null && !bossData.currentPhantom.isDead() && bossData.currentPhantom.isValid()) {
            bossData.currentPhantom.remove();
        }
        
        // ALWAYS spawn new phantom at EXACT boss coordinates
        Location spawnLoc = witch.getLocation().clone();
        
        Phantom phantom = (Phantom) witch.getWorld().spawnEntity(spawnLoc, EntityType.PHANTOM);
        phantom.setCustomName("§7Haunting Phantom");
        phantom.setCustomNameVisible(false);
        
        // Make phantom target boss's target
        if (witch.getTarget() instanceof Player) {
            phantom.setTarget(witch.getTarget());
        }
        
        bossData.currentPhantom = phantom;
        
        // Play sound
        witch.getWorld().playSound(witch.getLocation(), Sound.ENTITY_PHANTOM_AMBIENT, 1.0f, 0.7f);
    }

    private void stopMinionSpawning(UUID witchUUID) {
        WitchBossData bossData = witchData.get(witchUUID);
        if (bossData != null) {
            if (bossData.batTask != null) {
                bossData.batTask.cancel();
            }
            if (bossData.phantomTask != null) {
                bossData.phantomTask.cancel();
            }
            
            // Remove minions
            if (bossData.currentBat != null && !bossData.currentBat.isDead()) {
                bossData.currentBat.remove();
            }
            if (bossData.currentPhantom != null && !bossData.currentPhantom.isDead()) {
                bossData.currentPhantom.remove();
            }
            
            witchData.remove(witchUUID);
        }
    }

    @EventHandler
    public void onWitchDamagePlayer(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Skeleton)) return;
        Skeleton witch = (Skeleton) event.getDamager();
        
        if (!pumpkinWitches.containsKey(witch.getUniqueId())) return;
        
        // Boss hit a player - message handled by death event
    }

    @EventHandler
    public void onWitchDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Skeleton)) return;
        Skeleton witch = (Skeleton) event.getEntity();
        
        if (!pumpkinWitches.containsKey(witch.getUniqueId())) return;
        
        String creatorName = pumpkinWitches.remove(witch.getUniqueId());
        plugin.getVisuals().untrackBoss(witch.getUniqueId()); // v3.4.0
        stopBossTeleportation(witch.getUniqueId()); // Stop teleportation task when boss dies
        Player killer = witch.getKiller();
        
        // Stop minion spawning
        stopMinionSpawning(witch.getUniqueId());
        
        if (killer != null) {
            // Clear default drops and XP (we handle rewards manually)
            event.getDrops().clear();
            event.setDroppedExp(1); // Minimum valid value (prevents error)
            
            // Give smart XP reward (scales with player level)
            // Base reward: 15 (equivalent to 15 levels at level 1)
            // Multiplier: 1.4 (stronger boss = +40% more XP than Pumpkin Zombie Boss)
            int baseXpReward = plugin.getConfig().getInt("pumpkin_skeleton_boss.xp_reward", 15);
            plugin.giveSmartXpReward(killer, baseXpReward, 1.4);
            
            // Spawn reward chest at ground level
            Location chestLoc = witch.getLocation();
            Block chestBlock = chestLoc.getBlock();
            
            // Clear the block first, then set to chest
            chestBlock.setType(Material.AIR);
            chestBlock.setType(Material.CHEST);
            
            // Wait a tick to ensure block is fully placed, then add items
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (chestBlock.getType() == Material.CHEST) {
                    org.bukkit.block.Chest chest = (org.bukkit.block.Chest) chestBlock.getState();
                    
                    // v3.4.0 - shared reward service (material/candy/command/money, chance respected, POTION = Witch's Brew)
                    plugin.getRewards().give(plugin.getConfig().getMapList("pumpkin_skeleton_boss.rewards"),
                        new RewardService.Context("pumpkin_skeleton_boss.rewards").forPlayer(killer).items(chest.getInventory()::addItem));
                    plugin.getCandy().dropFor("boss_defeat", killer, new java.util.ArrayList<>() {
                        @Override public boolean add(ItemStack it) { chest.getInventory().addItem(it); return true; }
                    });
                }
            }, 1L);
            
            // Broadcast victory
            plugin.broadcastAbout(killer, plugin.getMessages().getRandomMessage("pumpkinWitchDefeat", killer.getName()));
            plugin.getStats().increment(killer, "bosses_defeated"); // v3.4.0
            plugin.getDiscord().sendBossDefeat("Halloween Boss", killer.getName());
            
            // Effects
            chestLoc.getWorld().playSound(chestLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
            chestLoc.getWorld().spawnParticle(Particle.WITCH, chestLoc.add(0, 1, 0), 100, 0.5, 0.5, 0.5, 0.1);
        }
    }


    // Inner class to track boss data
    private static class WitchBossData {
        Skeleton witch;
        BukkitTask batTask;
        BukkitTask phantomTask;
        Bat currentBat;
        Phantom currentPhantom;
        
        WitchBossData(Skeleton witch) {
            this.witch = witch;
        }
    }

    /**
     * Despawn all active Halloween Boss (skeleton) bosses (called when Halloween is disabled)
     */
    public void despawnAllBosses() {
        for (UUID bossId : pumpkinWitches.keySet()) {
            plugin.getVisuals().untrackBoss(bossId); // v3.4.0
            // Stop minion spawning tasks
            stopMinionSpawning(bossId);
            
            // Stop teleportation tasks
            stopBossTeleportation(bossId);
            
            // Remove the boss entity
            for (World world : Bukkit.getWorlds()) {
                for (Skeleton skeleton : world.getEntitiesByClass(Skeleton.class)) {
                    if (skeleton.getUniqueId().equals(bossId)) {
                        skeleton.remove();
                        plugin.getLogger().info("Despawned Halloween Boss (Halloween disabled)");
                        break;
                    }
                }
            }
        }
        pumpkinWitches.clear();
        witchData.clear();
        teleportTasks.clear();
    }

    /**
     * Start anti-stacking teleportation for a boss
     */
    private void startBossTeleportation(Skeleton boss) {
        UUID bossId = boss.getUniqueId();
        
        // Create repeating task that runs every 5-15 seconds
        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Check if boss still exists and is alive
            if (boss.isDead() || !boss.isValid()) {
                stopBossTeleportation(bossId);
                return;
            }
            
            // Find closest player within 30 blocks
            Player closestPlayer = null;
            double closestDistance = 30.0; // Max range
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld() != boss.getWorld()) continue;
                // v3.3.0 - Don't hunt vanished/spectating/opted-out players
                if (!plugin.isEligible(player)) continue;
                
                double distance = player.getLocation().distance(boss.getLocation());
                if (distance < closestDistance) {
                    closestPlayer = player;
                    closestDistance = distance;
                }
            }
            
            if (closestPlayer != null) {
                // Find safe location near player
                Location targetLoc = getSafeLocation(closestPlayer.getLocation());
                if (targetLoc != null) {
                    // Teleport with visual/audio effects
                    Location oldLoc = boss.getLocation();
                    
                    // Effects at old location
                    boss.getWorld().spawnParticle(Particle.WITCH, oldLoc, 50, 1.0, 1.0, 1.0, 0.1);
                    boss.getWorld().playSound(oldLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
                    
                    // Teleport
                    boss.teleport(targetLoc);
                    
                    // Effects at new location
                    boss.getWorld().spawnParticle(Particle.WITCH, targetLoc, 50, 1.0, 1.0, 1.0, 0.1);
                    boss.getWorld().playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
                }
            }
        }, 100L, 200L).getTaskId(); // Start after 5 seconds, repeat every 10 seconds
        
        teleportTasks.put(bossId, taskId);
    }
    
    /**
     * Stop teleportation for a boss
     */
    private void stopBossTeleportation(UUID bossId) {
        Integer taskId = teleportTasks.remove(bossId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }
    
    /**
     * Find a safe location near the target location
     */
    private Location getSafeLocation(Location target) {
        World world = target.getWorld();
        int centerX = target.getBlockX();
        int centerZ = target.getBlockZ();
        
        // Try locations in a 5-block radius
        for (int radius = 1; radius <= 5; radius++) {
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    // Find ground level
                    for (int y = Math.max(1, target.getBlockY() - 5); y <= target.getBlockY() + 10; y++) {
                        Location loc = new Location(world, x + 0.5, y, z + 0.5);
                        
                        // Check if location is safe (solid ground, air above)
                        if (world.getBlockAt(x, y - 1, z).getType().isSolid() &&
                            world.getBlockAt(x, y, z).getType().isAir() &&
                            world.getBlockAt(x, y + 1, z).getType().isAir()) {
                            return loc;
                        }
                    }
                }
            }
        }
        
        // Fallback: return original location at ground level
        return target.clone().add(0, 1, 0);
    }
}
