package cz.softici.server.minecraft.tasks;

import cz.softici.server.minecraft.HalloweenPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Herobrine Progressive Scare Event Task
 * 
 * Spawns Herobrine (armor stand with Steve skin and white eyes) at night
 * Progressive spawning system:
 *   Stage 1: Spawn at 3 chunks distance (border of view)
 *   Stage 2: Spawn at 2 chunks distance
 *   Stage 3: Spawn at 1 chunk distance
 *   Stage 4: Spawn behind player, play horror sound, hit to 0.5 hearts
 * 
 * Herobrine despawns if player approaches (except stage 4)
 * 
 * @version 1.7.0
 */
public class HerobrineTask {
    private final HalloweenPlugin plugin;
    private final Player player;
    private int taskId = -1;
    private int currentStage = 0;
    private List<ArmorStand> activeHerobrines = new ArrayList<>();
    private boolean sequenceRunning = false; // Prevent multiple sequences
    private boolean oneShot = false; // v3.4.0 - true when started by /halloween trigger: don't reschedule afterwards

    public HerobrineTask(HalloweenPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void scheduleNext() {
        // Cancel any existing task
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        // v3.0.0 - Check if event is enabled
        if (!plugin.getConfig().getBoolean("events.herobrine.enabled", true)) {
            return;
        }

        // v3.0.0 - Read intervals from config.yml (events section)
        int min = plugin.getConfig().getInt("events.herobrine.min", 20);
        int max = plugin.getConfig().getInt("events.herobrine.max", 40);
        
        // Safety check: if interval is 0 0, the event is disabled
        // Don't schedule anything to prevent server crashes
        if (min == 0 && max == 0) {
            return;
        }
        
        // Ensure minimum delay of at least 1 tick to prevent infinite loops
        long delay = plugin.eventDelayTicks(player, min, max); // v3.4.0 - shared (witching hour multiplier)
        if (delay < 1) {
            delay = 20; // Default to 1 second minimum
        }

        // v3.0.0 FIX: If it's currently day, wait until night before starting timer
        // This prevents Herobrine from "wasting" spawn attempts during daytime
        if (!isNight()) {
            // Check every 30 seconds if it's night yet
            taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (plugin.getData().isGlobalEnabled() && plugin.isEligible(player)) {
                    scheduleNext(); // Try again (will either wait for night or start timer)
                }
            }, 600L).getTaskId(); // 30 seconds
            return;
        }

        // It's night - start the actual event timer
        taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getData().isGlobalEnabled() && plugin.isEligible(player) && isNight()) {
                // Start progressive spawning sequence
                currentStage = 0;
                startProgressiveSpawning();
                plugin.getStats().increment(player, "scares"); // v3.4.0
            } else {
                // Not night time or not eligible, schedule next attempt
                scheduleNext();
            }
        }, delay).getTaskId();
    }

    /**
     * Starts the progressive spawning sequence (4 stages)
     * New behavior:
     * - Stages 1-3: Herobrine appears, despawns after 20 seconds OR if player approaches
     * - Each stage waits 10-20 seconds after previous despawn before next spawn
     * - Stage 4: No visible Herobrine, just attack from behind with effects
     * Prevents multiple sequences from running simultaneously
     */
    /**
     * v3.4.0 - Start the Herobrine sequence right now (used by /halloween trigger).
     * @return false when it can't start (already running, not night, not in the overworld)
     */
    public boolean triggerNow() {
        if (sequenceRunning || !isValidSpawnConditions()) return false;
        oneShot = (taskId == -1); // a task created only for the trigger must not start the regular timer afterwards
        currentStage = 0;
        startProgressiveSpawning();
        plugin.getStats().increment(player, "scares");
        return sequenceRunning;
    }

    private void startProgressiveSpawning() {
        // Prevent multiple sequences from running at once
        if (sequenceRunning) {
            plugin.getLogger().warning("Herobrine sequence already running for " + player.getName() + " - skipping duplicate");
            return;
        }
        
        sequenceRunning = true;
        
        // Stage 1: Far away (3 chunks)
        currentStage = 1;
        spawnHerobrineWithTimer(3, 10, 20); // 10-20 seconds until next stage

        // Stages 2, 3, and 4 are scheduled dynamically after each despawn
        // See spawnHerobrineWithTimer() method
    }

    /**
     * Spawns Herobrine with auto-despawn timer and schedules next stage
     * @param chunkDistance Distance in chunks from player
     * @param minDelay Minimum seconds until next stage
     * @param maxDelay Maximum seconds until next stage
     */
    private void spawnHerobrineWithTimer(int chunkDistance, int minDelay, int maxDelay) {
        if (!player.isOnline() || !isValidSpawnConditions()) return;

        // Calculate safe spawn location
        Location spawnLoc = findSafeSpawnLocation(chunkDistance);
        if (spawnLoc == null) {
            // Can't find safe spawn, skip to next stage
            scheduleNextStage(minDelay, maxDelay);
            return;
        }

        // Create and track Herobrine
        ArmorStand herobrine = createHerobrineEntity(spawnLoc);
        activeHerobrines.add(herobrine);

        // Make Herobrine look at player
        lookAtPlayer(herobrine, player.getLocation());

        // Send stage message
        String stageName = "herobrine_stage" + currentStage;
        plugin.sendEventMessage(player, plugin.getMessages().getRandomMessage(stageName, player.getName()));

        // Start proximity check (despawn if player approaches within 10 blocks)
        startProximityCheck(herobrine, 10.0);

        // Auto-despawn after 20 seconds and schedule next stage
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (herobrine.isValid()) {
                herobrine.remove();
                activeHerobrines.remove(herobrine);
            }
            // Schedule next stage after 10-20 seconds
            scheduleNextStage(minDelay, maxDelay);
        }, 20 * 20L); // 20 seconds
    }

    /**
     * Schedules the next stage after a random delay
     */
    private void scheduleNextStage(int minSeconds, int maxSeconds) {
        if (!sequenceRunning || !player.isOnline()) {
            sequenceRunning = false;
            return;
        }

        int delay = minSeconds + plugin.getRandom().nextInt(maxSeconds - minSeconds + 1);
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.isEligible(player) || !isNight() || !sequenceRunning) {
                // Sequence interrupted (day arrived, player changed mode, etc.)
                sequenceRunning = false;
                removeAllHerobrines(); // Clean up any remaining Herobrines
                
                // v3.0.0: Schedule next attempt immediately (timer will wait for night)
                // This ensures Herobrine doesn't "miss" spawns due to day/night cycles
                if (!oneShot) Bukkit.getScheduler().runTaskLater(plugin, this::scheduleNext, 100L); // v3.4.0 - not after a manual trigger
                return;
            }

            currentStage++;
            
            if (currentStage == 2) {
                // Stage 2: Medium distance
                spawnHerobrineWithTimer(2, 10, 20);
            } else if (currentStage == 3) {
                // Stage 3: Close distance
                spawnHerobrineWithTimer(1, 10, 20);
            } else if (currentStage == 4) {
                // Stage 4: Final encounter - no visible Herobrine, just attack
                finalEncounterInvisible();
                sequenceRunning = false;
                // Schedule next full event
                if (!oneShot) Bukkit.getScheduler().runTaskLater(plugin, this::scheduleNext, 100L); // v3.4.0 - not after a manual trigger
            }
        }, delay * 20L);
    }

    /**
     * Finds a safe spawn location for Herobrine
     * - Not in water
     * - Not underground (must have sky access)
     * - Not in Nether or End
     * - At specified chunk distance from player
     */
    private Location findSafeSpawnLocation(int chunkDistance) {
        if (!player.isOnline()) return null;

        Location playerLoc = player.getLocation();
        World world = playerLoc.getWorld();

        // Don't spawn in Nether or End
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return null;
        }

        int renderDistance = plugin.getConfig().getInt("herobrine.render_distance", 3);
        if (chunkDistance > renderDistance) {
            chunkDistance = renderDistance;
        }

        // Try up to 10 times to find a safe location
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = plugin.getRandom().nextDouble() * Math.PI * 2;
            double distance = chunkDistance * 16.0;

            double x = playerLoc.getX() + Math.cos(angle) * distance;
            double z = playerLoc.getZ() + Math.sin(angle) * distance;
            
            int y = world.getHighestBlockYAt((int)x, (int)z) + 1;
            Location testLoc = new Location(world, x, y, z);

            // Check if location is safe
            if (isSafeSpawnLocation(testLoc)) {
                return testLoc;
            }
        }

        return null; // Couldn't find safe location
    }

    /**
     * Checks if a location is safe for Herobrine spawn
     */
    private boolean isSafeSpawnLocation(Location loc) {
        // Check if in water
        if (loc.getBlock().getType() == Material.WATER ||
            loc.clone().add(0, -1, 0).getBlock().getType() == Material.WATER) {
            return false;
        }

        // Check if underground (must have direct sky access)
        if (loc.getBlock().getLightFromSky() == 0) {
            return false;
        }

        // Check if there's solid ground below
        if (loc.clone().add(0, -1, 0).getBlock().getType() == Material.AIR) {
            return false;
        }

        return true;
    }

    /**
     * Checks if current conditions allow Herobrine spawning
     */
    private boolean isValidSpawnConditions() {
        if (!player.isOnline()) return false;
        
        World world = player.getWorld();
        
        // Must be in Overworld
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return false;
        }

        // Must be night time
        if (!isNight()) {
            return false;
        }

        return true;
    }

    /**
     * OLD METHOD - Spawns 3-4 Herobrine entities at specified chunk distance
     * Kept for compatibility but not used in new system
     */
    @Deprecated
    private void spawnHerobrine(int chunkDistance) {
        if (!player.isOnline()) return;

        int renderDistance = plugin.getConfig().getInt("herobrine.render_distance", 3);
        
        // Ensure spawn distance doesn't exceed render distance
        if (chunkDistance > renderDistance) {
            chunkDistance = renderDistance;
        }

        int count = 3 + plugin.getRandom().nextInt(2); // 3-4 Herobrines
        Location playerLoc = player.getLocation();

        for (int i = 0; i < count; i++) {
            // Calculate spawn position at specified chunk distance
            double angle = plugin.getRandom().nextDouble() * Math.PI * 2;
            double distance = chunkDistance * 16.0; // chunks to blocks

            double x = playerLoc.getX() + Math.cos(angle) * distance;
            double z = playerLoc.getZ() + Math.sin(angle) * distance;
            
            // Get highest block at that location for Y coordinate
            Location spawnLoc = new Location(
                playerLoc.getWorld(),
                x,
                playerLoc.getWorld().getHighestBlockYAt((int)x, (int)z) + 1,
                z
            );

            // Create Herobrine armor stand
            ArmorStand herobrine = createHerobrineEntity(spawnLoc);
            activeHerobrines.add(herobrine);

            // Make Herobrine look at player
            lookAtPlayer(herobrine, playerLoc);

            // Start proximity check (despawn if player approaches)
            if (currentStage < 4) {
                startProximityCheck(herobrine, 10.0); // Despawn if player within 10 blocks
            }
        }

        // Send stage message
        String stageName = "";
        switch (currentStage) {
            case 1: stageName = "herobrine_stage1"; break;
            case 2: stageName = "herobrine_stage2"; break;
            case 3: stageName = "herobrine_stage3"; break;
        }
        
        if (!stageName.isEmpty()) {
            plugin.sendEventMessage(player, plugin.getMessages().getRandomMessage(stageName, player.getName()));
        }
    }

    /**
     * Final encounter - NO visible Herobrine, just invisible attack from behind
     * Creates creepy atmosphere with visual effects and sounds, then damages player
     */
    private void finalEncounterInvisible() {
        if (!player.isOnline()) return;

        Location playerLoc = player.getLocation();

        // Play ominous sounds building up
        player.playSound(playerLoc, Sound.AMBIENT_CAVE, 1.0f, 0.5f);
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 1.0f, 0.5f);
            }
        }, 20L); // After 1 second

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 0.5f);
            }
        }, 40L); // After 2 seconds

        // Visual effect - create brief particle burst behind player
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                Location behind = player.getLocation().clone().add(
                    player.getLocation().getDirection().multiply(-2).normalize()
                );
                
                // Spawn smoke/darkness particles
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.LARGE_SMOKE,
                    behind,
                    20,  // count
                    0.5, 0.5, 0.5, // spread
                    0.02 // speed
                );
                
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.SOUL_FIRE_FLAME,
                    behind,
                    15,
                    0.3, 0.5, 0.3,
                    0.01
                );
            }
        }, 50L); // After 2.5 seconds

        // THE HIT - Damage player from behind
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                double currentHealth = player.getHealth();
                double targetHealth = 1.0; // 0.5 hearts
                
                if (currentHealth > targetHealth) {
                    player.setHealth(targetHealth);
                    
                    // Play impact sounds
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.8f);
                    player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_HURT, 1.0f, 0.6f);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.5f);
                }

                // Send final message
                plugin.sendEventMessage(player, plugin.getMessages().getRandomMessage("herobrine_stage4", player.getName()));
                
                // More particles at player location (impact effect)
                player.getWorld().spawnParticle(
                    org.bukkit.Particle.CRIT,
                    player.getLocation().add(0, 1, 0),
                    30,
                    0.5, 0.5, 0.5,
                    0.1
                );
            }
        }, 60L); // After 3 seconds
    }

    /**
     * OLD Final encounter - Herobrine appears behind player (DEPRECATED - not used in new system)
     * Kept for reference
     */
    @Deprecated
    private void finalEncounter() {
        if (!player.isOnline()) return;

        // Spawn Herobrine directly behind player (5 blocks away)
        Location playerLoc = player.getLocation();
        Vector behind = playerLoc.getDirection().multiply(-1).normalize().multiply(5);
        Location spawnLoc = playerLoc.clone().add(behind);
        spawnLoc.setY(playerLoc.getWorld().getHighestBlockYAt(spawnLoc) + 1);

        ArmorStand herobrine = createHerobrineEntity(spawnLoc);
        activeHerobrines.add(herobrine);

        // Make Herobrine look at player
        lookAtPlayer(herobrine, playerLoc);

        // Play horror sound
        player.playSound(playerLoc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
        player.playSound(playerLoc, Sound.AMBIENT_CAVE, 1.0f, 0.5f);

        // Damage player to 0.5 hearts after short delay
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                double currentHealth = player.getHealth();
                double targetHealth = 1.0; // 0.5 hearts
                
                if (currentHealth > targetHealth) {
                    player.setHealth(targetHealth);
                    
                    // Play damage sound
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
                    player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_HURT, 1.0f, 0.8f);
                }

                // Send final message
                plugin.sendEventMessage(player, plugin.getMessages().getRandomMessage("herobrine_stage4", player.getName()));
            }

            // Remove Herobrine after 3 seconds
            Bukkit.getScheduler().runTaskLater(plugin, this::removeAllHerobrines, 60L);
        }, 20L);
    }

    /**
     * Creates Herobrine entity (armor stand with Steve skin and white eyes)
     */
    private ArmorStand createHerobrineEntity(Location loc) {
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        
        // Configure armor stand
        stand.setVisible(true);
        stand.setBasePlate(false);
        stand.setArms(true);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setCustomName("§fHerobrine");
        stand.setCustomNameVisible(false);
        stand.setMarker(false);

        // Create Herobrine head (Steve with white eyes)
        ItemStack head = createHerobrineHead();
        stand.getEquipment().setHelmet(head);

        // Steve's clothing (cyan shirt, blue pants)
        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        org.bukkit.inventory.meta.LeatherArmorMeta chestMeta = (org.bukkit.inventory.meta.LeatherArmorMeta) chest.getItemMeta();
        chestMeta.setColor(org.bukkit.Color.fromRGB(112, 203, 211)); // Light blue/cyan (Steve's shirt)
        chest.setItemMeta(chestMeta);
        
        ItemStack legs = new ItemStack(Material.LEATHER_LEGGINGS);
        org.bukkit.inventory.meta.LeatherArmorMeta legsMeta = (org.bukkit.inventory.meta.LeatherArmorMeta) legs.getItemMeta();
        legsMeta.setColor(org.bukkit.Color.fromRGB(41, 42, 143)); // Dark blue (Steve's pants)
        legs.setItemMeta(legsMeta);
        
        stand.getEquipment().setChestplate(chest);
        stand.getEquipment().setLeggings(legs);

        return stand;
    }
/**
 * v1.7.2 - Creates a player head using Galthorius' skin (Herobrine-like appearance)
 * Fixed: Properly uses 3-argument Property constructor with signature for Mojang textures
 * without compile-time dependency on Mojang's authlib.
 */
private ItemStack createHerobrineHead() {
    ItemStack head = new ItemStack(Material.PLAYER_HEAD);
    org.bukkit.inventory.meta.SkullMeta meta =
            (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();

    // v1.7.2 - Galthorius skin (Herobrine-like appearance with white eyes)
    final String VALUE =
            "ewogICJ0aW1lc3RhbXAiIDogMTc1OTg3MzMzMDYzNywKICAicHJvZmlsZUlkIiA6ICI3OTdhZjc2MjlkMTU0NmE5YWRhOTUyZWQzY2NiMThjNSIsCiAgInByb2ZpbGVOYW1lIiA6ICJHYWx0aG9yaXVzIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzJiYTNiYzNiOGY1YTU4N2JhYTIzOWIxZDFhZTQ0OWVmNTdlOGVhYTcwZTI5ZTU5MGEwNTcyMmU2MWYyODI2YjgiCiAgICB9CiAgfQp9";
    final String SIGNATURE =
            "YckZugg9bRnkPt9W9tUmHOilvjSZhrY3fUHIRSalYGDHE8iJFzvU03dDexQxRyFTGvmX7yyagtzFI8V9phS+Sqjvb9PRQve9zBuBQFpuqgN/Ctik+510xllPcjWCdkZGM96NZXCPZwcSsMDgAdRXwziuWcNwd6HB+54OQTaIGRRbTSDef0/C/0PYfXCJEPc72Lm5kY7n0CmeW+VGUCi/iFXC8mgxgLyOnRf6RGN/FQZ1vhuO6wNpL6X3LudYU6T3jO0U1db8uyYfHYTIi3YI8V+XADGgXXJUFRP0zem0giyVsJKZfNAji0grZ9CKfgRFiRNtMM9vrv4EkTYsq66kqstUy6WUXEO22rbWpyGqD2kZrbEtMzwD5GMSC+KSutUumKDRyFigRPIbByFW6zfzVge1pD8x8yyy5ccNXIVAXn8eYDXT2z5OOmWuj8CbFTLK94Tff4/Pvvr9AnnrDs2sOZUBpJu5v0ccbJYegbBT0LwUkm2cDkLx56WiSGROHCcDoRBhj3MPVO/Shk2KIUPVAYdhkldP2P8mIgvlgRZK4JmYkxasRkuhL/5IhQ+JYa73dID5Sqm22JEmapcj5DE4lnMC2dnrABFWMVfP8kic/JqiUiTOz0sDEr7T6wo+giFOtEWEYhPkseXAnASkZN9PG6Pb+7NsJSPp4rf83BKcEE4=";

    try {
        // Classes we need (via reflection)
        Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> propertyClass     = Class.forName("com.mojang.authlib.properties.Property");

        // new GameProfile(UUID.randomUUID(), "Herobrine")
        Object profile = gameProfileClass
                .getConstructor(java.util.UUID.class, String.class)
                .newInstance(java.util.UUID.randomUUID(), "Herobrine");

        // v1.7.2 - new Property("textures", VALUE, SIGNATURE) - signature required for Mojang textures
        Object property = propertyClass
                .getConstructor(String.class, String.class, String.class)
                .newInstance("textures", VALUE, SIGNATURE);

        // profile.getProperties() returns a PropertyMap (which is a Multimap)
        Object properties = gameProfileClass.getMethod("getProperties").invoke(profile);

        // PropertyMap.put(String key, Property value) - it's NOT a regular Map
        // We need to use the correct method signature for Multimap
        Class<?> propertyMapClass = properties.getClass();
        
        // Try multiple method signatures that different Spigot versions use (silently)
        boolean added = false;
        Exception lastException = null;
        
        // Method 1: Try put(Object, Object) for ForwardingMultimap
        try {
            java.lang.reflect.Method putMethod = propertyMapClass.getMethod("put", Object.class, Object.class);
            putMethod.invoke(properties, "textures", property);
            added = true;
        } catch (Exception e) {
            lastException = e;
        }
        
        // Method 2: Try put(K, V) generic
        if (!added) {
            try {
                java.lang.reflect.Method putMethod = propertyMapClass.getMethod("put", String.class, propertyClass);
                putMethod.invoke(properties, "textures", property);
                added = true;
            } catch (Exception e) {
                lastException = e;
            }
        }
        
        // Method 3: Try using Multimap's put via superclass
        if (!added) {
            try {
                Class<?> multimapClass = Class.forName("com.google.common.collect.Multimap");
                java.lang.reflect.Method putMethod = multimapClass.getMethod("put", Object.class, Object.class);
                putMethod.invoke(properties, "textures", property);
                added = true;
            } catch (Exception e) {
                lastException = e;
            }
        }
        
        // Only throw if all three methods failed
        if (!added && lastException != null) {
            throw new Exception("Could not find working put() method for PropertyMap", lastException);
        }

        // Inject the profile into SkullMeta (private field "profile")
        java.lang.reflect.Field profileField = meta.getClass().getDeclaredField("profile");
        profileField.setAccessible(true);
        profileField.set(meta, profile);

    } catch (Throwable t) {
        // Reflection failed, fallback to player lookup (silent)
        try {
            // Use player lookup - works on Paper 1.21.1+
            org.bukkit.OfflinePlayer offlinePlayer = org.bukkit.Bukkit.getOfflinePlayer("Galthorius");
            meta.setOwningPlayer(offlinePlayer);
            // Skin applied successfully via fallback method
        } catch (Throwable fallbackError) {
            // Both methods failed - use default Steve head (silent fallback)
        }
    }

    head.setItemMeta(meta);
    return head;
}

    /**
     * Makes Herobrine look at the player
     */
    private void lookAtPlayer(ArmorStand herobrine, Location playerLoc) {
        Location heroLoc = herobrine.getLocation();
        Vector direction = playerLoc.toVector().subtract(heroLoc.toVector()).normalize();
        
        // Calculate yaw
        double dx = direction.getX();
        double dz = direction.getZ();
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        
        heroLoc.setYaw((float) yaw);
        herobrine.teleport(heroLoc);
    }

    /**
     * Starts proximity check - despawns Herobrine if player gets too close
     */
    private void startProximityCheck(ArmorStand herobrine, double maxDistance) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!herobrine.isValid() || !player.isOnline()) {
                    this.cancel();
                    return;
                }

                // Check distance
                if (herobrine.getLocation().distance(player.getLocation()) < maxDistance) {
                    // Player approached - despawn Herobrine
                    herobrine.remove();
                    activeHerobrines.remove(herobrine);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 10L, 10L); // Check every 0.5 seconds
    }

    /**
     * Removes all active Herobrine entities
     */
    private void removeAllHerobrines() {
        for (ArmorStand herobrine : activeHerobrines) {
            if (herobrine.isValid()) {
                herobrine.remove();
            }
        }
        activeHerobrines.clear();
    }

    /**
     * Checks if it's night time in the player's world
     */
    private boolean isNight() {
        if (!player.isOnline()) return false;
        
        World world = player.getWorld();
        long time = world.getTime();
        
        // Night is from 13000 to 23000 ticks
        return time >= 13000 && time <= 23000;
    }

    public void cancel() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        removeAllHerobrines();
        resetSequence();
    }
    
    /**
     * v1.7.0 - Resets the sequence state so new events can start fresh
     * Called during cleanup to prevent stuck sequences
     */
    public void resetSequence() {
        sequenceRunning = false;
        currentStage = 0;
    }

}
