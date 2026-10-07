package cz.softici.server.minecraft;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.Candle;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class GraveManager implements Listener {

    private final HalloweenPlugin plugin;
    private final Map<Location, GraveData> activeGraves = new HashMap<>();
    private final Map<UUID, GraveData> participantGraves = new HashMap<>();

    public GraveManager(HalloweenPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCandleLight(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        
        Block block = event.getClickedBlock();
        
        // Check if it's a candle
        if (!(block.getBlockData() instanceof Candle)) return;
        
        // Check if player is using flint and steel or fire charge
        Material item = event.getItem() != null ? event.getItem().getType() : Material.AIR;
        if (item != Material.FLINT_AND_STEEL && item != Material.FIRE_CHARGE) return;
        
        Player player = event.getPlayer();
        
        // v3.1.0 - Check if player is in blacklisted world
        if (plugin.isWorldBlacklisted(player)) return;
        
        Candle candleData = (Candle) block.getBlockData();
        
        // Only trigger if candle is being lit (currently not lit)
        if (candleData.isLit()) return;
        
        // Check if player has Halloween disabled
        if (plugin.getData().isDisabled(player.getUniqueId())) {
            player.sendMessage(plugin.getMessages().getRandomMessage("gravePlayerDisabled", player.getName(), plugin.getCommandPrefix()));
            event.setCancelled(true);
            return;
        }
        
        // Check if Halloween is enabled globally
        if (!plugin.getData().isGlobalEnabled()) {
            player.sendMessage(plugin.getMessages().getRandomMessage("graveGloballyDisabled", player.getName(), plugin.getCommandPrefix()));
            event.setCancelled(true);
            return;
        }
        
        // Check if grave system is enabled
        if (!plugin.getConfig().getBoolean("grave.enabled", true)) {
            return;
        }
        
        // Validate grave structure
        GraveStructure structure = validateGraveStructure(block.getLocation());
        if (structure == null) {
            // Not a valid grave, allow normal candle lighting
            return;
        }
        
        // v3.4.0 - WorldGuard: halloween-events flag at the grave
        if (!plugin.isLocationAllowed(structure.getCenterLocation())) return;
        
        // Check if grave already active
        if (activeGraves.containsKey(structure.getCenterLocation())) {
            player.sendMessage(plugin.getMessages().getRandomMessage("graveAlreadyActive", player.getName()));
            event.setCancelled(true);
            return;
        }
        
        // Check cooldown
        int cooldownMinutes = plugin.getConfig().getInt("grave.cooldown_minutes", 15);
        if (!plugin.getData().canActivateGrave(player.getUniqueId(), cooldownMinutes)) {
            long remainingMs = plugin.getData().getRemainingGraveCooldown(player.getUniqueId(), cooldownMinutes);
            long remainingMinutes = remainingMs / (60 * 1000L);
            long remainingSeconds = (remainingMs % (60 * 1000L)) / 1000L;
            
            String cooldownMsg = plugin.getMessages().getRandomMessage("graveCooldown", player.getName())
                .replace("%MINUTES%", String.valueOf(remainingMinutes))
                .replace("%SECONDS%", String.valueOf(remainingSeconds));
            player.sendMessage(cooldownMsg);
            event.setCancelled(true);
            return;
        }
        
        // Cancel the normal candle lighting
        event.setCancelled(true);
        
        // Light the candle manually
        candleData.setLit(true);
        block.setBlockData(candleData);
        
        // Play ignite sound
        block.getWorld().playSound(block.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, 1.0f, 1.0f);
        
        // Start grave event
        startGraveEvent(player, structure);
    }

    private GraveStructure validateGraveStructure(Location candleLocation) {
        Block candleBlock = candleLocation.getBlock();
        
        
        // Candle must be on top of dirt (one of the 3 dirt blocks on the side/end)
        Block dirtBase = candleBlock.getRelative(BlockFace.DOWN);
        
        if (!isDirt(dirtBase)) {
            return null;
        }
        
        
        // Find the 3 dirt blocks in a row (candle is on one of them)
        Location[] dirtBlocks = findDirtRow(dirtBase.getLocation());
        if (dirtBlocks == null) {
            return null;
        }
        
        
        // Check for coffin (3 wood planks directly under the 3 dirt blocks)
        if (!validateCoffin(dirtBlocks)) {
            return null;
        }
        
        
        // Check for sign with RIP on middle dirt block
        Block signBlock = findRIPSign(dirtBlocks[1]); // Middle block
        if (signBlock == null) {
            return null;
        }
        
        
        // Check if candle is on the last dirt block (not middle where sign is)
        if (dirtBase.getLocation().equals(dirtBlocks[1])) {
            return null;
        }
        
        
        // Find cross - should be directly above one of the dirt blocks (not the candle dirt)
        Location crossLocation = findCrossAboveDirt(dirtBlocks, dirtBase.getLocation());
        if (crossLocation == null) {
            return null;
        }
        
        
        // Determine cross axis direction
        Block crossBase = crossLocation.getBlock();
        Block armsLevel = crossBase.getRelative(0, 2, 0); // Arms are at Y+2
        boolean crossXAxis = isStoneWall(armsLevel.getRelative(1, 0, 0)) && isStoneWall(armsLevel.getRelative(-1, 0, 0));
        
        // v1.7.1 - Determine dirt row axis direction
        // Check if dirt blocks are aligned on X-axis or Z-axis
        boolean dirtRowXAxis = dirtBlocks[0].getX() != dirtBlocks[1].getX(); // X changes = X-axis
        
        // Valid structure found!
        return new GraveStructure(dirtBlocks[0], crossLocation, dirtBlocks[0].clone().add(0, -1, 0), 
                                  signBlock.getLocation(), candleBlock.getLocation(), crossXAxis, dirtRowXAxis);
    }
    
    private Location[] findDirtRow(Location dirtLoc) {
        Block center = dirtLoc.getBlock();
        
        // v1.7.1 Fix: Only count dirt blocks that have wood planks below (part of coffin)
        // This prevents detecting surrounding dirt blocks when building underground
        
        // Try to find 3 dirt blocks in a row (X-axis)
        if (isDirtWithCoffin(center.getRelative(-1, 0, 0)) && isDirtWithCoffin(center) && isDirtWithCoffin(center.getRelative(1, 0, 0))) {
            return new Location[] {
                center.getRelative(-1, 0, 0).getLocation(),
                center.getLocation(),
                center.getRelative(1, 0, 0).getLocation()
            };
        }
        
        // Try starting from left
        if (isDirtWithCoffin(center) && isDirtWithCoffin(center.getRelative(1, 0, 0)) && isDirtWithCoffin(center.getRelative(2, 0, 0))) {
            return new Location[] {
                center.getLocation(),
                center.getRelative(1, 0, 0).getLocation(),
                center.getRelative(2, 0, 0).getLocation()
            };
        }
        
        // Try starting from right
        if (isDirtWithCoffin(center.getRelative(-2, 0, 0)) && isDirtWithCoffin(center.getRelative(-1, 0, 0)) && isDirtWithCoffin(center)) {
            return new Location[] {
                center.getRelative(-2, 0, 0).getLocation(),
                center.getRelative(-1, 0, 0).getLocation(),
                center.getLocation()
            };
        }
        
        // Try to find 3 dirt blocks in a row (Z-axis)
        if (isDirtWithCoffin(center.getRelative(0, 0, -1)) && isDirtWithCoffin(center) && isDirtWithCoffin(center.getRelative(0, 0, 1))) {
            return new Location[] {
                center.getRelative(0, 0, -1).getLocation(),
                center.getLocation(),
                center.getRelative(0, 0, 1).getLocation()
            };
        }
        
        // Try starting from front
        if (isDirtWithCoffin(center) && isDirtWithCoffin(center.getRelative(0, 0, 1)) && isDirtWithCoffin(center.getRelative(0, 0, 2))) {
            return new Location[] {
                center.getLocation(),
                center.getRelative(0, 0, 1).getLocation(),
                center.getRelative(0, 0, 2).getLocation()
            };
        }
        
        // Try starting from back
        if (isDirtWithCoffin(center.getRelative(0, 0, -2)) && isDirtWithCoffin(center.getRelative(0, 0, -1)) && isDirtWithCoffin(center)) {
            return new Location[] {
                center.getRelative(0, 0, -2).getLocation(),
                center.getRelative(0, 0, -1).getLocation(),
                center.getLocation()
            };
        }
        
        return null;
    }
    
    /**
     * Checks if block is dirt/grass AND has wood planks below (part of coffin)
     * This prevents detecting surrounding dirt when building underground
     */
    private boolean isDirtWithCoffin(Block block) {
        if (!isDirt(block)) {
            return false;
        }
        Block below = block.getRelative(0, -1, 0);
        return isWoodPlanks(below);
    }
    
    private boolean validateCoffin(Location[] dirtBlocks) {
        // Check that all 3 blocks below the dirt are wood planks
        for (Location dirtLoc : dirtBlocks) {
            Block below = dirtLoc.getBlock().getRelative(0, -1, 0);
            if (!isWoodPlanks(below)) {
                return false;
            }
        }
        return true;
    }
    
    private Block findRIPSign(Location middleDirt) {
        Block above = middleDirt.getBlock().getRelative(BlockFace.UP);
        if (above.getState() instanceof Sign) {
            Sign sign = (Sign) above.getState();
            @SuppressWarnings("deprecation")
            String[] lines = sign.getLines();
            String text = String.join(" ", lines).toLowerCase();
            if (text.contains("rip") || text.contains("r.i.p") || text.contains("r i p")) {
                return above;
            }
        }
        return null;
    }
    
    private Location findCrossAboveDirt(Location[] dirtBlocks, Location candleDirt) {
        // Cross pattern: Base (Y+1)
        //                Second layer (Y+2)
        //                XXX Arms (Y+3)
        //                Top (Y+4)
        // Cross base should be directly above one of the dirt blocks (not the candle dirt)
        
        
        // Check each dirt block for a cross above it (skip the candle dirt)
        for (int i = 0; i < dirtBlocks.length; i++) {
            Location dirtLoc = dirtBlocks[i];
            
            // Skip the dirt block that has the candle
            if (dirtLoc.equals(candleDirt)) {
                continue;
            }
            
            
            // Check if there's a cross base directly above this dirt block
            Block aboveDirt = dirtLoc.getBlock().getRelative(BlockFace.UP);
            
            // If there's a sign above, check one more block up
            if (aboveDirt.getState() instanceof org.bukkit.block.Sign) {
                aboveDirt = aboveDirt.getRelative(BlockFace.UP);
            }
            
            
            // Check if there's a valid cross here
            if (isValidCrossBase(aboveDirt.getLocation())) {
                return aboveDirt.getLocation();
            }
        }
        
        return null;
    }
    
    private boolean isValidCrossBase(Location baseLoc) {
        Block base = baseLoc.getBlock();
        
        // Cross structure from your description:
        // Layer 1 (Y+0): Base - single cobble wall
        // Layer 2 (Y+1): Second layer - single cobble wall above base
        // Layer 3 (Y+2): Arms - 3 cobble walls (XXX) with middle above second layer
        // Layer 4 (Y+3): Top - single cobble wall above middle of arms
        
        if (!isStoneWall(base)) {
            return false;
        }
        
        Block secondLayer = base.getRelative(0, 1, 0);
        if (!isStoneWall(secondLayer)) {
            return false;
        }
        
        
        // Check arms at Y+2 - middle arm must be above second layer
        Block armsMiddle = base.getRelative(0, 2, 0);
        if (!isStoneWall(armsMiddle)) {
            return false;
        }
        
        // Try X-axis arms
        Block xLeft = armsMiddle.getRelative(-1, 0, 0);
        Block xRight = armsMiddle.getRelative(1, 0, 0);
        
        boolean hasXArms = isStoneWall(xLeft) && isStoneWall(xRight);
        
        // Try Z-axis arms
        Block zFront = armsMiddle.getRelative(0, 0, 1);
        Block zBack = armsMiddle.getRelative(0, 0, -1);
        
        boolean hasZArms = isStoneWall(zFront) && isStoneWall(zBack);
        
        if (!hasXArms && !hasZArms) {
            return false;
        }
        
        
        // Check top layer (Y+3) - must be above middle of arms
        Block top = armsMiddle.getRelative(0, 1, 0);
        if (!isStoneWall(top)) {
            return false;
        }
        
        return true;
    }

    private boolean isDirt(Block block) {
        return block.getType() == Material.DIRT || block.getType() == Material.GRASS_BLOCK;
    }

    private boolean isStoneWall(Block block) {
        return block.getType() == Material.COBBLESTONE_WALL || 
               block.getType() == Material.MOSSY_COBBLESTONE_WALL ||
               block.getType().toString().endsWith("_WALL");
    }

    private boolean isWoodPlanks(Block block) {
        return block.getType().toString().endsWith("_PLANKS");
    }

    private void startGraveEvent(Player player, GraveStructure structure) {
        // Broadcast message
        String message = plugin.getMessageManager().getRandomMessage("graveAwaken", player.getName());
        plugin.broadcastAbout(player, message); // v3.3.0 - respects /halloween disable and vanish
        plugin.getStats().increment(player, "graves_started"); // v3.4.0
        plugin.getDiscord().sendGraveStart(player.getName());
        
        // Create grave data
        GraveData graveData = new GraveData(structure, player);
        activeGraves.put(structure.getCenterLocation(), graveData);
        
        // Set cooldown for activator
        plugin.getData().setLastGraveActivation(player.getUniqueId());
        plugin.getData().saveAsync(); // v3.4.0 - persist cooldown immediately
        
        // Start sinking animation
        startSinkingAnimation(graveData);
    }

    /**
     * v3.2.1 - Schedule a delayed task that belongs to a grave event.
     * The task is tracked in GraveData (so /halloween off can cancel it) and is a no-op
     * if the grave was closed in the meantime.
     */
    private void scheduleForGrave(GraveData graveData, long delayTicks, Runnable action) {
        if (graveData.isClosed()) return;
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isGraveActive(graveData)) return;
            action.run();
        }, delayTicks).getTaskId();
        graveData.trackTask(taskId);
    }

    private boolean isGraveActive(GraveData graveData) {
        return !graveData.isClosed() && activeGraves.containsValue(graveData);
    }

    private void startSinkingAnimation(GraveData graveData) {
        BukkitRunnable animation = new BukkitRunnable() {
            int sinkStage = 0;
            
            @Override
            public void run() {
                // v3.2.1 - Stop the animation if the grave was force-closed (/halloween off)
                if (!isGraveActive(graveData)) {
                    this.cancel();
                    return;
                }
                if (sinkStage < 4) {
                    // Sink cross by 1 block
                    sinkCrossOneBlock(graveData.getStructure());
                    sinkStage++;
                } else if (sinkStage == 4) {
                    // Remove dirt layer, sign, and candles
                    removeDirtLayer(graveData.getStructure());
                    sinkStage++;
                } else if (sinkStage == 5) {
                    // Transform coffin and spawn mobs
                    transformCoffinAndSpawnMobs(graveData);
                    this.cancel();
                }
            }
        };
        graveData.trackTask(animation.runTaskTimer(plugin, 0L, 60L).getTaskId()); // Every 3 seconds (60 ticks)
    }

    private void sinkCrossOneBlock(GraveStructure structure) {
        Location cross = structure.getCrossLocation();
        Block base = cross.getBlock();
        
        // Move arm blocks down FIRST (before moving vertical blocks)
        moveArmsDown(structure);
        
        // Remove bottom block
        base.setType(Material.AIR);
        
        // Move blocks down
        for (int y = 0; y < 3; y++) {
            Block current = base.getRelative(0, y + 1, 0);
            Block below = base.getRelative(0, y, 0);
            below.setType(current.getType());
            below.setBlockData(current.getBlockData());
        }
        
        // Clear top block
        base.getRelative(0, 3, 0).setType(Material.AIR);
        
        // Play sound
        cross.getWorld().playSound(cross, Sound.BLOCK_STONE_BREAK, 1.0f, 0.8f);
        cross.getWorld().spawnParticle(Particle.BLOCK, cross, 20, 0.3, 0.3, 0.3, 0, Material.COBBLESTONE_WALL.createBlockData());
    }

    private void moveArmsDown(GraveStructure structure) {
        Location cross = structure.getCrossLocation();
        Block base = cross.getBlock();
        
        // Use stored axis direction
        boolean xAxis = structure.isCrossXAxis();
        
        // v1.7.2 - Move arms down from top to bottom, properly sinking block by block
        // Process from Y=3 down to Y=0, moving each arm block down by 1
        for (int y = 3; y >= 0; y--) {
            Block levelBlock = base.getRelative(0, y, 0);
            
            if (xAxis) {
                // Check and move X-axis arms
                Block right = levelBlock.getRelative(1, 0, 0);
                Block left = levelBlock.getRelative(-1, 0, 0);
                
                // v1.7.2 - If arm exists at this level, move it down (or remove if at ground)
                if (isStoneWall(right)) {
                    if (y > 0) {
                        // Move down one block
                        Block below = levelBlock.getRelative(1, -1, 0);
                        below.setType(right.getType());
                        below.setBlockData(right.getBlockData());
                    }
                    // Always clear current position (moves down or disappears)
                    right.setType(Material.AIR);
                }
                
                if (isStoneWall(left)) {
                    if (y > 0) {
                        // Move down one block
                        Block below = levelBlock.getRelative(-1, -1, 0);
                        below.setType(left.getType());
                        below.setBlockData(left.getBlockData());
                    }
                    // Always clear current position
                    left.setType(Material.AIR);
                }
            } else {
                // Check and move Z-axis arms
                Block front = levelBlock.getRelative(0, 0, 1);
                Block back = levelBlock.getRelative(0, 0, -1);
                
                // v1.7.2 - Same logic for Z-axis
                if (isStoneWall(front)) {
                    if (y > 0) {
                        Block below = levelBlock.getRelative(0, -1, 1);
                        below.setType(front.getType());
                        below.setBlockData(front.getBlockData());
                    }
                    front.setType(Material.AIR);
                }
                
                if (isStoneWall(back)) {
                    if (y > 0) {
                        Block below = levelBlock.getRelative(0, -1, -1);
                        below.setType(back.getType());
                        below.setBlockData(back.getBlockData());
                    }
                    back.setType(Material.AIR);
                }
            }
        }
    }

    private void removeDirtLayer(GraveStructure structure) {
        Location dirtStart = structure.getDirtLocation();
        boolean xAxis = structure.isDirtRowXAxis(); // v1.7.1 - Use stored axis direction
        
        // v1.7.1 - Remove all 3 dirt blocks using correct axis
        for (int i = 0; i < 3; i++) {
            Block dirt;
            if (xAxis) {
                // Dirt blocks are aligned on X-axis
                dirt = dirtStart.clone().add(i, 0, 0).getBlock();
            } else {
                // Dirt blocks are aligned on Z-axis
                dirt = dirtStart.clone().add(0, 0, i).getBlock();
            }
            
            // Remove dirt block and anything on top (sign, candles)
            dirt.setType(Material.AIR);
            dirt.getRelative(BlockFace.UP).setType(Material.AIR);
        }
        
        // Play sound and particles
        dirtStart.getWorld().playSound(dirtStart, Sound.BLOCK_GRASS_BREAK, 1.0f, 0.7f);
        dirtStart.getWorld().spawnParticle(Particle.BLOCK, dirtStart, 30, 1.0, 0.3, 1.0, 0, Material.DIRT.createBlockData());
    }
    
    private void removeAllCrossBlocks(GraveStructure structure) {
        Location cross = structure.getCrossLocation();
        Block base = cross.getBlock();
        boolean xAxis = structure.isCrossXAxis();
        
        
        // Remove all vertical blocks (up to 4 high)
        for (int y = 0; y < 4; y++) {
            Block vertical = base.getRelative(0, y, 0);
            if (isStoneWall(vertical)) {
                vertical.setType(Material.AIR);
            }
        }
        
        // Remove any remaining arm blocks
        for (int y = 0; y < 4; y++) {
            Block level = base.getRelative(0, y, 0);
            if (xAxis) {
                // Remove X-axis arms
                Block left = level.getRelative(-1, 0, 0);
                Block right = level.getRelative(1, 0, 0);
                if (isStoneWall(left)) left.setType(Material.AIR);
                if (isStoneWall(right)) right.setType(Material.AIR);
            } else {
                // Remove Z-axis arms
                Block front = level.getRelative(0, 0, 1);
                Block back = level.getRelative(0, 0, -1);
                if (isStoneWall(front)) front.setType(Material.AIR);
                if (isStoneWall(back)) back.setType(Material.AIR);
            }
        }
        
    }

    private void transformCoffinAndSpawnMobs(GraveData graveData) {
        GraveStructure structure = graveData.getStructure();
        Location coffinStart = structure.getCoffinLocation();
        World world = coffinStart.getWorld();
        
        // Set time to night
        world.setTime(13000); // Night time
        
        // Transform coffin to nether bricks FIRST (before removing blocks!)
        Block firstWood = coffinStart.getBlock();
        
        // Check X-axis first
        if (isWoodPlanks(firstWood) && isWoodPlanks(firstWood.getRelative(1, 0, 0)) && isWoodPlanks(firstWood.getRelative(2, 0, 0))) {
            // X-axis coffin
            firstWood.setType(Material.NETHER_BRICKS);
            firstWood.getRelative(1, 0, 0).setType(Material.NETHER_BRICKS);
            firstWood.getRelative(2, 0, 0).setType(Material.NETHER_BRICKS);
        } 
        // Check Z-axis
        else if (isWoodPlanks(firstWood) && isWoodPlanks(firstWood.getRelative(0, 0, 1)) && isWoodPlanks(firstWood.getRelative(0, 0, 2))) {
            // Z-axis coffin
            firstWood.setType(Material.NETHER_BRICKS);
            firstWood.getRelative(0, 0, 1).setType(Material.NETHER_BRICKS);
            firstWood.getRelative(0, 0, 2).setType(Material.NETHER_BRICKS);
        }
        // Check negative X-axis
        else if (isWoodPlanks(firstWood) && isWoodPlanks(firstWood.getRelative(-1, 0, 0)) && isWoodPlanks(firstWood.getRelative(-2, 0, 0))) {
            // Negative X-axis coffin
            firstWood.setType(Material.NETHER_BRICKS);
            firstWood.getRelative(-1, 0, 0).setType(Material.NETHER_BRICKS);
            firstWood.getRelative(-2, 0, 0).setType(Material.NETHER_BRICKS);
        }
        // Check negative Z-axis
        else if (isWoodPlanks(firstWood) && isWoodPlanks(firstWood.getRelative(0, 0, -1)) && isWoodPlanks(firstWood.getRelative(0, 0, -2))) {
            // Negative Z-axis coffin
            firstWood.setType(Material.NETHER_BRICKS);
            firstWood.getRelative(0, 0, -1).setType(Material.NETHER_BRICKS);
            firstWood.getRelative(0, 0, -2).setType(Material.NETHER_BRICKS);
        }
        
        // Clean up any remaining cross blocks
        removeAllCrossBlocks(structure);
        
        // ANTI-GRIEF: Remove blocks around grave (players building barriers)
        removeBlocksAroundGrave(coffinStart);
        
        // Play dramatic sound
        world.playSound(coffinStart, Sound.ENTITY_WITHER_SPAWN, 0.5f, 0.7f);
        world.spawnParticle(Particle.LARGE_SMOKE, coffinStart.clone().add(1, 1, 1), 50, 1.0, 1.0, 1.0, 0.05);
        
        // Spawn wave 1 after 3 seconds
        scheduleForGrave(graveData, 60L, () -> spawnWave(graveData));
    }
    
    private void removeBlocksAroundGrave(Location center) {
        // Remove blocks in a 7x7x7 area around the grave (anti-grief)
        // This prevents players from building barriers or roofs
        for (int x = -3; x <= 3; x++) {
            for (int y = 0; y <= 6; y++) {
                for (int z = -3; z <= 3; z++) {
                    Block block = center.clone().add(x, y, z).getBlock();
                    Material type = block.getType();
                    
                    // Don't remove natural terrain or the grave structure itself
                    if (type == Material.DIRT || type == Material.GRASS_BLOCK || 
                        type == Material.STONE || type == Material.NETHER_BRICKS ||
                        type == Material.AIR || type == Material.CAVE_AIR) {
                        continue;
                    }
                    
                    // Remove player-placed blocks
                    block.setType(Material.AIR);
                }
            }
        }
    }

    private void spawnWave(GraveData graveData) {
        // v3.2.1 - Never spawn for a grave that was closed (/halloween off, completed) or while Halloween is off
        if (!isGraveActive(graveData) || !plugin.getData().isGlobalEnabled()) {
            return;
        }
        int wave = graveData.getCurrentWave();
        
        // v3.0.8 - Broadcast wave announcement from messages.yml
        String waveMsg;
        if (wave == 1) {
            waveMsg = plugin.getMessages().getRandomMessage("graveFirstWaveStart", "").replace("%TOTAL_WAVES%", String.valueOf(graveData.getTotalWaves()));
        } else {
            waveMsg = plugin.getMessages().getRandomMessage("graveWaveStart", "")
                .replace("%WAVE%", String.valueOf(wave))
                .replace("%TOTAL_WAVES%", String.valueOf(graveData.getTotalWaves()));
        }
        plugin.broadcastAll(waveMsg);
        
        // Play deep horn sound for nearby players (±20 blocks)
        Location graveLocation = graveData.getStructure().getCoffinLocation();
        playHornForNearbyPlayers(graveLocation, 20);
        // v3.4.0 - title + wave bar
        plugin.getVisuals().titleNearby(graveLocation, 30, "wave_start", "titleWaveStart",
            "%WAVE%", String.valueOf(wave), "%TOTAL_WAVES%", String.valueOf(graveData.getTotalWaves()));
        
        spawnGraveMobs(graveData, wave);
        
        // v1.7.2 - Start wave timeout
        startWaveTimeout(graveData);
    }
    
    /**
     * v3.4.0 - Create/refresh the grave wave bar: "Wave 2/3 · 5 mobs left · 1:30".
     * Progress = remaining time of the wave. Audience = eligible players within 30 blocks + participants.
     */
    private void updateWaveBar(GraveData graveData) {
        if (graveData.isClosed() || !plugin.getVisuals().bossBarsEnabled()) return;
        org.bukkit.boss.BossBar bar = graveData.getWaveBar();
        if (bar == null) {
            bar = plugin.getVisuals().newBar("", org.bukkit.boss.BarColor.RED, org.bukkit.boss.BarStyle.SEGMENTED_6);
            graveData.setWaveBar(bar);
        }
        long elapsed = (System.currentTimeMillis() - graveData.getWaveStartTime()) / 1000L;
        long remaining = Math.max(0, graveData.getWaveTimeoutSeconds() - elapsed);
        double progress = graveData.getWaveTimeoutSeconds() > 0 ? Math.max(0.0, Math.min(1.0, (double) remaining / graveData.getWaveTimeoutSeconds())) : 1.0;
        bar.setProgress(progress);
        bar.setTitle(plugin.getMessages().get("bossBarGraveWave",
            "%WAVE%", String.valueOf(graveData.getCurrentWave()),
            "%TOTAL_WAVES%", String.valueOf(graveData.getTotalWaves()),
            "%MOBS_LEFT%", String.valueOf(graveData.getRemainingMobs()),
            "%TIME%", String.format("%d:%02d", remaining / 60, remaining % 60)));
        plugin.getVisuals().updateAudience(bar, graveData.getStructure().getCoffinLocation(), 30, graveData.getKillCounts().keySet());
    }

    /** v3.4.0 - 1 s countdown refresh of the wave bar, tracked so close() cancels it. */
    private void startWaveBarTicker(GraveData graveData) {
        if (!plugin.getVisuals().bossBarsEnabled() || graveData.isBarTickerStarted()) return;
        graveData.setBarTickerStarted(true);
        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isGraveActive(graveData)) return;
            updateWaveBar(graveData);
        }, 20L, 20L).getTaskId();
        graveData.trackTask(taskId);
    }

    private void playHornForNearbyPlayers(Location location, double radius) {
        World world = location.getWorld();
        if (world == null) return;
        
        // Find all players within radius
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distance(location) <= radius) {
                // Play dramatic wither spawn sound (deep and ominous)
                player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.8f);
            }
        }
    }

    private void spawnGraveMobs(GraveData graveData, int wave) {
        GraveStructure structure = graveData.getStructure();
        Location spawnLoc = structure.getCoffinLocation().clone().add(1, 1, 0);
        World world = spawnLoc.getWorld();
        
        // v1.7.2 - Get spawn counts from per-wave config
        String wavePath = "grave.spawns.wave" + wave;
        int skeletonCount = plugin.getConfig().getInt(wavePath + ".skeleton", 3);
        int zombieCount = plugin.getConfig().getInt(wavePath + ".zombie", 2);
        int skeletonHorseCount = plugin.getConfig().getInt(wavePath + ".skeleton_horse", 2);
        int zombieHorseCount = plugin.getConfig().getInt(wavePath + ".zombie_horse", 3);
        int blazeCount = plugin.getConfig().getInt(wavePath + ".blaze", 0); // v1.7.2 - Blazes instead of phantoms
        
        List<LivingEntity> mobs = new ArrayList<>();
        
        // Spawn skeletons
        for (int i = 0; i < skeletonCount; i++) {
            Skeleton skeleton = (Skeleton) world.spawnEntity(spawnLoc, EntityType.SKELETON);
            setupGraveMob(skeleton, graveData, wave);
            mobs.add(skeleton);
        }
        
        // Spawn zombies
        for (int i = 0; i < zombieCount; i++) {
            Zombie zombie = (Zombie) world.spawnEntity(spawnLoc, EntityType.ZOMBIE);
            setupGraveMob(zombie, graveData, wave);
            mobs.add(zombie);
        }
        
        // Spawn skeleton riders
        for (int i = 0; i < skeletonHorseCount; i++) {
            SkeletonHorse horse = (SkeletonHorse) world.spawnEntity(spawnLoc, EntityType.SKELETON_HORSE);
            Skeleton rider = (Skeleton) world.spawnEntity(spawnLoc, EntityType.SKELETON);
            horse.addPassenger(rider);
            setupGraveMob(horse, graveData, wave);
            setupGraveMob(rider, graveData, wave);
            // Link rider to horse for death synchronization
            graveData.linkRiderToHorse(rider.getUniqueId(), horse.getUniqueId());
            mobs.add(horse);
            mobs.add(rider);
        }
        
        // Spawn zombie riders
        for (int i = 0; i < zombieHorseCount; i++) {
            ZombieHorse horse = (ZombieHorse) world.spawnEntity(spawnLoc, EntityType.ZOMBIE_HORSE);
            Zombie rider = (Zombie) world.spawnEntity(spawnLoc, EntityType.ZOMBIE);
            horse.addPassenger(rider);
            setupGraveMob(horse, graveData, wave);
            setupGraveMob(rider, graveData, wave);
            // Link rider to horse for death synchronization
            graveData.linkRiderToHorse(rider.getUniqueId(), horse.getUniqueId());
            mobs.add(horse);
            mobs.add(rider);
        }
        
        // v1.7.2 - Spawn blazes (replaced phantoms)
        for (int i = 0; i < blazeCount; i++) {
            Blaze blaze = (Blaze) world.spawnEntity(spawnLoc.clone().add(0, 2, 0), EntityType.BLAZE);
            setupGraveMob(blaze, graveData, wave);
            mobs.add(blaze);
        }
        
        graveData.setMobs(mobs);
        
        // v1.7.2 - Track total mobs for timeout percentage calculation
        graveData.setTotalMobsInWave(mobs.size());
        updateWaveBar(graveData); // v3.4.0
        
        // ANTI-CHEESE: Teleport 10 random mobs upward to bypass roofs
        teleportMobsAboveRoof(mobs, spawnLoc);
        
        // Play spawn sound
        world.playSound(spawnLoc, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.0f, 0.7f);
        
    }
    
    private void teleportMobsAboveRoof(List<LivingEntity> mobs, Location originalSpawn) {
        // Shuffle mobs and pick first 10 to teleport upward
        List<LivingEntity> shuffled = new ArrayList<>(mobs);
        Collections.shuffle(shuffled);
        
        int teleportCount = Math.min(10, shuffled.size());
        Random random = new Random();
        
        for (int i = 0; i < teleportCount; i++) {
            LivingEntity mob = shuffled.get(i);
            
            // Random X/Z offset within ±5 blocks
            double offsetX = (random.nextDouble() - 0.5) * 10; // -5 to +5
            double offsetZ = (random.nextDouble() - 0.5) * 10; // -5 to +5
            
            // Teleport +5 blocks up and random X/Z
            Location newLoc = originalSpawn.clone().add(offsetX, 5, offsetZ);
            mob.teleport(newLoc);
        }
        
    }

    private void setupGraveMob(LivingEntity entity, GraveData graveData, int wave) {
        // Add pumpkin helmet (except bats)
        if (entity.getEquipment() != null && !(entity instanceof Bat)) {
            entity.getEquipment().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
            entity.getEquipment().setHelmetDropChance(0.0f);
        }
        
        // Make it glow
        entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Integer.MAX_VALUE, 0, false, false));
        
        // Buff stats based on wave
        double waveMultiplier = 1.0 + (wave - 1) * 0.5; // Wave 1=1.0x, Wave 2=1.5x, Wave 3=2.0x
        double healthBoost = plugin.getConfig().getDouble("grave.health_boost", 1.3) * waveMultiplier;
        double damageBoost = plugin.getConfig().getDouble("grave.damage_boost", 1.5) * waveMultiplier;
        double speedBoost = plugin.getConfig().getDouble("grave.speed_boost", 0.2);
        
        if (entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null) {
            entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(
                entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue() * healthBoost
            );
            entity.setHealth(entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        }
        
        if (entity.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE) != null) {
            entity.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).setBaseValue(
                entity.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).getBaseValue() * damageBoost
            );
        }
        
        if (entity.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED) != null) {
            entity.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).setBaseValue(
                entity.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).getBaseValue() + speedBoost
            );
        }
        
        // Mark as grave mob (no custom name to avoid console spam)
        entity.setPersistent(true);
        
        // Store in grave data
        graveData.addMobUUID(entity.getUniqueId());
    }

    @EventHandler
    public void onGraveMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        
        // Find grave data for this mob
        GraveData graveData = null;
        for (GraveData data : activeGraves.values()) {
            if (data.containsMob(entity.getUniqueId())) {
                graveData = data;
                break;
            }
        }
        
        if (graveData == null) return;
        
        // HORSE-RIDER SYNCHRONIZATION: Kill horse when rider dies
        UUID linkedHorseUUID = graveData.getLinkedHorse(entity.getUniqueId());
        if (linkedHorseUUID != null) {
            // This entity is a rider, kill its horse too
            for (Entity worldEntity : entity.getWorld().getEntities()) {
                if (worldEntity.getUniqueId().equals(linkedHorseUUID) && worldEntity instanceof LivingEntity) {
                    LivingEntity horse = (LivingEntity) worldEntity;
                    horse.damage(999999); // Instantly kill the horse
                    graveData.removeMob(linkedHorseUUID); // Remove horse from tracking
                    break;
                }
            }
        }
        
        // Track killer
        Player killer = entity.getKiller();
        if (killer != null) {
            graveData.addParticipant(killer.getUniqueId());
            plugin.getStats().increment(killer, "halloween_mob_kills"); // v3.4.0
            participantGraves.put(killer.getUniqueId(), graveData);
        }
        
        // CLEAR ALL DROPS (no arrows, meat, etc.)
        event.getDrops().clear();
        
        // v3.4.0 - grave.pumpkin_drop_chance / grave.pumpkin_drops_per_mob (the latter existed in config but was never read)
        double pumpkinChance = plugin.getConfig().getDouble("grave.pumpkin_drop_chance", 0.10);
        int pumpkinsPerMob = Math.max(0, plugin.getConfig().getInt("grave.pumpkin_drops_per_mob", 1));
        if (pumpkinsPerMob > 0 && plugin.getRandom().nextDouble() < pumpkinChance) {
            event.getDrops().add(new ItemStack(Material.CARVED_PUMPKIN, pumpkinsPerMob));
        }
        plugin.getCandy().dropFor("grave_mob", killer, event.getDrops()); // v3.4.0
        
        // Keep XP (it's separate from drops)
        int extraXP = plugin.getConfig().getInt("grave.xp_per_mob", 5);
        int totalXP = event.getDroppedExp() + extraXP;
        if (totalXP > 0) {
            event.setDroppedExp(totalXP);
        }
        
        // Remove mob from grave data
        graveData.removeMob(entity.getUniqueId());
        graveData.countKill(); // v3.4.0 - overall completion
        updateWaveBar(graveData); // v3.4.0
        
        // Check if all mobs defeated
        if (graveData.allMobsDefeated()) {
            checkWaveProgression(graveData);
        }
    }

    private void checkWaveProgression(GraveData graveData) {
        // v1.7.2 - Cancel wave timeout when all mobs are defeated
        graveData.cancelWaveTimeout();
        
        int currentWave = graveData.getCurrentWave();
        
        if (graveData.isLastWave()) {
            // Final wave defeated - complete event
            completeGraveEvent(graveData);
        } else {
            // Spawn next wave
            graveData.nextWave();
            
            // v3.0.8 - Announce next wave from messages.yml
            String waveMsg = plugin.getMessages().getRandomMessage("graveWaveCleared", "")
                .replace("%CURRENT_WAVE%", String.valueOf(currentWave))
                .replace("%NEXT_WAVE%", String.valueOf(graveData.getCurrentWave()));
            plugin.broadcastAll(waveMsg);
            
            // Spawn next wave after 5 seconds
            scheduleForGrave(graveData, 100L, () -> spawnWave(graveData));
        }
    }
    
    // v1.7.2 - Wave timeout system
    private void startWaveTimeout(GraveData graveData) {
        int wave = graveData.getCurrentWave();
        int timeoutSeconds = plugin.getConfig().getInt("grave.wave_timeouts.wave" + wave, 120);
        graveData.setWaveTimeoutSeconds(timeoutSeconds); // v3.4.0
        startWaveBarTicker(graveData);
        long timeoutTicks = timeoutSeconds * 20L;
        
        // Store wave start time
        graveData.setWaveStartTime(System.currentTimeMillis());
        
        // Schedule 30-second warning
        int warningSeconds = 30;
        long warningTicks = (timeoutSeconds - warningSeconds) * 20L;
        if (warningTicks > 0) {
            final int warnedWave = wave;
            int warningTaskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // v3.4.0 - only if this wave is still running (the task is also cancelled when the wave is cleared)
                if (isGraveActive(graveData) && graveData.getCurrentWave() == warnedWave && !graveData.allMobsDefeated()) {
                    int remaining = graveData.getRemainingMobs();
                    // v3.0.8 - Use messages.yml for warning
                    String warningMsg = plugin.getMessages().getRandomMessage("graveWaveWarning", "")
                        .replace("%MOBS_LEFT%", String.valueOf(remaining));
                    plugin.broadcastAll(warningMsg);
                }
            }, warningTicks).getTaskId();
            graveData.setWaveWarningTask(warningTaskId);
            graveData.trackTask(warningTaskId);
        }
        
        // Schedule timeout
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Check if wave is still active (v3.2.1 - and the grave was not force-closed)
            if (isGraveActive(graveData) && !graveData.allMobsDefeated()) {
                handleWaveTimeout(graveData);
            }
        }, timeoutTicks).getTaskId();
        
        graveData.setWaveTimeoutTask(taskId);
        graveData.trackTask(taskId);
    }
    
    private void handleWaveTimeout(GraveData graveData) {
        int wave = graveData.getCurrentWave();
        int remaining = graveData.getRemainingMobs();
        double completionPercent = graveData.getCompletionPercentage() * 100;
        
        // v3.0.8 - Broadcast timeout message from messages.yml
        String timeoutMsg = plugin.getMessages().getRandomMessage("graveWaveTimeout", "")
            .replace("%WAVE%", String.valueOf(wave));
        plugin.broadcastAll(timeoutMsg);
        
        // Broadcast timeout details from messages.yml (multiple lines - pick random one)
        String detailMsg = plugin.getMessages().getRandomMessage("graveWaveTimeoutDetails", "")
            .replace("%MOBS_LEFT%", String.valueOf(remaining))
            .replace("%COMPLETION_PERCENT%", String.format("%.1f", completionPercent));
        plugin.broadcastAll(detailMsg);
        
        // Remove all remaining mobs from this wave
        List<UUID> mobsToRemove = new ArrayList<>(graveData.getMobUUIDs());
        for (UUID mobUUID : mobsToRemove) {
            // Find and remove the entity
            for (World world : Bukkit.getWorlds()) {
                for (LivingEntity entity : world.getLivingEntities()) {
                    if (entity.getUniqueId().equals(mobUUID)) {
                        entity.remove();
                        break;
                    }
                }
            }
            graveData.removeMob(mobUUID);
        }
        
        // Progress to next wave with reduced rewards or complete event
        if (graveData.isLastWave()) {
            // v3.4.0 - rewards scale with the OVERALL completion (all waves), not just the last one
            completeGraveEventWithPenalty(graveData, graveData.getOverallCompletion() * 100.0);
        } else {
            graveData.nextWave();
            // v3.3.0 - configurable via messages.yml
            plugin.broadcastAll(plugin.getMessages().get("graveProceedingToWave", "%NEXT_WAVE%", String.valueOf(graveData.getCurrentWave())));
            
            // Spawn next wave after 5 seconds
            scheduleForGrave(graveData, 100L, () -> spawnWave(graveData));
        }
    }
    
    private void completeGraveEventWithPenalty(GraveData graveData, double completionPercent) {
        GraveStructure structure = graveData.getStructure();
        Location rewardLoc = structure.getCoffinLocation().clone().add(1, 1, 0);
        
        // v3.0.8 - Broadcast completion message with penalty note from messages.yml
        String playerName = Bukkit.getOfflinePlayer(graveData.getInitiator()).getName();
        if (playerName == null) playerName = "Unknown";
        
        Player initiator = Bukkit.getPlayer(graveData.getInitiator());
        String completeMsg = plugin.getMessages().getRandomMessage("graveEventCompletedWithPenalty", playerName);
        plugin.broadcastAbout(initiator, completeMsg);
        
        // v3.3.0 - configurable via messages.yml (graveFinalCompletion, %COMPLETION_PERCENT%)
        plugin.broadcastAbout(initiator, plugin.getMessages().get("graveFinalCompletion",
            "%COMPLETION_PERCENT%", String.format("%.1f", completionPercent)));
        
        // Spawn reduced reward chest
        spawnGraveRewardChest(graveData, rewardLoc, completionPercent / 100.0);
        
        // Cleanup
        graveData.close(); // v3.2.1
        activeGraves.remove(structure.getCenterLocation());
        graveData.getKillCounts().keySet().forEach(participantGraves::remove);
    }

    private void completeGraveEvent(GraveData graveData) {
        // v1.7.2 - Cancel any remaining timeout
        graveData.cancelWaveTimeout();
        
        GraveStructure structure = graveData.getStructure();
        Location rewardLoc = structure.getCoffinLocation().clone().add(1, 1, 0);
        
        // v3.0.8 - Broadcast completion message from messages.yml
        String playerName = Bukkit.getOfflinePlayer(graveData.getInitiator()).getName();
        if (playerName == null) playerName = "Unknown";
        Player initiator = Bukkit.getPlayer(graveData.getInitiator());
        String message = plugin.getMessages().getRandomMessage("graveCurseBroken", playerName);
        plugin.broadcastAbout(initiator, message);
        // v3.4.0 - completion title for everyone near the grave
        plugin.getVisuals().titleNearby(structure.getCoffinLocation(), 30, "grave_complete", "titleGraveComplete", "%PLAYER%", playerName);
        
        // Use messages.yml for all waves defeated message
        String allWavesMsg = plugin.getMessages().getRandomMessage("graveAllWavesDefeated", playerName);
        plugin.broadcastAbout(initiator, allWavesMsg);
        
        // v3.0.5 - Calculate actual completion percentage for rewards
        // v3.4.0 - overall across all waves (a wave that timed out earlier lowers the result)
        double completionPercent = graveData.getOverallCompletion() * 100.0;
        if (completionPercent < 99.5) {
            plugin.broadcastAbout(initiator, plugin.getMessages().get("graveFinalCompletion",
                "%COMPLETION_PERCENT%", String.format("%.1f", completionPercent)));
        }
        spawnGraveRewardChest(graveData, rewardLoc, completionPercent / 100.0);
    }
    
    // v1.7.2 - Unified reward chest spawning method with completion multiplier
    private void spawnGraveRewardChest(GraveData graveData, Location rewardLoc, double completionMultiplier) {
        final Location finalRewardLoc = rewardLoc.clone();
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Block chestBlock = finalRewardLoc.getBlock();
            chestBlock.setType(Material.CHEST);
            highlightRewardChest(chestBlock.getLocation()); // v3.4.0 - glowing outline so players find the loot
            
            // Wait another tick for chest to initialize
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Block freshBlock = finalRewardLoc.getBlock();
                
                if (freshBlock.getType() != Material.CHEST) {
                    plugin.getLogger().warning("Failed to place chest - block is: " + freshBlock.getType());
                    return;
                }
                
                // Cast to Chest and get inventory directly
                if (freshBlock.getState() instanceof org.bukkit.block.Chest) {
                    org.bukkit.block.Chest chestState = (org.bukkit.block.Chest) freshBlock.getState();
                    org.bukkit.inventory.Inventory inv = chestState.getInventory();
                    
                    // v3.0.5 - Special case: If less than 50% killed, give only 1 pumpkin
                    if (completionMultiplier < 0.5) {
                        inv.setItem(0, new ItemStack(Material.CARVED_PUMPKIN, 1));
                        plugin.getLogger().info("Grave completion < 50% - giving only 1 pumpkin as penalty");
                        // v3.0.8 - Use messages.yml for incomplete defense
                        String incompleteMsg = plugin.getMessages().getRandomMessage("graveIncompleteDefense", "");
                        plugin.broadcastAll(incompleteMsg);
                    } else {
                        // v3.4.0 - shared reward service: items -> chest, money split by kill share,
                        // commands for every participant, completion multiplier applied to amounts
                        plugin.getRewards().give(plugin.getConfig().getMapList("grave.rewards"),
                            new RewardService.Context("grave.rewards")
                                .forParticipants(new java.util.HashMap<>(graveData.getKillCounts()))
                                .multiplier(completionMultiplier)
                                .items(inv::addItem));
                        int candy = plugin.getCandy().roll("grave_complete");
                        if (candy > 0) inv.addItem(plugin.getCandy().create(candy));
                    }
                    
                    plugin.getLogger().info("Successfully filled chest at " + finalRewardLoc + 
                        String.format(" (%.0f%% completion)", completionMultiplier * 100));
                }
            }, 2L);
        }, 5L);
        
        // Award XP to participants
        distributeParticipantXP(graveData);
        
        // v3.4.0 - statistics + Discord
        for (Map.Entry<UUID, Integer> entry : graveData.getKillCounts().entrySet()) {
            Player participant = Bukkit.getPlayer(entry.getKey());
            if (participant != null) {
                plugin.getStats().increment(participant, "graves_completed");
                plugin.getStats().recordMax(participant, "grave_mvp", entry.getValue());
            }
        }
        Player initiatorPlayer = Bukkit.getPlayer(graveData.getInitiator());
        plugin.getDiscord().sendGraveComplete(initiatorPlayer != null ? initiatorPlayer.getName() : plugin.getStats().nameOf(graveData.getInitiator()),
            completionMultiplier * 100.0);
        
        // Remove from active graves
        graveData.close(); // v3.2.1 - cancel any pending warning/timeout tasks
        activeGraves.remove(graveData.getStructure().getCenterLocation());
        
        // Remove participants
        graveData.getKillCounts().keySet().forEach(participantGraves::remove);
        
        // Play completion sound
        finalRewardLoc.getWorld().playSound(finalRewardLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        finalRewardLoc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, finalRewardLoc.clone().add(0.5, 0.5, 0.5), 50, 0.5, 0.5, 0.5, 0.1);
    }

    private void distributeParticipantXP(GraveData graveData) {
        Map<UUID, Integer> killCounts = graveData.getKillCounts();
        if (killCounts.isEmpty()) return;
        
        int totalKills = killCounts.values().stream().mapToInt(Integer::intValue).sum();
        int totalXP = 10 * 7; // 10 levels worth at level 1 (70 XP)
        
        for (Map.Entry<UUID, Integer> entry : killCounts.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            
            double percentage = (double) entry.getValue() / totalKills;
            int xpReward = (int) (totalXP * percentage);
            
            player.giveExp(xpReward);
            // v3.0.8 - Use messages.yml for XP reward message
            String xpMsg = plugin.getMessages().getRandomMessage("graveXPReward", player.getName())
                .replace("%XP%", String.valueOf(xpReward))
                .replace("%CONTRIBUTION%", String.format("%.1f", percentage * 100));
            player.sendMessage(xpMsg);
        }
    }

    /* =========================
       v3.4.0 - Reward chest highlight
       An invisible, glowing, AI-less shulker inside the chest block draws a full-block outline visible through
       terrain (the classic "glowing block" trick). Removed when the chest is opened, after grave.chest_glow.minutes,
       or by cleanup.
       ========================= */
    private NamespacedKey chestMarkerKey; // initialised lazily (plugin field is assigned in the constructor)
    private final Map<UUID, Location> chestMarkers = new HashMap<>(); // marker entity -> chest block

    private NamespacedKey chestMarkerKey() {
        if (chestMarkerKey == null) chestMarkerKey = new NamespacedKey(plugin, "grave_chest_marker");
        return chestMarkerKey;
    }

    private void highlightRewardChest(Location chestLoc) {
        if (!plugin.getConfig().getBoolean("grave.chest_glow.enabled", true)) return;
        World world = chestLoc.getWorld();
        if (world == null) return;
        Location center = chestLoc.getBlock().getLocation().add(0.5, 0.0, 0.5);
        Shulker marker = world.spawn(center, Shulker.class, s -> {
            s.setAI(false);
            s.setInvisible(true);
            s.setInvulnerable(true);
            s.setSilent(true);
            s.setGlowing(true);
            s.setPersistent(false);
            s.setCollidable(false);
            s.setPeek(0f);
            s.setCustomNameVisible(false);
            s.getPersistentDataContainer().set(chestMarkerKey(), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        });
        chestMarkers.put(marker.getUniqueId(), chestLoc.getBlock().getLocation());
        int minutes = Math.max(1, plugin.getConfig().getInt("grave.chest_glow.minutes", 15));
        UUID id = marker.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> removeChestMarker(id), minutes * 60L * 20L);
    }

    private void removeChestMarker(UUID markerId) {
        chestMarkers.remove(markerId);
        Entity e = Bukkit.getEntity(markerId);
        if (e != null) e.remove();
    }

    /** Remove the highlight when somebody opens (or breaks) the chest. */
    @EventHandler
    public void onRewardChestOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (chestMarkers.isEmpty()) return;
        Location loc = event.getInventory().getLocation();
        if (loc == null) return;
        Location block = loc.getBlock().getLocation();
        for (Map.Entry<UUID, Location> e : new ArrayList<>(chestMarkers.entrySet())) {
            if (e.getValue().getWorld().equals(block.getWorld()) && e.getValue().distanceSquared(block) < 1) removeChestMarker(e.getKey());
        }
    }

    @EventHandler
    public void onRewardChestBreak(org.bukkit.event.block.BlockBreakEvent event) {
        if (chestMarkers.isEmpty()) return;
        Location block = event.getBlock().getLocation();
        for (Map.Entry<UUID, Location> e : new ArrayList<>(chestMarkers.entrySet())) {
            if (e.getValue().equals(block)) removeChestMarker(e.getKey());
        }
    }

    /**
     * The marker entity sits inside the chest block, so clicks hit it before the block.
     * Right-click: open the chest for the player and drop the highlight. Left-click: just drop the highlight
     * (the next hit reaches the chest normally).
     */
    @EventHandler
    public void onMarkerInteract(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        Location chestLoc = chestMarkers.get(event.getRightClicked().getUniqueId());
        if (chestLoc == null) return;
        event.setCancelled(true);
        removeChestMarker(event.getRightClicked().getUniqueId());
        Block block = chestLoc.getBlock();
        if (block.getState() instanceof org.bukkit.block.Chest) {
            event.getPlayer().openInventory(((org.bukkit.block.Chest) block.getState()).getInventory());
        }
    }

    @EventHandler
    public void onMarkerDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!chestMarkers.containsKey(event.getEntity().getUniqueId())) return;
        event.setCancelled(true);
        removeChestMarker(event.getEntity().getUniqueId()); // a hit removes the highlight so the chest can be broken
    }

    /** Remove every marker (called from despawnAllGraves / cleanup); also catches markers from a previous run. */
    public void removeAllChestMarkers() {
        for (UUID id : new ArrayList<>(chestMarkers.keySet())) removeChestMarker(id);
        for (World world : Bukkit.getWorlds()) {
            for (Shulker s : world.getEntitiesByClass(Shulker.class)) {
                if (s.getPersistentDataContainer().has(chestMarkerKey(), org.bukkit.persistence.PersistentDataType.BYTE)) s.remove();
            }
        }
    }

    public void despawnAllGraves() {
        removeAllChestMarkers(); // v3.4.0
        for (GraveData graveData : activeGraves.values()) {
            // v3.2.1 - Cancel pending wave spawns / timeouts / warnings / animation first.
            // Without this the wave timeout fired later and spawned the next wave after /halloween off.
            graveData.close();
            
            // Remove all mobs
            for (UUID mobUUID : new ArrayList<>(graveData.getMobUUIDs())) {
                Entity entity = Bukkit.getEntity(mobUUID);
                if (entity != null) {
                    entity.remove();
                }
                graveData.removeMob(mobUUID);
            }
        }
        activeGraves.clear();
        participantGraves.clear();
    }

    // Inner classes for data management
    private static class GraveStructure {
        private final Location dirtLocation;
        private final Location crossLocation;
        private final Location coffinLocation;
        private final Location signLocation;
        private final Location candleLocation;
        private final boolean crossXAxis; // true = X-axis arms, false = Z-axis arms
        private final boolean dirtRowXAxis; // v1.7.1 - true = dirt blocks are on X-axis, false = Z-axis

        public GraveStructure(Location dirtLocation, Location crossLocation, Location coffinLocation, 
                             Location signLocation, Location candleLocation, boolean crossXAxis, boolean dirtRowXAxis) {
            this.dirtLocation = dirtLocation;
            this.crossLocation = crossLocation;
            this.coffinLocation = coffinLocation;
            this.signLocation = signLocation;
            this.candleLocation = candleLocation;
            this.crossXAxis = crossXAxis;
            this.dirtRowXAxis = dirtRowXAxis;
        }

        public Location getDirtLocation() { return dirtLocation; }
        public Location getCrossLocation() { return crossLocation; }
        public Location getCoffinLocation() { return coffinLocation; }
        public Location getSignLocation() { return signLocation; }
        public Location getCandleLocation() { return candleLocation; }
        public boolean isCrossXAxis() { return crossXAxis; }
        public boolean isDirtRowXAxis() { return dirtRowXAxis; } // v1.7.1
        public Location getCenterLocation() { 
            // v1.7.1 - Use correct axis for center calculation
            if (dirtRowXAxis) {
                return dirtLocation.clone().add(1, 0, 0);
            } else {
                return dirtLocation.clone().add(0, 0, 1);
            }
        }
    }

    private static class GraveData {
        private final GraveStructure structure;
        private final UUID initiator;
        private final List<LivingEntity> mobs = new ArrayList<>();
        private final Set<UUID> mobUUIDs = new HashSet<>();
        private final Map<UUID, Integer> killCounts = new HashMap<>();
        private final Map<UUID, UUID> riderToHorse = new HashMap<>(); // rider UUID -> horse UUID
        private int currentWave = 1;
        private static final int TOTAL_WAVES = 3;
        private int waveTimeoutTaskId = -1; // v1.7.2 - Track timeout task
        private final Set<Integer> scheduledTaskIds = new HashSet<>(); // v3.2.1 - Every task scheduled for this grave
        private boolean closed = false; // v3.2.1 - Set once the event is over or force-closed (/halloween off)
        private long waveStartTime = 0; // v1.7.2 - Track when wave started
        private int totalMobsInWave = 0; // v1.7.2 - Track total mobs for reward calculation
        private org.bukkit.boss.BossBar waveBar; // v3.4.0 - wave progress bar (may be null)
        private int waveTimeoutSeconds = 0; // v3.4.0 - for the countdown in the bar
        private boolean barTickerStarted = false; // v3.4.0
        public boolean isBarTickerStarted() { return barTickerStarted; }
        public void setBarTickerStarted(boolean b) { this.barTickerStarted = b; }
        public org.bukkit.boss.BossBar getWaveBar() { return waveBar; }
        public void setWaveBar(org.bukkit.boss.BossBar bar) { this.waveBar = bar; }
        public int getWaveTimeoutSeconds() { return waveTimeoutSeconds; }
        public void setWaveTimeoutSeconds(int s) { this.waveTimeoutSeconds = s; }

        public GraveData(GraveStructure structure, Player initiator) {
            this.structure = structure;
            this.initiator = initiator.getUniqueId();
        }
        
        public int getCurrentWave() { return currentWave; }
        public void nextWave() { currentWave++; }
        public boolean isLastWave() { return currentWave >= TOTAL_WAVES; }
        public int getTotalWaves() { return TOTAL_WAVES; }

        public GraveStructure getStructure() { return structure; }
        public UUID getInitiator() { return initiator; }
        
        public void setMobs(List<LivingEntity> mobs) {
            this.mobs.addAll(mobs);
            for (LivingEntity mob : mobs) {
                mobUUIDs.add(mob.getUniqueId());
            }
            totalSpawned += mobs.size(); // v3.4.0 - overall completion
        }

        // v3.4.0 - overall completion across all waves (killed / spawned); timed-out mobs are removed, not killed
        private int totalSpawned = 0;
        private int totalKilled = 0;
        public void countKill() { totalKilled++; }
        public int getTotalSpawned() { return totalSpawned; }
        public int getTotalKilled() { return totalKilled; }
        /** Overall completion 0.0-1.0 across every wave so far. */
        public double getOverallCompletion() {
            if (totalSpawned == 0) return 1.0;
            return Math.max(0.0, Math.min(1.0, (double) totalKilled / totalSpawned));
        }
        
        public void addMobUUID(UUID uuid) { mobUUIDs.add(uuid); }
        public Set<UUID> getMobUUIDs() { return mobUUIDs; }
        
        public boolean containsMob(UUID uuid) { return mobUUIDs.contains(uuid); }
        
        public void removeMob(UUID uuid) { mobUUIDs.remove(uuid); }
        
        public boolean allMobsDefeated() { return mobUUIDs.isEmpty(); }
        
        public void addParticipant(UUID playerUUID) {
            killCounts.put(playerUUID, killCounts.getOrDefault(playerUUID, 0) + 1);
        }
        
        public Map<UUID, Integer> getKillCounts() { return killCounts; }
        
        // Rider-Horse linking for death synchronization
        public void linkRiderToHorse(UUID riderUUID, UUID horseUUID) {
            riderToHorse.put(riderUUID, horseUUID);
        }
        
        public UUID getLinkedHorse(UUID riderUUID) {
            return riderToHorse.get(riderUUID);
        }
        
        // v1.7.2 - Wave timeout methods
        public void setWaveTimeoutTask(int taskId) { this.waveTimeoutTaskId = taskId; }
        public int getWaveTimeoutTask() { return waveTimeoutTaskId; }
        public void cancelWaveTimeout() {
            if (waveTimeoutTaskId != -1) {
                Bukkit.getScheduler().cancelTask(waveTimeoutTaskId);
                scheduledTaskIds.remove(waveTimeoutTaskId);
                waveTimeoutTaskId = -1;
            }
            // v3.4.0 - the 30 s warning belongs to the same wave; before, it survived a cleared wave and fired
            // during the next one ("30 seconds remaining" with the next wave's mob count)
            if (waveWarningTaskId != -1) {
                Bukkit.getScheduler().cancelTask(waveWarningTaskId);
                scheduledTaskIds.remove(waveWarningTaskId);
                waveWarningTaskId = -1;
            }
        }
        private int waveWarningTaskId = -1; // v3.4.0
        public void setWaveWarningTask(int taskId) { this.waveWarningTaskId = taskId; }

        // v3.2.1 - Track every scheduled task so a force-close can cancel them all
        public void trackTask(int taskId) { scheduledTaskIds.add(taskId); }
        public boolean isClosed() { return closed; }
        /** Marks the grave as finished and cancels all pending tasks (wave spawns, timeouts, warnings, animation). */
        public void close() {
            closed = true;
            if (waveBar != null) { waveBar.removeAll(); waveBar = null; } // v3.4.0
            cancelWaveTimeout();
            for (int taskId : scheduledTaskIds) {
                Bukkit.getScheduler().cancelTask(taskId);
            }
            scheduledTaskIds.clear();
        }
        public void setWaveStartTime(long time) { this.waveStartTime = time; }
        public long getWaveStartTime() { return waveStartTime; }
        public void setTotalMobsInWave(int count) { this.totalMobsInWave = count; }
        public int getTotalMobsInWave() { return totalMobsInWave; }
        public int getRemainingMobs() { return mobUUIDs.size(); }
        /** Completion of the CURRENT wave only (used for the wave timeout message). */
        public double getCompletionPercentage() {
            if (totalMobsInWave == 0) return 1.0;
            return 1.0 - ((double) mobUUIDs.size() / totalMobsInWave);
        }
    }
}
