package cz.softici.server.minecraft;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PlayerDataManager {
    private final HalloweenPlugin plugin;
    private File file;
    private FileConfiguration config;

    private boolean globalEnabled;
    private final Set<UUID> disabledPlayers = new HashSet<>();
    private final Map<UUID, Integer> pumpkinCounts = new HashMap<>();
    private final Set<UUID> pumpkinPranked = new HashSet<>();
    private final Set<UUID> helmetRewarded = new HashSet<>();
    private final Map<UUID, Integer> helmetRewardTier = new HashMap<>(); // Track reward progression
    private final Map<UUID, Long> lastHelmetReward = new HashMap<>(); // Track last reward time
    private final Map<UUID, Long> lastPumpkinJoin = new HashMap<>(); // Track last pumpkin received on join (30min cooldown)
    private final Map<UUID, Long> lastZombieBossSpawn = new HashMap<>(); // Track last zombie boss spawn
    private final Map<UUID, Long> lastSkeletonBossSpawn = new HashMap<>(); // Track last skeleton boss spawn
    private final Map<UUID, Long> lastGraveActivation = new HashMap<>(); // Track last grave activation

    // intervals
    private final Map<String, int[]> intervals = new HashMap<>();

    public PlayerDataManager(HalloweenPlugin plugin) {
        this.plugin = plugin;
    }

    /* =========================
       Load & Save
       ========================= */
    public void load() {
        file = new File(plugin.getDataFolder(), "data.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create data.yml!");
            }
        }
        config = YamlConfiguration.loadConfiguration(file);

        globalEnabled = config.getBoolean("global-enabled", true);
        lastSeasonState = config.contains("season-last-state") ? config.getBoolean("season-last-state") : null;

        disabledPlayers.clear();
        for (String s : config.getStringList("disabled")) {
            try {
                disabledPlayers.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {}
        }

        pumpkinCounts.clear();
        if (config.isConfigurationSection("pumpkin-counts")) {
            for (String key : config.getConfigurationSection("pumpkin-counts").getKeys(false)) {
                pumpkinCounts.put(UUID.fromString(key), config.getInt("pumpkin-counts." + key, 0));
            }
        }

        pumpkinPranked.clear();
        for (String s : config.getStringList("pumpkin-pranked")) {
            try {
                pumpkinPranked.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {}
        }

        helmetRewarded.clear();
        for (String s : config.getStringList("helmet-rewarded")) {
            try {
                helmetRewarded.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {}
        }

        helmetRewardTier.clear();
        if (config.isConfigurationSection("helmet-reward-tier")) {
            for (String key : config.getConfigurationSection("helmet-reward-tier").getKeys(false)) {
                helmetRewardTier.put(UUID.fromString(key), config.getInt("helmet-reward-tier." + key, 0));
            }
        }

        lastHelmetReward.clear();
        if (config.isConfigurationSection("last-helmet-reward")) {
            for (String key : config.getConfigurationSection("last-helmet-reward").getKeys(false)) {
                lastHelmetReward.put(UUID.fromString(key), config.getLong("last-helmet-reward." + key, 0));
            }
        }

        lastPumpkinJoin.clear();
        if (config.isConfigurationSection("last-pumpkin-join")) {
            for (String key : config.getConfigurationSection("last-pumpkin-join").getKeys(false)) {
                lastPumpkinJoin.put(UUID.fromString(key), config.getLong("last-pumpkin-join." + key, 0));
            }
        }

        // v3.0.5 - Load cooldown data
        lastZombieBossSpawn.clear();
        if (config.isConfigurationSection("last-zombie-boss-spawn")) {
            for (String key : config.getConfigurationSection("last-zombie-boss-spawn").getKeys(false)) {
                lastZombieBossSpawn.put(UUID.fromString(key), config.getLong("last-zombie-boss-spawn." + key, 0));
            }
        }

        lastSkeletonBossSpawn.clear();
        if (config.isConfigurationSection("last-skeleton-boss-spawn")) {
            for (String key : config.getConfigurationSection("last-skeleton-boss-spawn").getKeys(false)) {
                lastSkeletonBossSpawn.put(UUID.fromString(key), config.getLong("last-skeleton-boss-spawn." + key, 0));
            }
        }

        lastGraveActivation.clear();
        if (config.isConfigurationSection("last-grave-activation")) {
            for (String key : config.getConfigurationSection("last-grave-activation").getKeys(false)) {
                lastGraveActivation.put(UUID.fromString(key), config.getLong("last-grave-activation." + key, 0));
            }
        }

        // Intervals
        loadIntervals("pumpkin", 1, 10);
        loadIntervals("slenderman", 10, 15);
        loadIntervals("fire", 30, 60);
        loadIntervals("lightning", 20, 40);
        loadIntervals("darkness", 10, 50);
        loadIntervals("herobrine", 40, 80); // v1.7.0
    }

    /**
     * Save synchronously (used on shutdown).
     * v3.4.0 - The YAML is built from a snapshot of the in-memory state, never from the live maps.
     */
    public void save() {
        if (file == null) return;
        writeFile(buildSnapshot());
    }

    /**
     * Save without blocking the main thread.
     * v3.4.0 - Snapshot + serialisation happen on the calling (main) thread; only the disk write is async.
     * The previous implementation iterated the live HashMaps from the async thread (race / CME risk).
     */
    public void saveAsync() {
        if (file == null) return;
        if (!Bukkit.isPrimaryThread()) { save(); return; }
        final String yaml = buildSnapshot();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> writeFile(yaml));
    }

    private final Object fileLock = new Object();

    private void writeFile(String yaml) {
        synchronized (fileLock) {
            try {
                java.nio.file.Files.writeString(file.toPath(), yaml, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save data.yml! " + e.getMessage());
            }
        }
    }

    /** Serialise the current state into a YAML string (main thread only). */
    private String buildSnapshot() {
        YamlConfiguration snap = new YamlConfiguration();
        snap.set("global-enabled", globalEnabled);

        List<String> disabledList = new ArrayList<>();
        for (UUID id : disabledPlayers) disabledList.add(id.toString());
        snap.set("disabled", disabledList);

        for (Map.Entry<UUID, Integer> e : pumpkinCounts.entrySet()) snap.set("pumpkin-counts." + e.getKey(), e.getValue());

        List<String> prankList = new ArrayList<>();
        for (UUID id : pumpkinPranked) prankList.add(id.toString());
        snap.set("pumpkin-pranked", prankList);

        List<String> rewardList = new ArrayList<>();
        for (UUID id : helmetRewarded) rewardList.add(id.toString());
        snap.set("helmet-rewarded", rewardList);

        for (Map.Entry<UUID, Integer> e : helmetRewardTier.entrySet()) snap.set("helmet-reward-tier." + e.getKey(), e.getValue());
        for (Map.Entry<UUID, Long> e : lastHelmetReward.entrySet()) snap.set("last-helmet-reward." + e.getKey(), e.getValue());
        for (Map.Entry<UUID, Long> e : lastPumpkinJoin.entrySet()) snap.set("last-pumpkin-join." + e.getKey(), e.getValue());
        // v3.0.5 - cooldown data
        for (Map.Entry<UUID, Long> e : lastZombieBossSpawn.entrySet()) snap.set("last-zombie-boss-spawn." + e.getKey(), e.getValue());
        for (Map.Entry<UUID, Long> e : lastSkeletonBossSpawn.entrySet()) snap.set("last-skeleton-boss-spawn." + e.getKey(), e.getValue());
        for (Map.Entry<UUID, Long> e : lastGraveActivation.entrySet()) snap.set("last-grave-activation." + e.getKey(), e.getValue());
        // v3.3.x+ - season scheduler state (see SeasonScheduler)
        if (lastSeasonState != null) snap.set("season-last-state", lastSeasonState);

        // v3.0.0 - intervals moved to config.yml, but keep this for legacy data.yml support
        for (Map.Entry<String, int[]> entry : intervals.entrySet()) {
            snap.set("events." + entry.getKey() + ".min", entry.getValue()[0]);
            snap.set("events." + entry.getKey() + ".max", entry.getValue()[1]);
        }
        return snap.saveToString();
    }

    /* v3.4.0 - season scheduler remembers the last state it applied (null = never) */
    private Boolean lastSeasonState = null;
    public Boolean getLastSeasonState() { return lastSeasonState; }
    public void setLastSeasonState(Boolean state) { this.lastSeasonState = state; }

    /* =========================
       Global State
       ========================= */
    public boolean isGlobalEnabled() {
        return globalEnabled;
    }

    public void setGlobalEnabled(boolean enabled) {
        this.globalEnabled = enabled;
    }

    /* =========================
       Player Disabled State
       ========================= */
    public boolean isDisabled(UUID id) {
        return disabledPlayers.contains(id);
    }

    public void setDisabled(UUID id, boolean disabled) {
        if (disabled) {
            disabledPlayers.add(id);
        } else {
            disabledPlayers.remove(id);
        }
    }

    /* =========================
       Pumpkin Counts & Easter Egg
       ========================= */
    public int incrementPumpkinCount(UUID id) {
        int newCount = pumpkinCounts.getOrDefault(id, 0) + 1;
        pumpkinCounts.put(id, newCount);
        return newCount;
    }

    public boolean hasPumpkinPrank(UUID id) {
        return pumpkinPranked.contains(id);
    }

    public void setPumpkinPrank(UUID id, boolean prank) {
        if (prank) pumpkinPranked.add(id); else pumpkinPranked.remove(id);
    }

    /* =========================
       Helmet Reward
       ========================= */
    public boolean hasHelmetReward(UUID id) {
        return helmetRewarded.contains(id);
    }

    public void setHelmetReward(UUID id, boolean reward) {
        if (reward) helmetRewarded.add(id); else helmetRewarded.remove(id);
    }

    public int getHelmetRewardTier(UUID id) {
        return helmetRewardTier.getOrDefault(id, 0);
    }

    public void incrementHelmetRewardTier(UUID id) {
        int tier = helmetRewardTier.getOrDefault(id, 0) + 1;
        helmetRewardTier.put(id, tier);
    }

    public long getLastHelmetReward(UUID id) {
        return lastHelmetReward.getOrDefault(id, 0L);
    }

    public void setLastHelmetReward(UUID id, long time) {
        lastHelmetReward.put(id, time);
    }

    public int getRequiredMinutesForTier(int tier) {
        // v3.0.0 - Read from config (configurable rewards)
        int firstReward = plugin.getConfig().getInt("helmet_rewards.first_reward_minutes", 3);
        int increment = plugin.getConfig().getInt("helmet_rewards.increment_minutes", 3);
        int maxMinutes = plugin.getConfig().getInt("helmet_rewards.max_minutes", 30);
        
        if (tier == 0) return firstReward;
        int calculated = firstReward + (tier * increment);
        return Math.min(calculated, maxMinutes);
    }

    /* =========================
       Join Pumpkin Cooldown (v3.0.0 - Configurable)
       ========================= */
    public boolean canReceiveJoinPumpkin(UUID playerId, int cooldownMinutes) {
        if (!lastPumpkinJoin.containsKey(playerId)) {
            return true;
        }
        long lastJoin = lastPumpkinJoin.get(playerId);
        long cooldown = cooldownMinutes * 60 * 1000L; // configurable minutes in milliseconds
        return System.currentTimeMillis() - lastJoin >= cooldown;
    }

    public void setLastPumpkinJoin(UUID playerId) {
        lastPumpkinJoin.put(playerId, System.currentTimeMillis());
    }

    public long getRemainingCooldown(UUID playerId, int cooldownMinutes) {
        if (!lastPumpkinJoin.containsKey(playerId)) {
            return 0;
        }
        long lastJoin = lastPumpkinJoin.get(playerId);
        long cooldown = cooldownMinutes * 60 * 1000L; // configurable minutes
        long elapsed = System.currentTimeMillis() - lastJoin;
        long remaining = cooldown - elapsed;
        return Math.max(0, remaining);
    }

    /* =========================
       Boss & Grave Cooldowns (v3.0.5)
       ========================= */
    
    // Zombie Boss Cooldown
    public boolean canSpawnZombieBoss(UUID playerId, int cooldownMinutes) {
        if (!lastZombieBossSpawn.containsKey(playerId)) {
            return true; // First time spawning
        }
        
        long lastSpawn = lastZombieBossSpawn.get(playerId);
        long cooldown = cooldownMinutes * 60 * 1000L; // minutes to milliseconds
        return System.currentTimeMillis() - lastSpawn >= cooldown;
    }
    
    public void setLastZombieBossSpawn(UUID playerId) {
        lastZombieBossSpawn.put(playerId, System.currentTimeMillis());
    }
    
    public long getRemainingZombieBossCooldown(UUID playerId, int cooldownMinutes) {
        if (!lastZombieBossSpawn.containsKey(playerId)) {
            return 0; // No cooldown if never spawned
        }
        
        long lastSpawn = lastZombieBossSpawn.get(playerId);
        long cooldown = cooldownMinutes * 60 * 1000L;
        long elapsed = System.currentTimeMillis() - lastSpawn;
        long remaining = cooldown - elapsed;
        
        return remaining > 0 ? remaining : 0;
    }
    
    // Skeleton Boss Cooldown
    public boolean canSpawnSkeletonBoss(UUID playerId, int cooldownMinutes) {
        if (!lastSkeletonBossSpawn.containsKey(playerId)) {
            return true; // First time spawning
        }
        
        long lastSpawn = lastSkeletonBossSpawn.get(playerId);
        long cooldown = cooldownMinutes * 60 * 1000L; // minutes to milliseconds
        return System.currentTimeMillis() - lastSpawn >= cooldown;
    }
    
    public void setLastSkeletonBossSpawn(UUID playerId) {
        lastSkeletonBossSpawn.put(playerId, System.currentTimeMillis());
    }
    
    public long getRemainingSkeletonBossCooldown(UUID playerId, int cooldownMinutes) {
        if (!lastSkeletonBossSpawn.containsKey(playerId)) {
            return 0; // No cooldown if never spawned
        }
        
        long lastSpawn = lastSkeletonBossSpawn.get(playerId);
        long cooldown = cooldownMinutes * 60 * 1000L;
        long elapsed = System.currentTimeMillis() - lastSpawn;
        long remaining = cooldown - elapsed;
        
        return remaining > 0 ? remaining : 0;
    }
    
    // Grave Activation Cooldown
    public boolean canActivateGrave(UUID playerId, int cooldownMinutes) {
        if (!lastGraveActivation.containsKey(playerId)) {
            return true; // First time activating
        }
        
        long lastActivation = lastGraveActivation.get(playerId);
        long cooldown = cooldownMinutes * 60 * 1000L; // minutes to milliseconds
        return System.currentTimeMillis() - lastActivation >= cooldown;
    }
    
    public void setLastGraveActivation(UUID playerId) {
        lastGraveActivation.put(playerId, System.currentTimeMillis());
    }
    
    public long getRemainingGraveCooldown(UUID playerId, int cooldownMinutes) {
        if (!lastGraveActivation.containsKey(playerId)) {
            return 0; // No cooldown if never activated
        }
        
        long lastActivation = lastGraveActivation.get(playerId);
        long cooldown = cooldownMinutes * 60 * 1000L;
        long elapsed = System.currentTimeMillis() - lastActivation;
        long remaining = cooldown - elapsed;
        
        return remaining > 0 ? remaining : 0;
    }

    /* =========================
       Reset Functions
       ========================= */
    public void resetPumpkinPranked() {
        pumpkinPranked.clear();
    }

    public void resetHelmetRewarded() {
        helmetRewarded.clear();
        helmetRewardTier.clear();
        lastHelmetReward.clear();
    }

    /** v3.4.0 - Clear boss and grave cooldowns for one player (uuid) or for everyone (null). */
    public void resetCooldowns(UUID uuid) {
        if (uuid == null) {
            lastZombieBossSpawn.clear();
            lastSkeletonBossSpawn.clear();
            lastGraveActivation.clear();
        } else {
            lastZombieBossSpawn.remove(uuid);
            lastSkeletonBossSpawn.remove(uuid);
            lastGraveActivation.remove(uuid);
        }
    }

    public void resetAll() {
        pumpkinPranked.clear();
        helmetRewarded.clear();
        helmetRewardTier.clear();
        lastHelmetReward.clear();
        pumpkinCounts.clear();
        lastPumpkinJoin.clear();
        lastZombieBossSpawn.clear();
        lastSkeletonBossSpawn.clear();
        lastGraveActivation.clear();
    }

    /* =========================
       Intervals
       ========================= */
    // v3.0.0 - Legacy method, intervals now in config.yml events section
    private void loadIntervals(String key, int defMin, int defMax) {
        int min = config.getInt("events." + key + ".min", defMin);
        int max = config.getInt("events." + key + ".max", defMax);
        intervals.put(key, new int[]{min, max});
    }

    public int[] getInterval(String action) {
        return intervals.getOrDefault(action, new int[]{5, 10});
    }

    public void setInterval(String action, int min, int max) {
        intervals.put(action, new int[]{min, max});
    }
}
