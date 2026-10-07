package cz.softici.server.minecraft;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

public class MessageManager {
    private final HalloweenPlugin plugin;
    private File file;
    private FileConfiguration config;
    private FileConfiguration defaults;  // v3.0.8: Cache defaults for fallback
    private boolean fileBroken = false;  // v3.2.2: messages.yml has a YAML syntax error - never overwrite it
    private String fileError = null;     // v3.2.2: parser message for the admin
    private final Random random = new Random();

    public MessageManager(HalloweenPlugin plugin) {
        this.plugin = plugin;
        createOrLoad();
    }

    private void createOrLoad() {
        file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        
        // v3.0.8: Load defaults from JAR for fallback system
        loadDefaults();
        
        // v3.2.2: Parse the user's file explicitly. YamlConfiguration.loadConfiguration() swallows
        // syntax errors and returns an EMPTY config, which made mergeDefaults() treat every category
        // as missing and overwrite the admin's messages.yml with defaults (no backup).
        if (loadUserFile()) {
            // Merge new default messages without overwriting existing ones
            mergeDefaults();
        }
    }
    
    /**
     * v3.2.2 - Load messages.yml, detecting YAML syntax errors (typos) instead of silently
     * discarding the file. On error the file is left untouched, all lookups fall back to the
     * JAR defaults and saving is disabled until the admin fixes the file and runs /halloween reload.
     *
     * @return true if the file parsed correctly
     */
    private boolean loadUserFile() {
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
            config = loaded;
            fileBroken = false;
            fileError = null;
            return true;
        } catch (InvalidConfigurationException e) {
            fileBroken = true;
            fileError = e.getMessage();
        } catch (IOException e) {
            fileBroken = true;
            fileError = "cannot read file: " + e.getMessage();
        }
        config = new YamlConfiguration(); // empty -> getRandomMessage() falls back to JAR defaults
        plugin.getLogger().severe("⚠️  messages.yml has a syntax error and was NOT loaded (your file is untouched):");
        for (String line : String.valueOf(fileError).split("\\n")) {
            if (!line.isBlank()) plugin.getLogger().severe("   " + line.trim());
        }
        plugin.getLogger().severe("   Using default messages until you fix the typo and run /" + plugin.getConfig().getString("command_prefix", "halloween") + " reload.");
        plugin.getLogger().severe("   Tip: messages containing ':' or '#' or quotes must be wrapped in double quotes.");
        return false;
    }
    
    /** v3.2.2 - True if messages.yml failed to parse and defaults are being used. */
    public boolean isFileBroken() { return fileBroken; }
    public String getFileError() { return fileError; }
    
    /**
     * v3.0.8 - Load default messages from JAR for fallback
     */
    private void loadDefaults() {
        java.io.InputStream defaultStream = null;
        try {
            defaultStream = plugin.getResource("messages.yml");
            if (defaultStream == null) {
                plugin.getLogger().warning("⚠️  Could not load default messages from JAR!");
                return;
            }
            
            defaults = YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(defaultStream, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().warning("⚠️  Failed to load default messages: " + e.getMessage());
        } finally {
            if (defaultStream != null) {
                try {
                    defaultStream.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }
    
    /**
     * v3.0.0 - Smart merge: Adds new messages to END of existing categories
     * Creates timestamped backup if merge fails
     */
    private void mergeDefaults() {
        java.io.InputStream defaultStream = null;
        try {
            // Load defaults from jar
            defaultStream = plugin.getResource("messages.yml");
            if (defaultStream == null) return;
            
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(defaultStream, java.nio.charset.StandardCharsets.UTF_8));
            
            boolean modified = false;
            int newCategories = 0;
            
            // Check each category in defaults
            for (String key : defaults.getKeys(false)) {
                List<String> defaultMessages = defaults.getStringList(key);
                
                if (!config.contains(key)) {
                    // Add entirely new category that doesn't exist
                    config.set(key, defaultMessages);
                    modified = true;
                    newCategories++;
                }
                // v3.0.5 FIX: Do NOT merge messages to existing categories
                // If user has ANY messages in a category, respect their customization completely
                // This prevents unwanted default messages from being added to user's custom messages
            }
            
            // Save if modified
            if (modified) {
                save();
                if (newCategories > 0) {
                    plugin.getLogger().info("✓ Added " + newCategories + " new message categories (user customizations preserved)");
                }
            }
            
        } catch (Exception e) {
            // Create timestamped backup on error
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
            File backupFile = new File(plugin.getDataFolder(), "messages_" + timestamp + ".backup.yml");
            
            try {
                java.nio.file.Files.copy(
                    file.toPath(),
                    backupFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
                plugin.getLogger().warning("⚠️  Failed to merge messages.yml: " + e.getMessage());
                plugin.getLogger().warning("   Backup created: " + backupFile.getName());
                plugin.getLogger().warning("   You can recover your custom messages from this backup");
            } catch (IOException backupError) {
                plugin.getLogger().severe("⚠️  Failed to backup messages.yml: " + backupError.getMessage());
            }
        } finally {
            // Close stream
            if (defaultStream != null) {
                try {
                    defaultStream.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    public String getRandomMessage(String category, String playerName) {
        return get(category, "%PLAYER%", playerName);
    }

    /**
     * v3.0.4 - Enhanced message method with command prefix support
     * v3.0.8 - Added fallback to JAR defaults if message missing from server
     */
    public String getRandomMessage(String category, String playerName, String commandPrefix) {
        return get(category, "%PLAYER%", playerName, "%COMMAND%", commandPrefix);
    }

    /**
     * v3.3.0 - Random message from a category with any number of placeholder/value pairs, e.g.
     * {@code get("cmdSetApplied", "%ACTION%", action, "%MIN%", "5", "%MAX%", "20")}.
     * Placeholders in messages.yml are case-insensitive ({@code %player%} == {@code %PLAYER%}) and
     * {@code &} colour codes / {@code &#RRGGBB} hex colours are translated.
     */
    public String get(String category, String... placeholderValuePairs) {
        List<String> list = config.getStringList(category);
        
        // v3.0.8: If missing from server config, try JAR defaults
        if ((list == null || list.isEmpty()) && defaults != null) {
            list = defaults.getStringList(category);
            if (list != null && !list.isEmpty() && reportedFallbacks.add(category)) {
                plugin.getLogger().info("ℹ️  Message category '" + category + "' loaded from JAR defaults (add to messages.yml to customize)");
            }
        }
        
        // If still missing, return fallback message
        if (list == null || list.isEmpty()) {
            return "§c[Missing message: " + category + " - check messages.yml]";
        }
        
        String msg = list.get(random.nextInt(list.size()));
        
        // v3.3.0 - Normalise placeholder tokens to upper case so %player% and %PLAYER% both work
        // (callers that replace further placeholders afterwards rely on the upper-case form).
        msg = PLACEHOLDER.matcher(msg).replaceAll(m -> "%" + m.group(1).toUpperCase(java.util.Locale.ROOT) + "%");
        
        for (int i = 0; i + 1 < placeholderValuePairs.length; i += 2) {
            String key = placeholderValuePairs[i];
            String value = placeholderValuePairs[i + 1] == null ? "" : placeholderValuePairs[i + 1];
            msg = msg.replace(key.toUpperCase(java.util.Locale.ROOT), value);
        }
        
        return colorize(msg);
    }

    /** Matches %name% style tokens (letters, digits, underscore) - the trailing literal % of "%X%%" is left alone. */
    private static final java.util.regex.Pattern PLACEHOLDER = java.util.regex.Pattern.compile("%([A-Za-z_][A-Za-z0-9_]*)%");
    /** Matches &#RRGGBB hex colours. */
    private static final java.util.regex.Pattern HEX_COLOR = java.util.regex.Pattern.compile("&#([0-9A-Fa-f]{6})");
    private final java.util.Set<String> reportedFallbacks = new java.util.HashSet<>();

    /**
     * v3.3.0 - Translate {@code &} colour/format codes and {@code &#RRGGBB} hex colours to the section-sign
     * form the client understands. Strings that already use {@code §} pass through unchanged.
     */
    public static String colorize(String text) {
        if (text == null || text.indexOf('&') < 0) return text;
        String out = HEX_COLOR.matcher(text).replaceAll(m -> net.md_5.bungee.api.ChatColor.of("#" + m.group(1)).toString());
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', out);
    }

    public void reload() {
        // v3.0.8: Also refresh defaults on reload in case messages.yml needs to merge new categories
        loadDefaults();
        
        // v3.2.2: Reload with syntax-error protection (see loadUserFile)
        if (loadUserFile()) {
            mergeDefaults();
            plugin.getLogger().info("✓ messages.yml reloaded from disk");
        }
    }

    public void save() {
        if (fileBroken) {
            // v3.2.2 - Never overwrite a file we could not parse
            plugin.getLogger().warning("Not saving messages.yml - the file has a syntax error, fix it first.");
            return;
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save messages.yml!");
        }
    }
}
