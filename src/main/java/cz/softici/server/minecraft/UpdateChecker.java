package cz.softici.server.minecraft;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

/**
 * Update checker for Halloween Plugin using Spiget API
 * Checks for new versions on SpigotMC and notifies admins
 * 
 * @version 1.7.1
 * @since 1.7.1
 */
public class UpdateChecker implements Listener {
    
    private final HalloweenPlugin plugin;
    private final int resourceId = 129197; // Your SpigotMC resource ID
    private final String currentVersion;
    private String latestVersion = null;
    private boolean updateAvailable = false;
    private boolean checkFailed = false;
    
    public UpdateChecker(HalloweenPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }
    
    /**
     * Checks for updates asynchronously
     */
    public void checkForUpdates() {
        if (!plugin.getConfig().getBoolean("update_checker.enabled", true)) {
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                // Spiget API endpoint - /versions/latest returns latest version object
                String apiUrl = "https://api.spiget.org/v2/resources/" + resourceId + "/versions/latest";
                
                @SuppressWarnings("deprecation")
                HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "HalloweenPlugin-UpdateChecker");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    // Parse JSON object - returns single version object
                    // Format: {"downloads": 2, "name": "1.7.0", "rating": {...}, ...}
                    // Note: Spiget API includes spaces after colons
                    String json = response.toString();
                    
                    // Find "name" field (version string) - handle both with and without spaces
                    int nameIndex = json.indexOf("\"name\":");
                    
                    if (nameIndex >= 0) {
                        // Skip past "name": and any whitespace, then find the opening quote
                        int quoteStart = json.indexOf("\"", nameIndex + 7); // 7 = length of "name":
                        
                        if (quoteStart >= 0) {
                            int quoteEnd = json.indexOf("\"", quoteStart + 1);
                            
                            if (quoteEnd > quoteStart) {
                                latestVersion = json.substring(quoteStart + 1, quoteEnd);
                                plugin.getLogger().info("✓ Successfully parsed latest version: " + latestVersion);
                            }
                        }
                    }
                    
                    if (latestVersion == null || latestVersion.isEmpty()) {
                        plugin.getLogger().warning("❌ Could not parse version from API response");
                        plugin.getLogger().warning("JSON received: " + json);
                        checkFailed = true;
                        return;
                    }
                    
                    // Compare versions
                    if (!currentVersion.equals(latestVersion)) {
                        updateAvailable = true;
                        plugin.getLogger().info("╔═══════════════════════════════════════════════════════════╗");
                        plugin.getLogger().info("║  🎃 UPDATE AVAILABLE!                                     ║");
                        plugin.getLogger().info("║  Current: " + String.format("%-48s", currentVersion) + "║");
                        plugin.getLogger().info("║  Latest:  " + String.format("%-48s", latestVersion) + "║");
                        plugin.getLogger().info("║  Download: https://www.spigotmc.org/resources/129197/    ║");
                        plugin.getLogger().info("╚═══════════════════════════════════════════════════════════╝");
                    } else {
                        plugin.getLogger().info("✅ Halloween Plugin is up to date! (v" + currentVersion + ")");
                    }
                } else {
                    checkFailed = true;
                    plugin.getLogger().warning("⚠️ Could not check for updates (HTTP " + responseCode + ")");
                }
                
                connection.disconnect();
                
            } catch (Exception e) {
                checkFailed = true;
                plugin.getLogger().warning("⚠️ Update check failed: " + e.getMessage());
            }
        });
    }
    
    /**
     * Notifies player of available update (for admins only)
     */
    @EventHandler
    public void onAdminJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Only notify admins with update permission
        if (!player.hasPermission("halloween.admin")) {
            return;
        }
        
        // Only notify if update check is enabled
        if (!plugin.getConfig().getBoolean("update_checker.enabled", true)) {
            return;
        }
        
        // Only notify if update is available
        if (!updateAvailable || latestVersion == null) {
            return;
        }
        
        // Delay notification to avoid login spam
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                sendUpdateNotification(player);
            }
        }, 60L); // 3 seconds after join
    }
    
    /**
     * Sends formatted update notification to player
     */
    private void sendUpdateNotification(Player player) {
        // Get message from config (simple string, not from messages.yml)
        String message = plugin.getConfig().getString("update_checker.message", 
            "§6[Halloween] §eNew version available: §a%new_version% §7(current: §c%current_version%§7)\n§eClick to download: %link%");
        
        if (message == null || message.isEmpty()) {
            // Fallback message
            message = "§6[Halloween] §eNew version available: §a%new_version% §7(current: §c%current_version%§7)\n§eClick to download: %link%";
        }
        
        // Replace placeholders (v3.3.0: & colour codes supported in config.yml message)
        message = MessageManager.colorize(message);
        message = message.replace("%current_version%", currentVersion);
        message = message.replace("%new_version%", latestVersion);
        
        // Check if message contains clickable link placeholder
        String resourceUrl = "https://www.spigotmc.org/resources/halloween-plugin.129197/updates";
        
        if (message.contains("%link%")) {
            // Split message by link placeholder to create clickable component
            String[] parts = message.split("%link%");
            
            // Send first part
            if (parts.length > 0) {
                player.sendMessage(parts[0]);
            }
            
            // Send clickable link
            net.md_5.bungee.api.chat.TextComponent linkComponent = new net.md_5.bungee.api.chat.TextComponent("§b§n[Click Here to Download]");
            linkComponent.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, 
                resourceUrl
            ));
            @SuppressWarnings("deprecation")
            net.md_5.bungee.api.chat.HoverEvent hoverEvent = new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.ComponentBuilder("§aOpen in browser\n§7" + resourceUrl).create()
            );
            linkComponent.setHoverEvent(hoverEvent);
            player.spigot().sendMessage(linkComponent);
            
            // Send remaining part
            if (parts.length > 1) {
                player.sendMessage(parts[1]);
            }
        } else {
            // No clickable link, just replace URL text
            message = message.replace("{link}", resourceUrl);
            player.sendMessage(message);
        }
    }
    
    /**
     * Gets the latest version string (may be null if check hasn't completed)
     */
    public String getLatestVersion() {
        return latestVersion;
    }
    
    /**
     * Checks if an update is available
     */
    public boolean isUpdateAvailable() {
        return updateAvailable;
    }
    
    /**
     * Checks if the update check failed
     */
    public boolean hasCheckFailed() {
        return checkFailed;
    }
    
    /**
     * Gets the current version
     */
    public String getCurrentVersion() {
        return currentVersion;
    }
}
