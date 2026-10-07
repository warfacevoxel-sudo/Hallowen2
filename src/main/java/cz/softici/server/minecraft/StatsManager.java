package cz.softici.server.minecraft;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * v3.4.0 - Per-player Halloween statistics, persisted in stats.yml.
 * Counters are plain longs keyed by stat name; writes set a dirty flag and a periodic task writes a snapshot
 * asynchronously (the live maps are never touched off the main thread).
 */
public class StatsManager {
    /** All known statistics, in display order. Unknown names are still accepted (forward compatibility). */
    public static final List<String> STATS = List.of(
        "scares", "pumpkins_received", "halloween_mob_kills", "bosses_spawned", "bosses_defeated",
        "graves_started", "graves_completed", "grave_mvp", "cakes", "candy_earned", "pumpkin_time");

    private final HalloweenPlugin plugin;
    private final File file;
    private final Map<UUID, Map<String, Long>> stats = new HashMap<>();
    private final Map<UUID, String> lastKnownNames = new HashMap<>();
    private final Object fileLock = new Object();
    private boolean dirty = false;
    private int saveTaskId = -1;

    public StatsManager(HalloweenPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("statistics.enabled", true);
    }

    /* ---------- counting ---------- */

    public void increment(Player player, String stat) { increment(player, stat, 1); }

    public void increment(Player player, String stat, long amount) {
        if (player == null) return;
        lastKnownNames.put(player.getUniqueId(), player.getName());
        increment(player.getUniqueId(), stat, amount);
    }

    public void increment(UUID uuid, String stat, long amount) {
        if (!isEnabled() || uuid == null || amount == 0) return;
        stats.computeIfAbsent(uuid, k -> new HashMap<>()).merge(stat, amount, Long::sum);
        dirty = true;
    }

    /** Set a stat to max(current, value) - used for "best" records like grave_mvp. */
    public void recordMax(Player player, String stat, long value) {
        if (!isEnabled() || player == null) return;
        lastKnownNames.put(player.getUniqueId(), player.getName());
        Map<String, Long> m = stats.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        if (m.getOrDefault(stat, 0L) < value) { m.put(stat, value); dirty = true; }
    }

    public long get(UUID uuid, String stat) {
        Map<String, Long> m = stats.get(uuid);
        return m == null ? 0L : m.getOrDefault(stat, 0L);
    }

    public Map<String, Long> getAll(UUID uuid) {
        Map<String, Long> m = stats.get(uuid);
        return m == null ? Collections.emptyMap() : Collections.unmodifiableMap(m);
    }

    /** Top N players for a stat (value desc), as (uuid, value) pairs. */
    public List<Map.Entry<UUID, Long>> top(String stat, int limit) {
        return stats.entrySet().stream()
            .map(e -> Map.entry(e.getKey(), e.getValue().getOrDefault(stat, 0L)))
            .filter(e -> e.getValue() > 0)
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .limit(Math.max(1, limit))
            .collect(Collectors.toList());
    }

    /** Best-effort display name for a UUID (cached name, then Bukkit, then short uuid). */
    public String nameOf(UUID uuid) {
        String n = lastKnownNames.get(uuid);
        if (n != null) return n;
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() != null ? op.getName() : uuid.toString().substring(0, 8);
    }

    /** Stats a regular player may see about themselves (statistics.player_visible). */
    public List<String> playerVisibleStats() {
        List<String> list = plugin.getConfig().getStringList("statistics.player_visible");
        return list.isEmpty() ? STATS : list;
    }

    public boolean isKnownStat(String stat) {
        return STATS.contains(stat);
    }

    public void resetAll() {
        stats.clear();
        dirty = true;
    }

    /* ---------- persistence ---------- */

    public void load() {
        stats.clear();
        lastKnownNames.clear();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = cfg.getConfigurationSection("players");
        if (players == null) return;
        for (String key : players.getKeys(false)) {
            UUID uuid;
            try { uuid = UUID.fromString(key); } catch (IllegalArgumentException e) { continue; }
            ConfigurationSection sec = players.getConfigurationSection(key);
            if (sec == null) continue;
            Map<String, Long> m = new HashMap<>();
            for (String stat : sec.getKeys(false)) {
                if (stat.equals("name")) { lastKnownNames.put(uuid, sec.getString("name")); continue; }
                m.put(stat, sec.getLong(stat));
            }
            stats.put(uuid, m);
        }
        plugin.getLogger().info("✓ Loaded statistics for " + stats.size() + " player(s)");
    }

    /** Start the periodic (5 min) dirty-flag save. */
    public void startAutoSave() {
        stopAutoSave();
        saveTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (dirty) saveAsync(); }, 20L * 300, 20L * 300).getTaskId();
    }

    public void stopAutoSave() {
        if (saveTaskId != -1) { Bukkit.getScheduler().cancelTask(saveTaskId); saveTaskId = -1; }
    }

    public void saveAsync() {
        if (!Bukkit.isPrimaryThread()) { save(); return; }
        final String yaml = snapshot();
        dirty = false;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(yaml));
    }

    /** Synchronous save (shutdown). */
    public void save() {
        write(snapshot());
        dirty = false;
    }

    private String snapshot() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Long>> e : stats.entrySet()) {
            String base = "players." + e.getKey() + ".";
            String name = lastKnownNames.get(e.getKey());
            if (name != null) cfg.set(base + "name", name);
            for (Map.Entry<String, Long> s : e.getValue().entrySet()) cfg.set(base + s.getKey(), s.getValue());
        }
        return cfg.saveToString();
    }

    private void write(String yaml) {
        synchronized (fileLock) {
            try {
                java.nio.file.Files.writeString(file.toPath(), yaml, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save stats.yml: " + e.getMessage());
            }
        }
    }
}
