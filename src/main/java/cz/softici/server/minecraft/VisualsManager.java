package cz.softici.server.minecraft;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;

import java.util.*;

/**
 * v3.4.0 - Boss bars and titles (Bukkit API only, works on Spigot and Paper).
 *  - Boss health bars for the Pumpkin Monster / Halloween Boss, shown to eligible players within visuals.boss_bar.radius.
 *  - Grave wave bars are created here and updated by GraveManager.
 *  - Titles for boss spawn, wave start and grave completion (messages.yml "title..." categories, "TITLE|SUBTITLE").
 *
 * config.yml:
 *   visuals: { boss_bar: { enabled, radius }, titles: { enabled, boss_spawn, wave_start, grave_complete } }
 */
public class VisualsManager implements Listener {
    private final HalloweenPlugin plugin;
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private int refreshTaskId = -1;

    public VisualsManager(HalloweenPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean bossBarsEnabled() { return plugin.getConfig().getBoolean("visuals.boss_bar.enabled", true); }
    public double bossBarRadius() { return plugin.getConfig().getDouble("visuals.boss_bar.radius", 40.0); }

    /* ---------- generic bars ---------- */

    public BossBar newBar(String title, BarColor color, BarStyle style) {
        return Bukkit.createBossBar(MessageManager.colorize(title), color, style);
    }

    /** Show the bar to eligible players near a location (plus extra players), hide it from everyone else. */
    public void updateAudience(BossBar bar, Location center, double radius, Collection<UUID> extra) {
        if (bar == null || center == null || center.getWorld() == null) return;
        Set<Player> wanted = new HashSet<>();
        double r2 = radius * radius;
        for (Player p : center.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(center) <= r2 && plugin.isEligible(p)) wanted.add(p);
        }
        if (extra != null) {
            for (UUID id : extra) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline() && !plugin.getData().isDisabled(id)) wanted.add(p);
            }
        }
        for (Player p : new ArrayList<>(bar.getPlayers())) {
            if (!wanted.contains(p)) bar.removePlayer(p);
        }
        for (Player p : wanted) {
            if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
        }
    }

    /* ---------- boss health bars ---------- */

    public void trackBoss(LivingEntity boss, String titleCategory) {
        if (!bossBarsEnabled() || boss == null) return;
        BossBar bar = newBar(plugin.getMessages().get(titleCategory), BarColor.PURPLE, BarStyle.SEGMENTED_10);
        bossBars.put(boss.getUniqueId(), bar);
        updateBoss(boss);
        ensureRefreshTask();
        plugin.getLogger().info("Boss bar '" + org.bukkit.ChatColor.stripColor(bar.getTitle()) + "' shown to " + bar.getPlayers().size() + " player(s) within " + bossBarRadius() + " blocks");
    }

    public void untrackBoss(UUID bossId) {
        BossBar bar = bossBars.remove(bossId);
        if (bar != null) bar.removeAll();
        if (bossBars.isEmpty()) stopRefreshTask();
    }

    public void untrackAllBosses() {
        for (BossBar bar : bossBars.values()) bar.removeAll();
        bossBars.clear();
        stopRefreshTask();
    }

    private void updateBoss(LivingEntity boss) {
        BossBar bar = bossBars.get(boss.getUniqueId());
        if (bar == null) return;
        if (!boss.isValid() || boss.isDead()) { untrackBoss(boss.getUniqueId()); return; }
        double max = boss.getAttribute(Attribute.MAX_HEALTH) != null ? boss.getAttribute(Attribute.MAX_HEALTH).getValue() : boss.getHealth();
        double progress = max > 0 ? Math.max(0.0, Math.min(1.0, boss.getHealth() / max)) : 0.0;
        bar.setProgress(progress);
        updateAudience(bar, boss.getLocation(), bossBarRadius(), null);
    }

    private void ensureRefreshTask() {
        if (refreshTaskId != -1) return;
        refreshTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID id : new ArrayList<>(bossBars.keySet())) {
                org.bukkit.entity.Entity e = Bukkit.getEntity(id);
                if (e instanceof LivingEntity) updateBoss((LivingEntity) e);
                else untrackBoss(id);
            }
        }, 20L, 20L).getTaskId();
    }

    private void stopRefreshTask() {
        if (refreshTaskId != -1) { Bukkit.getScheduler().cancelTask(refreshTaskId); refreshTaskId = -1; }
    }

    @EventHandler
    public void onBossDamage(EntityDamageEvent event) {
        if (!bossBars.containsKey(event.getEntity().getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> { // health is applied after the event
            if (event.getEntity() instanceof LivingEntity) updateBoss((LivingEntity) event.getEntity());
        });
    }

    @EventHandler
    public void onBossHeal(EntityRegainHealthEvent event) {
        if (!bossBars.containsKey(event.getEntity().getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getEntity() instanceof LivingEntity) updateBoss((LivingEntity) event.getEntity());
        });
    }

    /* ---------- titles ---------- */

    /** Send a title from messages.yml ("Title|Subtitle") to one player if visuals.titles.<toggle> is on. */
    public void title(Player player, String toggle, String category, String... placeholders) {
        if (player == null || !plugin.getConfig().getBoolean("visuals.titles.enabled", true)) return;
        if (!plugin.getConfig().getBoolean("visuals.titles." + toggle, true)) return;
        String raw = plugin.getMessages().get(category, placeholders);
        String[] parts = raw.split("\\|", 2);
        String title = parts[0].trim();
        String subtitle = parts.length > 1 ? parts[1].trim() : "";
        player.sendTitle(title, subtitle, 10, 60, 10);
    }

    /** Title to every eligible player within radius of a location. */
    public void titleNearby(Location center, double radius, String toggle, String category, String... placeholders) {
        if (center == null || center.getWorld() == null) return;
        double r2 = radius * radius;
        for (Player p : center.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(center) <= r2 && plugin.isEligible(p)) title(p, toggle, category, placeholders);
        }
    }

    public void shutdown() {
        untrackAllBosses();
    }
}
