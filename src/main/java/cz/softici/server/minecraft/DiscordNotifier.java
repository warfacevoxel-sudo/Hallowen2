package cz.softici.server.minecraft;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * v3.4.0 - Discord webhook notifications (boss spawn/defeat, grave start/complete, season start/end, top players).
 * Messages come from messages.yml (discord* categories) with colours stripped. Sending is asynchronous and
 * rate-limited to one request every 2 seconds (queued in order).
 *
 * config.yml:
 *   discord: { enabled, webhook_url, username, events: { boss_spawn, boss_defeat, grave_start, grave_complete, season, top_players } }
 */
public class DiscordNotifier {
    private final HalloweenPlugin plugin;
    private final Deque<String> queue = new ArrayDeque<>();
    private long lastSentAt = 0;
    private boolean sending = false;
    private boolean warnedNoUrl = false;

    public DiscordNotifier(HalloweenPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("discord.enabled", false);
    }

    private boolean eventEnabled(String key) {
        return isEnabled() && plugin.getConfig().getBoolean("discord.events." + key, true);
    }

    /* ---------- typed events ---------- */

    public void sendBossSpawn(String bossType, String playerName) {
        if (eventEnabled("boss_spawn")) send(plugin.getMessages().get("discordBossSpawn", "%BOSS_TYPE%", bossType, "%PLAYER%", playerName));
    }

    public void sendBossDefeat(String bossType, String playerName) {
        if (eventEnabled("boss_defeat")) send(plugin.getMessages().get("discordBossDefeat", "%BOSS_TYPE%", bossType, "%PLAYER%", playerName));
    }

    public void sendGraveStart(String playerName) {
        if (eventEnabled("grave_start")) send(plugin.getMessages().get("discordGraveStart", "%PLAYER%", playerName));
    }

    public void sendGraveComplete(String playerName, double completionPercent) {
        if (eventEnabled("grave_complete")) send(plugin.getMessages().get("discordGraveComplete", "%PLAYER%", playerName,
            "%COMPLETION_PERCENT%", String.format("%.0f", completionPercent)));
    }

    public void sendSeason(boolean started) {
        if (eventEnabled("season")) send(plugin.getMessages().get(started ? "discordSeasonStart" : "discordSeasonEnd"));
    }

    /** Leaderboard lines (already formatted) as one message. */
    public void sendTop(String header, java.util.List<String> lines) {
        if (!eventEnabled("top_players")) return;
        StringBuilder sb = new StringBuilder(header);
        for (String l : lines) sb.append('\n').append(l);
        send(sb.toString());
    }

    /* ---------- transport ---------- */

    /** Queue a raw message (colours are stripped). */
    public void send(String message) {
        if (!isEnabled() || message == null || message.isBlank()) return;
        String url = plugin.getConfig().getString("discord.webhook_url", "");
        if (url == null || url.isBlank()) {
            if (!warnedNoUrl) { plugin.getLogger().warning("discord.enabled is true but discord.webhook_url is empty"); warnedNoUrl = true; }
            return;
        }
        String clean = ChatColor.stripColor(MessageManager.colorize(message));
        if (queue.size() < 50) queue.add(clean);
        pump();
    }

    private void pump() {
        if (sending || queue.isEmpty()) return;
        long wait = Math.max(0, 2000 - (System.currentTimeMillis() - lastSentAt));
        sending = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            String content = queue.poll();
            if (content == null) { sending = false; return; }
            String url = plugin.getConfig().getString("discord.webhook_url", "");
            String username = plugin.getConfig().getString("discord.username", "Halloween");
            lastSentAt = System.currentTimeMillis();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                post(url, username, content);
                Bukkit.getScheduler().runTask(plugin, () -> { sending = false; pump(); });
            });
        }, wait / 50 + 1);
    }

    private void post(String url, String username, String content) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("username", username);
            body.addProperty("content", content.length() > 1900 ? content.substring(0, 1900) : content);
            HttpURLConnection con = (HttpURLConnection) URI.create(url).toURL().openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("User-Agent", "HalloweenPlugin-Discord");
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            con.setDoOutput(true);
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = con.getOutputStream()) { os.write(bytes); }
            int code = con.getResponseCode();
            if (code < 200 || code >= 300) {
                plugin.getLogger().warning("Discord webhook returned HTTP " + code);
            }
            con.disconnect();
        } catch (Exception e) {
            plugin.getLogger().warning("Discord webhook failed: " + e.getMessage());
        }
    }
}
