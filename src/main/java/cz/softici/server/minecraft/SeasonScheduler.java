package cz.softici.server.minecraft;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * v3.4.0 - Turns Halloween on when the configured season starts and off when it ends.
 * Only reacts to boundary crossings (remembered in data.yml as season-last-state), so admins can still
 * toggle manually inside or outside the season.
 *
 * config.yml:
 *   season: { enabled, start: "MM-DD", end: "MM-DD", countdown_days, timezone }
 */
public class SeasonScheduler {
    private final HalloweenPlugin plugin;

    public SeasonScheduler(HalloweenPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("season.enabled", false);
    }

    private ZoneId zone() {
        String tz = plugin.getConfig().getString("season.timezone", "");
        if (tz != null && !tz.isBlank()) {
            try { return ZoneId.of(tz.trim()); } catch (Exception ignored) { /* fall back to system */ }
        }
        return ZoneId.systemDefault();
    }

    private MonthDay parse(String key, String def) {
        String v = plugin.getConfig().getString(key, def);
        try {
            return MonthDay.parse("--" + v.trim());
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid season date '" + v + "' for " + key + " (expected MM-DD) - using " + def);
            return MonthDay.parse("--" + def);
        }
    }

    public LocalDate today() { return LocalDate.now(zone()); }

    /** Is the date inside the season? Handles seasons that wrap the new year (e.g. 12-20 .. 01-05). */
    public boolean isInSeason(LocalDate date) {
        MonthDay start = parse("season.start", "10-01");
        MonthDay end = parse("season.end", "11-05");
        MonthDay d = MonthDay.from(date);
        if (!start.isAfter(end)) {
            return !d.isBefore(start) && !d.isAfter(end);
        }
        return !d.isBefore(start) || !d.isAfter(end); // wraps year end
    }

    /** Days until the next season start (0 when in season). */
    public long daysUntilStart(LocalDate date) {
        if (isInSeason(date)) return 0;
        MonthDay start = parse("season.start", "10-01");
        LocalDate next = start.atYear(date.getYear());
        if (next.isBefore(date)) next = start.atYear(date.getYear() + 1);
        return ChronoUnit.DAYS.between(date, next);
    }

    /** Called every minute (and once on enable). */
    public void tick() {
        if (!isEnabled()) return;
        boolean inSeason = isInSeason(today());
        Boolean last = plugin.getData().getLastSeasonState();
        if (last != null && last == inSeason) return; // no boundary crossed

        plugin.getData().setLastSeasonState(inSeason);
        if (plugin.getData().isGlobalEnabled() != inSeason) {
            plugin.getLogger().info("🎃 Season scheduler: Halloween " + (inSeason ? "starts" : "ends") + " today - turning it " + (inSeason ? "ON" : "OFF"));
            plugin.setHalloweenActive(inSeason, null);
        }
        if (last != null || inSeason) { // don't announce "season ended" on the very first run outside the season
            plugin.broadcastAll(plugin.getMessages().get(inSeason ? "seasonStarted" : "seasonEnded"));
            plugin.getDiscord().sendSeason(inSeason);
            if (!inSeason) plugin.announceSeasonTop();
        }
        plugin.getData().saveAsync();
    }

    /** Countdown message for a joining player, or null. */
    public String countdownMessage() {
        if (!isEnabled()) return null;
        int countdownDays = plugin.getConfig().getInt("season.countdown_days", 7);
        if (countdownDays <= 0) return null;
        long days = daysUntilStart(today());
        if (days <= 0 || days > countdownDays) return null;
        return plugin.getMessages().get("seasonCountdown", "%DAYS%", String.valueOf(days));
    }
}
