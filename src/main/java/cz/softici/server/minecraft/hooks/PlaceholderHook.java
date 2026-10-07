package cz.softici.server.minecraft.hooks;

import cz.softici.server.minecraft.HalloweenPlugin;
import cz.softici.server.minecraft.StatsManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * v3.4.0 - PlaceholderAPI expansion "halloween". Only loaded when PlaceholderAPI is present.
 *   %halloween_<stat>%                    player's own value
 *   %halloween_top_<stat>_<n>_name%       n-th player (1-based) on the leaderboard
 *   %halloween_top_<stat>_<n>_value%
 *   %halloween_season_active%             true/false (global Halloween state)
 *   %halloween_witching_hour%             true/false
 */
class PlaceholderHook extends PlaceholderExpansion {
    private final HalloweenPlugin plugin;

    PlaceholderHook(HalloweenPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String getIdentifier() { return "halloween"; }
    @Override public String getAuthor() { return "Softici s.r.o."; }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String p = params.toLowerCase();
        StatsManager stats = plugin.getStats();
        if (p.equals("season_active")) return String.valueOf(plugin.getData().isGlobalEnabled());
        if (p.equals("witching_hour")) return String.valueOf(plugin.getWitchingHour().isActive(null));
        if (p.startsWith("top_")) {
            // top_<stat>_<n>_<name|value>
            int lastUnderscore = p.lastIndexOf('_');
            if (lastUnderscore < 0) return null;
            String field = p.substring(lastUnderscore + 1);
            String rest = p.substring(4, lastUnderscore);
            int nUnderscore = rest.lastIndexOf('_');
            if (nUnderscore < 0) return null;
            String stat = rest.substring(0, nUnderscore);
            int n;
            try { n = Integer.parseInt(rest.substring(nUnderscore + 1)); } catch (NumberFormatException e) { return null; }
            List<Map.Entry<UUID, Long>> top = stats.top(stat, n);
            if (top.size() < n || n < 1) return field.equals("value") ? "0" : "-";
            Map.Entry<UUID, Long> e = top.get(n - 1);
            return field.equals("value") ? String.valueOf(e.getValue()) : stats.nameOf(e.getKey());
        }
        if (player == null) return null;
        if (stats.isKnownStat(p)) return String.valueOf(stats.get(player.getUniqueId(), p));
        return null;
    }
}
