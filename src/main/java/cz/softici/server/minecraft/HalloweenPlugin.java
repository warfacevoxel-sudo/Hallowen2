package cz.softici.server.minecraft;

import cz.softici.server.minecraft.tasks.DarknessTask;
import cz.softici.server.minecraft.tasks.FireTask;
import cz.softici.server.minecraft.tasks.HerobrineTask;
import cz.softici.server.minecraft.tasks.LightningTask;
import cz.softici.server.minecraft.tasks.PumpkinTask;
import cz.softici.server.minecraft.tasks.SlendermanTask;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.InputStream;
import java.util.*;

public class HalloweenPlugin extends JavaPlugin implements Listener {

    /** Version written into config.yml by migrations - bump on every release (see VERSION_UPDATE_CHECKLIST.md). */
    public static final String CONFIG_VERSION = "3.4.0";

    private final Random random = new Random();
    private PlayerDataManager data;
    private MessageManager messages;
    private PumpkinMonsterManager pumpkinMonsterManager;
    private PumpkinWitchManager pumpkinWitchManager;
    private RandomPumpkinMobManager randomPumpkinMobManager;
    private PumpkinVillagerManager pumpkinVillagerManager;
    private GraveManager graveManager;
    private UpdateChecker updateChecker; // v1.7.1
    private BatWitchManager batWitchManager; // v3.0.0
    private cz.softici.server.minecraft.hooks.HookManager hooks; // v3.4.0
    private EventEffects effects; // v3.4.0
    private StatsManager stats; // v3.4.0
    private SeasonScheduler seasonScheduler; // v3.4.0
    private WitchingHour witchingHour; // v3.4.0
    private DiscordNotifier discord; // v3.4.0
    private RewardService rewards; // v3.4.0
    private CandyManager candy; // v3.4.0
    private VisualsManager visuals; // v3.4.0
    // private PumpkinProtectorManager pumpkinProtectorManager; // Disabled for now

    private final Set<UUID> fireProtected = new HashSet<>();
    private final Map<UUID, Long> pumpkinHelmetStart = new HashMap<>();

    // Per-player prank tasks
    private final Map<UUID, PumpkinTask> pumpkinTasks = new HashMap<>();
    private final Map<UUID, SlendermanTask> slendermanTasks = new HashMap<>();
    private final Map<UUID, FireTask> fireTasks = new HashMap<>();
    private final Map<UUID, LightningTask> lightningTasks = new HashMap<>();
    private final Map<UUID, DarknessTask> darknessTasks = new HashMap<>();
    private final Map<UUID, HerobrineTask> herobrineTasks = new HashMap<>();
    
    // v1.6.1 - Command prefix
    private String commandPrefix;

    @Override
    public void onLoad() {
        // v3.4.0 - WorldGuard flags must be registered before WorldGuard enables
        hooks = new cz.softici.server.minecraft.hooks.HookManager(this);
        hooks.registerEarly();
    }

    @Override
    public void onEnable() {
        // v1.6.1 - Check for folder conflicts and warn user
        checkForFolderConflicts();
        if (hooks == null) hooks = new cz.softici.server.minecraft.hooks.HookManager(this);
        
        // Always save defaults first to create/update internal config
        saveDefaultConfig();
        
        // Then reload and merge with defaults
        reloadConfig();
        getConfig().options().copyDefaults(true);
        
        // v1.6.1 - Migrate and clean config
        migrateAndMergeConfig();
        
        // v3.2.2 - Only extract the default messages.yml when missing (MessageManager does the same);
        // an unconditional saveResource() logged a bogus "already exists" warning on every start.
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }
        
        // v1.6.1 - Load command prefix from config
        commandPrefix = getConfig().getString("command_prefix", "halloween");
        if (commandPrefix.isEmpty() || commandPrefix.contains(" ")) {
            getLogger().warning("Invalid command_prefix in config! Using default 'halloween'");
            commandPrefix = "halloween";
        }

        data = new PlayerDataManager(this);
        data.load();
        messages = new MessageManager(this);
        
        // Initialize v1.5.0+ managers
        effects = new EventEffects(this); // v3.4.0
        stats = new StatsManager(this); // v3.4.0
        stats.load();
        stats.startAutoSave();
        seasonScheduler = new SeasonScheduler(this); // v3.4.0
        witchingHour = new WitchingHour(this); // v3.4.0
        discord = new DiscordNotifier(this); // v3.4.0
        rewards = new RewardService(this); // v3.4.0
        candy = new CandyManager(this); // v3.4.0
        visuals = new VisualsManager(this); // v3.4.0
        pumpkinMonsterManager = new PumpkinMonsterManager(this);
        pumpkinWitchManager = new PumpkinWitchManager(this);
        randomPumpkinMobManager = new RandomPumpkinMobManager(this);
        pumpkinVillagerManager = new PumpkinVillagerManager(this);
        graveManager = new GraveManager(this); // v1.6.0
        updateChecker = new UpdateChecker(this); // v1.7.1
        batWitchManager = new BatWitchManager(this); // v3.0.0
        // pumpkinProtectorManager = new PumpkinProtectorManager(this); // Disabled for now

        hooks.enable(); // v3.4.0 - Vault / PlaceholderAPI / WorldGuard

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(pumpkinMonsterManager, this);
        getServer().getPluginManager().registerEvents(pumpkinWitchManager, this);
        getServer().getPluginManager().registerEvents(randomPumpkinMobManager, this);
        getServer().getPluginManager().registerEvents(pumpkinVillagerManager, this);
        getServer().getPluginManager().registerEvents(graveManager, this); // v1.6.0
        getServer().getPluginManager().registerEvents(updateChecker, this); // v1.7.1
        getServer().getPluginManager().registerEvents(batWitchManager, this); // v3.0.0
        getServer().getPluginManager().registerEvents(candy, this); // v3.4.0
        getServer().getPluginManager().registerEvents(visuals, this); // v3.4.0
        // getServer().getPluginManager().registerEvents(pumpkinProtectorManager, this); // Disabled for now
        
        // v1.6.1 - Command registration logic
        if (commandPrefix.equals("halloween") || commandPrefix.equals("hlw")) {
            // Use default /halloween command from plugin.yml
            PluginCommand halloweenCommand = getCommand("halloween");
            if (halloweenCommand != null) {
                halloweenCommand.setExecutor(this);
                halloweenCommand.setTabCompleter(this);
            }
            getLogger().info("✅ Using default commands: /halloween, /hlw");
        } else {
            // Register custom command and UNREGISTER /halloween to avoid conflicts
            try {
                registerCustomCommand(commandPrefix);
                unregisterDefaultCommand("halloween");
                getLogger().info("✅ Registered custom command: /" + commandPrefix);
                getLogger().info("   ⚠️ Unregistered /halloween to avoid conflicts with other plugins");
                getLogger().info("   Use /" + commandPrefix + " help for commands");
            } catch (Exception e) {
                getLogger().warning("⚠️ Could not register custom command /" + commandPrefix);
                getLogger().warning("   Reason: " + e.getMessage());
                getLogger().warning("   Falling back to: /halloween and /hlw");
                // Fallback: register default command
                PluginCommand halloweenCommand = getCommand("halloween");
                if (halloweenCommand != null) {
                    halloweenCommand.setExecutor(this);
                    halloweenCommand.setTabCompleter(this);
                }
            }
        }

        // v1.7.0 - Clean up any leftover Herobrine entities from previous sessions
        cleanupAllHerobrines();

        if (data.isGlobalEnabled()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                // Only start tasks for players who have Halloween enabled
                if (!data.isDisabled(p.getUniqueId())) {
                    startTasksForPlayer(p);
                }
            }
        }

        // Pumpkin buffs refresher every 5s
        Bukkit.getScheduler().runTaskTimer(this, this::applyPumpkinBuffs, 20L, 100L);

        // Helmet reward checker every 20s
        Bukkit.getScheduler().runTaskTimer(this, this::checkHelmetRewards, 20L, 20L * 20);
        // v3.4.0 - existing Pumpkin Traders get the configured trades (worlds are loaded by now)
        Bukkit.getScheduler().runTaskLater(this, () -> pumpkinVillagerManager.refreshAllTrades(), 40L);
        // v3.4.0 - minute timer: season boundaries + witching hour
        Bukkit.getScheduler().runTaskTimer(this, () -> { seasonScheduler.tick(); witchingHour.tick(); }, 100L, 20L * 60);
        
        // v1.7.1 - Check for updates
        updateChecker.checkForUpdates();

        getLogger().info("🎃 Halloween Plugin v" + getDescription().getVersion() + " enabled");
    }

    @Override
    public void onDisable() {
        stopAllTasks();
        // Clean up all Halloween entities before disable
        cleanupAllEntities(false);
        data.save();
        if (stats != null) { stats.stopAutoSave(); stats.save(); } // v3.4.0
        if (visuals != null) visuals.shutdown(); // v3.4.0
        getLogger().info("🎃 Halloween Plugin v" + getDescription().getVersion() + " disabled");
    }
    
    /**
     * v1.6.1 - Register a custom command dynamically using CommandMap
     * This allows /scary, /spooky, etc. to work as actual commands
     */
    private void registerCustomCommand(String commandName) {
        try {
            // Get the CommandMap using reflection
            final java.lang.reflect.Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());
            
            // Create a new command that delegates to our handler
            Command customCommand = new Command(commandName) {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    // Delegate to our main command handler
                    return onCommand(sender, this, label, args);
                }
                
                @Override
                public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                    // Delegate to our tab completer
                    return onTabComplete(sender, this, alias, args);
                }
            };
            
            // Set command properties
            customCommand.setDescription("Halloween plugin command (custom alias)");
            customCommand.setUsage("/" + commandName + " [help|enable|disable|on|off|reload|set|reset|buffs]");
            customCommand.setPermission("halloween.base");
            
            // Register the command
            commandMap.register("halloween", customCommand);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to register command: " + e.getMessage(), e);
        }
    }
    
    /**
     * v1.6.1 - Unregister the default /halloween command to free it for other plugins
     * This is called when user sets a custom command_prefix
     */
    private void unregisterDefaultCommand(String commandName) {
        try {
            // Get the CommandMap using reflection
            final java.lang.reflect.Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());
            
            // Get the knownCommands map from CommandMap
            // Try different field names for compatibility with Paper/Spigot/Purpur
            Map<String, Command> knownCommands = null;
            java.lang.reflect.Field knownCommandsField = null;
            
            // Try "knownCommands" first (Spigot/CraftBukkit)
            try {
                knownCommandsField = commandMap.getClass().getDeclaredField("knownCommands");
            } catch (NoSuchFieldException e1) {
                // Try alternative field names for Paper and forks
                try {
                    knownCommandsField = commandMap.getClass().getSuperclass().getDeclaredField("knownCommands");
                } catch (NoSuchFieldException e2) {
                    // Last resort: scan all fields for a Map<String, Command>
                    for (java.lang.reflect.Field field : commandMap.getClass().getDeclaredFields()) {
                        if (Map.class.isAssignableFrom(field.getType())) {
                            field.setAccessible(true);
                            Object obj = field.get(commandMap);
                            if (obj instanceof Map) {
                                knownCommandsField = field;
                                break;
                            }
                        }
                    }
                }
            }
            
            if (knownCommandsField != null) {
                knownCommandsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, Command> commandsMap = (Map<String, Command>) knownCommandsField.get(commandMap);
                knownCommands = commandsMap;
            }
            
            if (knownCommands == null) {
                getLogger().warning("⚠️ Could not access command map via reflection");
                getLogger().warning("   /halloween may still be registered on this server type");
                return;
            }
            
            // First, unregister the PluginCommand properly
            PluginCommand pluginCommand = getCommand(commandName);
            if (pluginCommand != null) {
                pluginCommand.unregister(commandMap);
            }
            
            // Then forcefully remove ALL traces from knownCommands map
            // We need to remove both the command and plugin-namespaced versions
            knownCommands.remove(commandName);
            knownCommands.remove("halloween:halloween");
            knownCommands.remove("hlw");
            knownCommands.remove("halloween:hlw");
            
            // Also iterate and remove any leftover references
            // This is important because Bukkit may register variations
            knownCommands.entrySet().removeIf(entry -> {
                Command cmd = entry.getValue();
                return cmd instanceof PluginCommand && 
                       ((PluginCommand) cmd).getPlugin().equals(this) &&
                       (entry.getKey().equalsIgnoreCase(commandName) || 
                        entry.getKey().equalsIgnoreCase("hlw") ||
                        entry.getKey().contains("halloween:"));
            });
            
            getLogger().info("✅ Successfully unregistered /" + commandName + " and /hlw");
            
        } catch (Exception e) {
            getLogger().warning("⚠️ Could not fully unregister /" + commandName + ": " + e.getMessage());
            getLogger().warning("   Server type: " + Bukkit.getServer().getClass().getName());
            getLogger().warning("   /halloween may still be registered. This could cause conflicts.");
            // Non-critical, continue anyway
        }
    }
    
    /**
     * v3.0.5 - Replace incompatible (pre-3.0.5) config with a fresh config from the JAR
     * Creates timestamped backup for user reference
     */
    private void replaceWithFreshConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        File backupFile = new File(getDataFolder(), "config.old_" + timestamp + ".yml");
        
        try {
            // Create timestamped backup of old config
            if (configFile.exists()) {
                java.nio.file.Files.copy(
                    configFile.toPath(),
                    backupFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
                getLogger().info("🎃 Replacing outdated config with fresh v" + getDescription().getVersion() + " config");
                getLogger().info("   Your old config backed up as: " + backupFile.getName());
            }
            
            // Delete old config
            configFile.delete();
            
            // Force save fresh config from resources
            saveDefaultConfig();
            reloadConfig();
            
            getLogger().info("✅ Fresh v" + getDescription().getVersion() + " config applied");
            getLogger().info("   Your old settings are saved in the backup file");
            
        } catch (Exception e) {
            getLogger().warning("Could not replace config: " + e.getMessage());
            getLogger().warning("Please manually delete config.yml to use fresh defaults");
        }
    }
    
    /**
     * v3.0.0 - Check for old "Halloween" folder from previous versions
     * This plugin now uses "Halloween_Softici" folder to avoid conflicts
     */
    private void checkForFolderConflicts() {
        File pluginsFolder = getDataFolder().getParentFile();
        File oldHalloweenFolder = new File(pluginsFolder, "Halloween");
        
        // Check if old "Halloween" folder exists (from v1.x-2.x)
        if (oldHalloweenFolder.exists() && isOurPluginFolder(oldHalloweenFolder)) {
            getLogger().warning("╔═══════════════════════════════════════════════════════╗");
            getLogger().warning("║  NOTICE: Config folder changed in v3.0.0!           ║");
            getLogger().warning("║  OLD: plugins/Halloween/                            ║");
            getLogger().warning("║  NEW: plugins/Halloween_Softici/                    ║");
            getLogger().warning("║                                                      ║");
            getLogger().warning("║  Your old configs are still in plugins/Halloween/   ║");
            getLogger().warning("║  Copy them to Halloween_Softici/ if you want to     ║");
            getLogger().warning("║  preserve your settings, or delete Halloween/       ║");
            getLogger().warning("║  folder to use fresh v3.0.0 defaults.               ║");
            getLogger().warning("╚═══════════════════════════════════════════════════════╝");
        }
    }
    
    /**
     * Check if a folder belongs to our plugin by looking for our signature files
     */
    private boolean isOurPluginFolder(File folder) {
        File configFile = new File(folder, "config.yml");
        
        if (!configFile.exists()) {
            return false;
        }
        
        // Read config to check for our signature
        try {
            java.util.Scanner scanner = new java.util.Scanner(configFile);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                // Look for our specific config markers (v3.0.0+ or legacy names)
                if (line.contains("Halloween Plugin v") || 
                    line.contains("pumpkin_zombie_boss:") ||
                    line.contains("pumpkin_skeleton_boss:") ||
                    line.contains("pumpkin_monster:") ||  // legacy
                    line.contains("pumpkin_witch:") ||     // legacy
                    line.contains("Softici")) {
                    scanner.close();
                    return true;
                }
            }
            scanner.close();
        } catch (Exception e) {
            // If we can't read it, assume it's not ours
            return false;
        }
        
        return false;
    }
    
    /**
     * v1.7.0 - Migrate and merge old config with new defaults
     * Returns true if migration was performed
     */
    private boolean migrateAndMergeConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        
        // If config doesn't exist, no migration needed
        if (!configFile.exists()) {
            return false;
        }
        
        // Load existing config
        reloadConfig();
        
        // First, load current config with defaults merged
        getConfig().options().copyDefaults(true);
        
        // v3.0.0 - Check config version and detect if it needs regeneration/migration
        String configVersion = getConfig().getString("config_version", "unknown");
        
        // v3.4.0 - Allow 3.0.5 through 3.4.0 to coexist without migration (only migrate older versions)
        // v3.0.0 through v3.0.4 get REPLACED with fresh config (too many breaking changes)
        if (!configVersion.equals(CONFIG_VERSION) && !configVersion.equals("3.3.0") && !configVersion.equals("3.2.2") && !configVersion.equals("3.2.1") && !configVersion.equals("3.2.0") && !configVersion.equals("3.1.1") && !configVersion.equals("3.1.0") &&
            !configVersion.equals("3.0.9") && !configVersion.equals("3.0.8") && !configVersion.equals("3.0.7") &&
            !configVersion.equals("3.0.6") && !configVersion.equals("3.0.5")) {
            getLogger().info("Detected config version: " + configVersion + " (expected: " + CONFIG_VERSION + ") - replacing with fresh " + CONFIG_VERSION + " config");
            replaceWithFreshConfig();
            return true;
        }
        
        // v3.1.0 - Smart merge: Add new config sections without replacing existing ones
        // (3.1.x configs have all keys already; the merge only bumps config_version for them)
        if (!configVersion.equals(CONFIG_VERSION)) {
            getLogger().info("Config v" + configVersion + " detected - applying smart merge to add missing keys...");
            smartMergeConfigSections();
        }
        
        // If we reach here, config is v3.0.5 or newer and has been brought up to the current keys
        getLogger().info("✅ Config updated successfully");
        return false;
    }

    /**
     * v3.1.0 - Smart merge: Add new config sections without overwriting existing values
     * v3.3.0 - Single code path: rewrites config_version, applies text migrations
     * (nested keys, buff level format) and appends new top-level sections. The file is only
     * ever edited line by line, so user values, comments and ordering are preserved.
     */
    private void smartMergeConfigSections() {
        File configFile = new File(getDataFolder(), "config.yml");
        try {
            // Load defaults from JAR
            InputStream defaultStream = getResource("config.yml");
            if (defaultStream == null) {
                getLogger().warning("⚠️  Could not find default config.yml in JAR!");
                return;
            }
            FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(defaultStream, java.nio.charset.StandardCharsets.UTF_8)
            );
            
            // Load CURRENT config from disk (not from JAR, so contains() reflects the user's file only)
            FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
            String fromVersion = currentConfig.getString("config_version", "unknown");
            
            // Identify new top-level sections that don't exist in the current config
            List<String> sectionsToAdd = new ArrayList<>();
            for (String key : defaultConfig.getKeys(false)) {
                if (!currentConfig.contains(key)) {
                    sectionsToAdd.add(key);
                    getLogger().info("  → Found new section to add: '" + key + "'");
                }
            }
            
            // Read current config file as text
            List<String> lines = java.nio.file.Files.readAllLines(
                configFile.toPath(), 
                java.nio.charset.StandardCharsets.UTF_8
            );
            
            // Version line + in-place migrations (nested keys, value format changes)
            applyTextMigrations(lines, fromVersion);
            
            // Append new sections at the end
            if (!sectionsToAdd.isEmpty()) {
                lines.add("");
                lines.add("# --- Added by Halloween Plugin v" + CONFIG_VERSION + " (smart merge). Documentation for these keys is in the default config.yml inside the JAR. ---");
                for (String section : sectionsToAdd) {
                    for (String line : yamlSerialize(section, defaultConfig.get(section), 0).split("\n")) {
                        if (!line.isEmpty()) {
                            lines.add(line);
                        }
                    }
                    lines.add("");  // Blank line after each section
                }
            }
            
            // Write entire updated content back to file
            java.nio.file.Files.write(
                configFile.toPath(),
                lines,
                java.nio.charset.StandardCharsets.UTF_8
            );
            
            // Reload config into memory
            reloadConfig();
            
            if (!sectionsToAdd.isEmpty()) {
                getLogger().info("✓ Smart merge complete: Added " + sectionsToAdd.size() + " new config section(s) at END of file");
                for (String section : sectionsToAdd) {
                    getLogger().info("  ✓ Added section: '" + section + "'");
                }
            }
            getLogger().info("✓ Existing config values preserved - no data loss");
            getLogger().info("✓ Config updated to version " + CONFIG_VERSION);
            
        } catch (Exception e) {
            // Create timestamped backup on error
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
            File backupFile = new File(getDataFolder(), "config_" + timestamp + ".backup.yml");
            
            try {
                java.nio.file.Files.copy(
                    configFile.toPath(),
                    backupFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
                getLogger().warning("⚠️  Failed to smart merge config.yml: " + e.getMessage());
                getLogger().warning("   Backup created: " + backupFile.getName());
                getLogger().warning("   You can recover your config from this backup if needed");
            } catch (Exception backupError) {
                getLogger().severe("⚠️  Failed to backup config.yml: " + backupError.getMessage());
            }
        }
    }

    /**
     * v3.3.0 - All line-based migrations of an existing config.yml in one place.
     * Only touches the lines it has to; everything else (values, comments, order) stays as is.
     *
     * @param lines       file content, edited in place
     * @param fromVersion config_version the file had before this run
     */
    private void applyTextMigrations(List<String> lines, String fromVersion) {
        // Update the version marker
        boolean versionLineFound = false;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("config_version:")) {
                lines.set(i, "config_version: \"" + CONFIG_VERSION + "\"");
                versionLineFound = true;
                break;
            }
        }
        if (!versionLineFound) {
            getLogger().warning("⚠️  config.yml has no top-level config_version line - it will be migrated again on every start");
        }
        
        // v3.1.1 - events.pumpkin.enable_pumpkin_surprise / pumpkin_surprise_count
        ensurePumpkinSurpriseKeys(lines);
        
        // v3.3.0 - pumpkin_buffs levels changed from 0-based amplifier to 1-based level (1 = Effect I)
        if (compareVersions(fromVersion, "3.3.0") < 0) {
            migrateBuffLevelsToOneBased(lines);
        }
        
        // v3.4.0 - configurable Pumpkin Trader trades
        ensurePumpkinVillagerTrades(lines);
        
        // v3.4.0 - grave.pumpkin_drops_per_mob was never read before 3.4.0 (1 pumpkin was hardcoded). The shipped
        // default said 3; keep the in-game behaviour unchanged by turning that untouched default into 1.
        if (compareVersions(fromVersion, "3.4.0") < 0) {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.trim().startsWith("pumpkin_drops_per_mob:") && line.trim().replaceAll("#.*", "").trim().endsWith(" 3")) {
                    lines.set(i, line.replaceFirst("pumpkin_drops_per_mob:\\s*3", "pumpkin_drops_per_mob: 1") + "  # v3.4.0: key is now honoured; was 1 in practice");
                    getLogger().info("  ✓ grave.pumpkin_drops_per_mob: default 3 -> 1 (the key is honoured from 3.4.0; behaviour unchanged)");
                    break;
                }
            }
        }
    }

    /**
     * v3.4.0 - Insert the configurable trade list into an existing pumpkin_villager section
     * (nested keys are not added by the generic top-level merge).
     */
    private void ensurePumpkinVillagerTrades(List<String> lines) {
        int sectionStart = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("pumpkin_villager:")) { sectionStart = i; break; }
        }
        if (sectionStart < 0) return; // section missing entirely -> added as a whole by the top-level merge
        int insertAt = sectionStart + 1;
        for (int i = sectionStart + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.isEmpty() && !Character.isWhitespace(line.charAt(0)) && !line.startsWith("#")) break; // next top-level key
            if (line.trim().startsWith("trades:")) return; // already there
            if (!line.trim().isEmpty() && !line.trim().startsWith("#")) insertAt = i + 1; // after the last real key of the section
        }
        List<String> block = Arrays.asList(
            "  # v3.4.0 - configurable trades (item: any material, or CANDY = Halloween Candy). daily_limit is no longer used.",
            "  trades:",
            "    - cost:   { item: EMERALD, amount: 1 }",
            "      result: { item: CARVED_PUMPKIN, amount: 64 }",
            "      max_uses: 999999",
            "    - cost:   { item: CARVED_PUMPKIN, amount: 64 }",
            "      result: { item: EMERALD, amount: 1 }",
            "      max_uses: 999999",
            "    - cost:   { item: CANDY, amount: 10 }",
            "      result: { item: GOLDEN_APPLE, amount: 1 }",
            "      max_uses: 5",
            "    - cost:   { item: CANDY, amount: 25 }",
            "      result: { item: DIAMOND, amount: 1 }",
            "      max_uses: 3");
        lines.addAll(insertAt, block);
        getLogger().info("  ✓ Added pumpkin_villager.trades (configurable Pumpkin Trader trades)");
    }

    /**
     * v3.1.1 - Insert the two pumpkin surprise keys after events.pumpkin.max if they are missing.
     */
    private void ensurePumpkinSurpriseKeys(List<String> lines) {
        boolean hasSurpriseEnabled = false;
        boolean hasSurpriseCount = false;
        for (String line : lines) {
            if (line.contains("enable_pumpkin_surprise:")) hasSurpriseEnabled = true;
            if (line.contains("pumpkin_surprise_count:")) hasSurpriseCount = true;
        }
        if (hasSurpriseEnabled && hasSurpriseCount) return;
        
        getLogger().info("  → Adding missing pumpkin surprise config keys...");
        for (int i = 0; i < lines.size(); i++) {
            // Find the pumpkin section's max: line
            if (lines.get(i).trim().startsWith("max:") && i > 0) {
                // Check if we're in the pumpkin section (look back for "pumpkin:")
                boolean inPumpkinSection = false;
                for (int j = i - 1; j >= Math.max(0, i - 10); j--) {
                    if (lines.get(j).trim().equals("pumpkin:")) {
                        inPumpkinSection = true;
                        break;
                    }
                    if (lines.get(j).trim().equals("slenderman:") || 
                        lines.get(j).trim().equals("fire:") ||
                        lines.get(j).trim().equals("lightning:")) {
                        break;
                    }
                }
                
                if (inPumpkinSection) {
                    // Insert new keys after max: line
                    int nextIndex = i + 1;
                    if (!hasSurpriseEnabled) {
                        lines.add(nextIndex, "    enable_pumpkin_surprise: true  # If true, players get inventory filled with pumpkins after Nth pumpkin (event or login)");
                        nextIndex++; // Account for the line we just added
                    }
                    if (!hasSurpriseCount) {
                        lines.add(nextIndex, "    pumpkin_surprise_count: 3      # Number of pumpkins required to trigger surprise (default: 3)");
                    }
                    getLogger().info("  ✓ Added pumpkin surprise config keys");
                    return;
                }
            }
        }
        getLogger().warning("  ⚠️  Could not find events.pumpkin.max in config.yml - pumpkin surprise keys not added (defaults are used)");
    }

    /**
     * v3.3.0 - pumpkin_buffs.effects[].level used to be the raw Bukkit amplifier (0 = Effect I).
     * From 3.3.0 the number is the visible effect level (1 = Effect I), like in the /halloween buffs commands.
     * Existing configs get every level inside the pumpkin_buffs section increased by one so that the
     * effects players receive do NOT change.
     */
    private void migrateBuffLevelsToOneBased(List<String> lines) {
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("pumpkin_buffs:")) {
                start = i;
                break;
            }
        }
        if (start < 0) {
            getLogger().warning("  ⚠️  pumpkin_buffs section not found - buff levels were NOT converted. Since v3.3.0 'level: 1' means Effect I, please check your levels.");
            return;
        }
        
        java.util.regex.Pattern levelLine = java.util.regex.Pattern.compile("^(\\s*-?\\s*level:\\s*)(\\d+)(\\s*(?:#.*)?)$");
        int converted = 0;
        for (int i = start + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            // Stop at the next top-level key (non-indented, not a comment)
            if (!line.isEmpty() && !Character.isWhitespace(line.charAt(0)) && !line.startsWith("#")) {
                break;
            }
            java.util.regex.Matcher m = levelLine.matcher(line);
            if (m.matches()) {
                int oldLevel = Integer.parseInt(m.group(2));
                lines.set(i, m.group(1) + (oldLevel + 1) + m.group(3));
                converted++;
            }
        }
        
        if (converted == 0) {
            getLogger().warning("  ⚠️  No 'level:' lines found in pumpkin_buffs - buff levels were NOT converted. Since v3.3.0 'level: 1' means Effect I, please check your levels.");
        } else {
            getLogger().info("  ✓ Converted " + converted + " pumpkin buff level(s) to the v3.3.0 format (level 1 = Effect I) - effects stay the same");
        }
    }

    /**
     * Compare dotted version strings numerically ("3.0.9" < "3.1.0"). Unknown/garbage compares as lowest.
     */
    private static int compareVersions(String a, String b) {
        String[] pa = a.split("\\."), pb = b.split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int x = 0, y = 0;
            try { x = i < pa.length ? Integer.parseInt(pa[i].trim()) : 0; } catch (NumberFormatException ignored) { return -1; }
            try { y = i < pb.length ? Integer.parseInt(pb[i].trim()) : 0; } catch (NumberFormatException ignored) { return 1; }
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    /**
     * Serialize a YAML key-value pair with proper indentation.
     * v3.3.0 - Handles ConfigurationSection (what YamlConfiguration returns for mappings) and
     * lists of mappings, so new sections from the default config are written as valid YAML.
     */
    private String yamlSerialize(String key, Object value, int indent) {
        String indentStr = "  ".repeat(indent);
        
        if (value instanceof org.bukkit.configuration.ConfigurationSection) {
            value = ((org.bukkit.configuration.ConfigurationSection) value).getValues(false);
        }
        
        if (value instanceof Map) {
            StringBuilder sb = new StringBuilder();
            sb.append(indentStr).append(key).append(":\n");
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                sb.append(yamlSerialize(entry.getKey().toString(), entry.getValue(), indent + 1));
            }
            return sb.toString();
        } else if (value instanceof List) {
            StringBuilder sb = new StringBuilder();
            sb.append(indentStr).append(key).append(":\n");
            for (Object item : (List<?>) value) {
                if (item instanceof org.bukkit.configuration.ConfigurationSection) {
                    item = ((org.bukkit.configuration.ConfigurationSection) item).getValues(false);
                }
                if (item instanceof Map) {
                    // "- first: value" then the remaining keys aligned under it
                    boolean first = true;
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) item).entrySet()) {
                        String entryYaml = yamlSerialize(entry.getKey().toString(), entry.getValue(), indent + 2);
                        if (first) {
                            entryYaml = indentStr + "  - " + entryYaml.substring(Math.min(entryYaml.length(), (indent + 2) * 2));
                            first = false;
                        }
                        sb.append(entryYaml);
                    }
                } else {
                    sb.append(indentStr).append("  - ").append(yamlScalar(item)).append("\n");
                }
            }
            return sb.toString();
        } else {
            return indentStr + key + ": " + yamlScalar(value) + "\n";
        }
    }

    private static String yamlScalar(Object value) {
        if (value == null) return "\"\"";
        if (value instanceof String) {
            return "\"" + ((String) value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return value.toString();
    }

    /**
     * v3.4.0 - Turn Halloween on/off globally (shared by /halloween on|off and the season scheduler).
     * @param sender who asked (may be null for the scheduler) - gets the cleanup confirmation on "off"
     */
    public void setHalloweenActive(boolean active, CommandSender sender) {
        data.setGlobalEnabled(active);
        data.saveAsync();
        if (active) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (isEligible(p)) startTasksForPlayer(p);
            }
            broadcastAll(msg("halloweenModeEnabled"));
            return;
        }
        stopAllTasks();
        // v3.0.0 - Force glow removal for all players (critical fix)
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGlowing(false);
        }
        pumpkinMonsterManager.despawnAllBosses();
        pumpkinWitchManager.despawnAllBosses();
        graveManager.despawnAllGraves();
        batWitchManager.despawnAllWitches();
        pumpkinVillagerManager.revertAllVillagers();
        broadcastAll(msg("halloweenModeDisabled"));
        if (sender != null) sender.sendMessage(msg("cmdOffCleanedUp"));
    }

    /**
     * v3.4.0 - /halloween stats [player] and /halloween top <stat> [count]
     */
    private boolean handleStatsCommand(CommandSender sender, String sub, String[] args) {
        if (!stats.isEnabled()) {
            sender.sendMessage(msg("statsDisabled"));
            return true;
        }
        boolean admin = sender.hasPermission("halloween.stats.admin") || sender.hasPermission("halloween.use");
        if (sub.equals("stats")) {
            if (!sender.hasPermission("halloween.stats") && !admin) {
                sender.sendMessage(msg("noPermission"));
                return true;
            }
            UUID target;
            String targetName;
            if (args.length >= 2) {
                if (!admin) { sender.sendMessage(msg("noPermission")); return true; }
                org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                target = op.getUniqueId();
                targetName = op.getName() != null ? op.getName() : args[1];
            } else if (sender instanceof Player) {
                target = ((Player) sender).getUniqueId();
                targetName = sender.getName();
            } else {
                sender.sendMessage(msg("cmdStatsUsage"));
                return true;
            }
            Map<String, Long> all = stats.getAll(target);
            if (all.isEmpty()) {
                sender.sendMessage(msg("statsNoData", "%PLAYER%", targetName));
                return true;
            }
            List<String> visible = admin ? StatsManager.STATS : stats.playerVisibleStats();
            sender.sendMessage(msg("statsHeader", "%PLAYER%", targetName));
            for (String stat : visible) {
                long v = all.getOrDefault(stat, 0L);
                sender.sendMessage(msg("statsLine", "%STAT%", stat, "%VALUE%", stat.equals("pumpkin_time") ? formatDuration(v) : String.valueOf(v)));
            }
            return true;
        }
        // top
        if (!admin && !getConfig().getBoolean("statistics.top_public", true)) {
            sender.sendMessage(msg("noPermission"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(msg("cmdStatsUsage"));
            return true;
        }
        String stat = args[1].toLowerCase(Locale.ROOT);
        if (!stats.isKnownStat(stat)) {
            sender.sendMessage(msg("statsUnknown", "%STAT%", stat));
            return true;
        }
        int count = 10;
        if (args.length >= 3) {
            try { count = Math.max(1, Math.min(50, Integer.parseInt(args[2]))); } catch (NumberFormatException ignored) { /* keep 10 */ }
        }
        List<Map.Entry<UUID, Long>> top = stats.top(stat, count);
        if (top.isEmpty()) {
            sender.sendMessage(msg("topEmpty", "%STAT%", stat));
            return true;
        }
        sender.sendMessage(msg("topHeader", "%STAT%", stat, "%COUNT%", String.valueOf(count)));
        int rank = 1;
        for (Map.Entry<UUID, Long> e : top) {
            String value = stat.equals("pumpkin_time") ? formatDuration(e.getValue()) : String.valueOf(e.getValue());
            sender.sendMessage(msg("topLine", "%RANK%", String.valueOf(rank++), "%PLAYER%", stats.nameOf(e.getKey()), "%VALUE%", value));
        }
        return true;
    }

    /** seconds -> "1h 05m" / "12m 30s" */
    public static String formatDuration(long seconds) {
        if (seconds >= 3600) return String.format("%dh %02dm", seconds / 3600, (seconds % 3600) / 60);
        return String.format("%dm %02ds", seconds / 60, seconds % 60);
    }

    /**
     * v3.4.0 - /halloween trigger <event> [player|@a] - fire any scare event or boss right now.
     * Works regardless of the global on/off state and ignores cooldowns (admin tool).
     */
    private boolean handleTriggerCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(msg("cmdTriggerUsage"));
            return true;
        }
        String event = args[1].toLowerCase(Locale.ROOT);
        if (!TRIGGERABLE.contains(event)) {
            sender.sendMessage(msg("cmdTriggerUnknown", "%EVENT%", event));
            return true;
        }
        List<Player> targets = new ArrayList<>();
        if (args.length >= 3) {
            if (args[2].equals("@a")) {
                targets.addAll(Bukkit.getOnlinePlayers());
            } else {
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage(msg("cmdSoundPlayerNotFound", "%PLAYER%", args[2]));
                    return true;
                }
                targets.add(target);
            }
        } else if (sender instanceof Player) {
            targets.add((Player) sender);
        } else {
            sender.sendMessage(msg("cmdTriggerUsage"));
            return true;
        }
        int count = 0;
        for (Player target : targets) {
            if (triggerEvent(event, target, sender)) count++;
        }
        if (args.length >= 3 && args[2].equals("@a")) {
            sender.sendMessage(msg("cmdTriggerDoneAll", "%EVENT%", event, "%COUNT%", String.valueOf(count)));
        } else if (count > 0) {
            sender.sendMessage(msg("cmdTriggerDone", "%EVENT%", event, "%PLAYER%", targets.get(0).getName()));
        }
        return true;
    }

    public static final List<String> TRIGGERABLE = Arrays.asList(
        "pumpkin", "slenderman", "fire", "lightning", "darkness", "herobrine", "zombieboss", "skeletonboss", "batwitch");

    /**
     * Fire one event for one player. Returns false when it could not run (e.g. Herobrine during the day).
     * Ignores cooldowns and the global on/off state, but NOT world protection: nothing spawns in a
     * blacklisted world or in a WorldGuard region with halloween-events = deny.
     */
    public boolean triggerEvent(String event, Player target, CommandSender sender) {
        if (isWorldBlacklisted(target) || !isLocationAllowed(target.getLocation())) {
            if (sender != null) sender.sendMessage(msg("cmdTriggerProtected", "%PLAYER%", target.getName(), "%WORLD%", target.getWorld().getName()));
            return false;
        }
        switch (event) {
            case "pumpkin": effects.pumpkin(target); return true;
            case "slenderman": effects.slenderman(target); return true;
            case "fire": effects.fire(target); return true;
            case "lightning": effects.lightning(target); return true;
            case "darkness": effects.darkness(target); return true;
            case "herobrine": {
                HerobrineTask task = herobrineTasks.get(target.getUniqueId());
                if (task == null) task = new HerobrineTask(this, target); // one-shot instance
                if (!task.triggerNow()) {
                    if (sender != null) sender.sendMessage(msg("cmdTriggerHerobrineUnavailable", "%PLAYER%", target.getName()));
                    return false;
                }
                return true;
            }
            case "zombieboss": pumpkinMonsterManager.spawnPumpkinMonster(target.getLocation(), target, false); return true;
            case "skeletonboss": pumpkinWitchManager.spawnPumpkinWitch(target.getLocation(), target, false); return true;
            case "batwitch": batWitchManager.spawnTransformedWitch(target.getLocation(), target); return true;
            default: return false;
        }
    }

    /**
     * v3.3.0 - Shortcut for command/system messages from messages.yml. %COMMAND% is always available,
     * further placeholder/value pairs can be appended.
     */
    private String msg(String category, String... placeholderValuePairs) {
        String[] all = new String[placeholderValuePairs.length + 2];
        all[0] = "%COMMAND%"; all[1] = commandPrefix;
        System.arraycopy(placeholderValuePairs, 0, all, 2, placeholderValuePairs.length);
        return messages.get(category, all);
    }

    /* =========================
       Accessors
       ========================= */
    public PlayerDataManager getData() { return data; }
    public MessageManager getMessages() { return messages; }
    public MessageManager getMessageManager() { return messages; } // Alias for GraveManager
    public Random getRandom() { return random; }
    public cz.softici.server.minecraft.hooks.HookManager getHooks() { return hooks; }
    public EventEffects getEffects() { return effects; }
    public SeasonScheduler getSeasonScheduler() { return seasonScheduler; }
    public WitchingHour getWitchingHour() { return witchingHour; }
    public DiscordNotifier getDiscord() { return discord; }
    public RewardService getRewards() { return rewards; }
    public CandyManager getCandy() { return candy; }
    public VisualsManager getVisuals() { return visuals; }

    /**
     * v3.4.0 - Random delay for a scare event in ticks (was copy-pasted in every task).
     * Applies the witching hour multiplier; never below 1 second.
     */
    public long eventDelayTicks(Player player, int min, int max) {
        if (max < min) max = min;
        long minutes = min + random.nextInt(max - min + 1);
        long ticks = minutes * 60L * 20L;
        if (witchingHour != null && witchingHour.isActive(player != null ? player.getWorld() : null)) {
            ticks = (long) (ticks * witchingHour.intervalMultiplier());
        }
        return Math.max(20L, ticks);
    }

    /** v3.4.0 - Stop and restart the per-player tasks of all eligible players (new intervals apply immediately). */
    public void restartAllTasks() {
        stopAllTasks();
        if (!data.isGlobalEnabled()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isEligible(p)) startTasksForPlayer(p);
        }
    }

    /** v3.4.0 - One random sound from the horror sound list (used by the witching hour ambience). */
    public void playRandomHorrorSound(Player player) {
        String name = HORROR_SOUND_NAMES.get(random.nextInt(HORROR_SOUND_NAMES.size()));
        Sound sound = getHorrorSound(name);
        if (sound != null) player.playSound(player.getLocation(), sound, 0.6f, 0.7f + random.nextFloat() * 0.4f);
    }

    public static final List<String> HORROR_SOUND_NAMES = Arrays.asList("cave_ambience", "witch_cackle", "ghast_scream", "enderman_scream",
        "zombie_growl", "skeleton_rattle", "creeper_hiss", "wither", "ender_dragon", "thunder", "bell", "portal", "soul", "sculk_shriek",
        "warden_heartbeat", "horse_death");

    /** v3.4.0 - Broadcast (and send to Discord) the season-end leaderboard. */
    public void announceSeasonTop() {
        int n = getConfig().getInt("statistics.season_end_top", 3);
        if (n <= 0 || !stats.isEnabled()) return;
        String stat = getConfig().getString("statistics.season_end_stat", "halloween_mob_kills");
        List<Map.Entry<UUID, Long>> top = stats.top(stat, n);
        if (top.isEmpty()) return;
        String header = messages.get("seasonTopHeader", "%STAT%", stat);
        List<String> lines = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<UUID, Long> e : top) {
            lines.add(messages.get("topLine", "%RANK%", String.valueOf(rank++), "%PLAYER%", stats.nameOf(e.getKey()), "%VALUE%", String.valueOf(e.getValue())));
        }
        broadcastAll(header);
        for (String l : lines) broadcastAll(l);
        discord.sendTop(header, lines);
    }
    public StatsManager getStats() { return stats; }
    public PumpkinMonsterManager getPumpkinMonsterManager() { return pumpkinMonsterManager; }
    public PumpkinWitchManager getPumpkinWitchManager() { return pumpkinWitchManager; }
    public PumpkinVillagerManager getPumpkinVillagerManager() { return pumpkinVillagerManager; }
    public GraveManager getGraveManager() { return graveManager; }
    public String getCommandPrefix() { return commandPrefix; } // v1.6.1

    /* =========================
       XP Calculation (Smart Scaling)
       ========================= */
    /**
     * Gives smart XP reward that scales based on player level
     * @param player The player to reward
     * @param baseReward Base XP reward (equivalent to levels at level 1)
     * @param multiplier Multiplier for stronger bosses (e.g., 1.0 for normal, 1.4 for strong boss)
     */
    public void giveSmartXpReward(Player player, int baseReward, double multiplier) {
        int currentLevel = player.getLevel();
        
        // Calculate XP equivalent of baseReward levels at level 1
        // At level 1, each level requires 7 XP
        int baseXpAmount = baseReward * 7;
        
        // Apply multiplier (e.g., 1.4 for stronger boss = +40%)
        int adjustedXpAmount = (int) (baseXpAmount * multiplier);
        
        // For high-level players, give raw XP instead of levels
        // This prevents level 90 players from jumping to level 100+
        if (currentLevel >= 30) {
            // Give XP points directly (scales naturally with level requirements)
            player.giveExp(adjustedXpAmount);
            
            // Show approximate levels gained
            int xpForNextLevel = player.getExpToLevel();
            double levelsGained = (double) adjustedXpAmount / xpForNextLevel;
            if (levelsGained >= 0.1) {
                String msg = this.getMessages().getRandomMessage("xpRewardWithLevels", "")
                    .replace("%XP%", String.valueOf(adjustedXpAmount))
                    .replace("%LEVELS%", String.format("%.1f", levelsGained));
                player.sendMessage(msg);
            } else {
                String msg = this.getMessages().getRandomMessage("xpRewardOnly", "")
                    .replace("%XP%", String.valueOf(adjustedXpAmount));
                player.sendMessage(msg);
            }
        } else {
            // For low-level players, calculate how many levels this XP gives them
            int levelsToGive = Math.max(1, adjustedXpAmount / 20); // Rough conversion
            player.giveExpLevels(levelsToGive);
            String msg = this.getMessages().getRandomMessage("levelReward", "")
                .replace("%LEVELS%", String.valueOf(levelsToGive));
            player.sendMessage(msg);
        }
    }

    /* =========================
       Event Listeners
       ========================= */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player joined = e.getPlayer();
        // v3.3.0 - Run 2 ticks later so vanish plugins have applied their "vanished" metadata first
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (joined.isOnline()) {
                handlePlayerJoin(joined);
            }
        }, 2L);
    }

    private void handlePlayerJoin(Player p) {
        boolean joinMessages = getConfig().getBoolean("login_messages.enabled", true);
        boolean joinSound = getConfig().getBoolean("login_messages.sound", true);
        
        if (data.isGlobalEnabled()) {
            if (!data.isDisabled(p.getUniqueId()) && !isWorldBlacklisted(p)) {
                // v3.3.0 - Gameplay (tasks, login pumpkin) only for eligible players (not vanished / spectating);
                // the informational join messages are still shown to them.
                if (isEligible(p)) {
                    // Player has Halloween enabled in allowed world - start tasks
                    startTasksForPlayer(p);
                    
                    // v3.0.0 - Check if login pumpkin feature is enabled
                    boolean loginPumpkinEnabled = getConfig().getBoolean("login_pumpkin.enabled", true);
                    int cooldownMinutes = getConfig().getInt("login_pumpkin.cooldown_minutes", 30);
                    
                    // Check if player doesn't have pumpkin on head
                    if (loginPumpkinEnabled && 
                        (p.getInventory().getHelmet() == null || 
                         p.getInventory().getHelmet().getType() != Material.CARVED_PUMPKIN)) {
                        
                        // Check cooldown (configurable)
                        if (data.canReceiveJoinPumpkin(p.getUniqueId(), cooldownMinutes)) {
                            applyPumpkinIfPossible(p, false);
                            data.setLastPumpkinJoin(p.getUniqueId());
                            data.saveAsync();
                        } else if (joinMessages) {
                            // Still on cooldown
                            long remainingMs = data.getRemainingCooldown(p.getUniqueId(), cooldownMinutes);
                            long remainingMin = remainingMs / 60000;
                            p.sendMessage(getMessages().get("pumpkinCooldown", "%MINUTES%", String.valueOf(remainingMin)));
                        }
                    }
                }
                if (joinSound) {
                    playSpookySound(p);
                }
                if (joinMessages) {
                    // v3.4.0 - season countdown
                    String countdown = seasonScheduler.countdownMessage();
                    if (countdown != null) p.sendMessage(countdown);
                    // v3.0.4 - Configurable welcome message from messages.yml
                    p.sendMessage(getMessages().getRandomMessage("welcomeMessage", p.getName()));
                    // v3.0.4 - Configurable disable notification from messages.yml
                    p.sendMessage(getMessages().getRandomMessage("disableMessageNotification", p.getName(), commandPrefix));
                }
            } else if (joinMessages) {
                // Player has Halloween disabled - inform they can enable it (configurable message)
                // v3.0.4 - Configurable enable notification from messages.yml
                p.sendMessage(getMessages().getRandomMessage("enableMessageNotification", p.getName(), commandPrefix));
            }
        } else if (joinMessages && !data.isDisabled(p.getUniqueId())) {
            // Global Halloween is disabled
            p.sendMessage(getMessages().getRandomMessage("halloweenGloballyDisabled", ""));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        // v3.0.0 - Force glow removal on quit (critical fix)
        p.setGlowing(false);
        stopTasksForPlayer(p);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        if (!fireProtected.contains(p.getUniqueId())) return;

        switch (e.getCause()) {
            case FIRE:
            case FIRE_TICK:
            case LIGHTNING:
                e.setCancelled(true);
                e.setDamage(0.0);
                break;
            default:
        }
    }

    /* =========================
       Helpers
       ========================= */
    public boolean isWorldBlacklisted(Player p) {
        if (p == null || p.getWorld() == null) return false;
        
        List<String> blacklist = getConfig().getStringList("worlds_blacklist");
        String worldName = p.getWorld().getName().trim();
        
        // Clean blacklist entries (remove quotes, trim whitespace)
        return blacklist.stream()
            .map(s -> s.trim().replace("\"", ""))
            .anyMatch(worldName::equalsIgnoreCase);
    }

    public boolean isEligible(Player p) {
        return p != null && p.isOnline() && !data.isDisabled(p.getUniqueId()) && !isWorldBlacklisted(p)
            && !isVanished(p) && !isSkippedSpectator(p) && isLocationAllowed(p.getLocation());
    }

    /** v3.4.0 - WorldGuard "halloween-events" flag (always true without WorldGuard). */
    public boolean isLocationAllowed(org.bukkit.Location loc) {
        return hooks == null || hooks.isLocationAllowed(loc);
    }

    /**
     * v3.3.0 - True if a vanish plugin hides this player. Uses the de-facto standard "vanished" metadata
     * set by SuperVanish/PremiumVanish, Essentials, CMI and others; no plugin dependency needed.
     * Can be turned off with player_filters.respect_vanish: false.
     */
    public boolean isVanished(Player p) {
        if (!getConfig().getBoolean("player_filters.respect_vanish", true)) return false;
        if (!p.hasMetadata("vanished")) return false;
        for (org.bukkit.metadata.MetadataValue value : p.getMetadata("vanished")) {
            if (value.asBoolean()) return true;
        }
        return false;
    }

    /** v3.3.0 - Spectators get no events/buffs when player_filters.skip_spectators is true (default). */
    public boolean isSkippedSpectator(Player p) {
        return getConfig().getBoolean("player_filters.skip_spectators", true)
            && p.getGameMode() == org.bukkit.GameMode.SPECTATOR;
    }

    /**
     * v3.3.0 - Broadcast a message that names a player. If that player is vanished, only they see it,
     * so vanish is never leaked through boss/grave announcements.
     */
    public void broadcastAbout(Player subject, String msg) {
        if (subject != null && subject.isOnline() && isVanished(subject)) {
            subject.sendMessage(msg);
            return;
        }
        broadcastAll(msg);
    }

    public void broadcastAll(String msg) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            // Only send message if player has Halloween enabled (not disabled)
            if (!data.isDisabled(online.getUniqueId())) {
                online.sendMessage(msg);
            }
            // Debug: log if player is disabled
            // else {
            //     getLogger().info("Skipping message for " + online.getName() + " (Halloween disabled)");
            // }
        }
    }
    
    public void sendEventMessage(Player targetPlayer, String msg) {
        if (getConfig().getBoolean("broadcast_events", false)) {
            // Broadcast to all players who have Halloween enabled
            broadcastAll(msg);
        } else {
            // Send only to affected player (if they have Halloween enabled)
            if (!data.isDisabled(targetPlayer.getUniqueId())) {
                targetPlayer.sendMessage(msg);
            }
        }
    }

    public void playSpookySound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 0.7f, 0.5f);
    }

    public void applyPumpkinIfPossible(Player player, boolean broadcast) {
        if (player == null || !player.isOnline()) return;

        ItemStack pumpkin = new ItemStack(Material.CARVED_PUMPKIN);
        boolean hasSpace = player.getInventory().firstEmpty() != -1;

        if (player.getInventory().getHelmet() == null) {
            if (hasSpace) {
                player.getInventory().setHelmet(pumpkin);
                afterPumpkinApplied(player, broadcast);
            }
        } else {
            if (hasSpace) {
                ItemStack currentHelmet = player.getInventory().getHelmet();
                player.getInventory().addItem(currentHelmet);
                player.getInventory().setHelmet(pumpkin);
                afterPumpkinApplied(player, broadcast);
            }
        }
    }

    private void afterPumpkinApplied(Player player, boolean broadcast) {
        playSpookySound(player);
        if (broadcast) {
            sendEventMessage(player, messages.getRandomMessage("pumpkin", player.getName()));
        }

        int count = data.incrementPumpkinCount(player.getUniqueId());
        stats.increment(player, "pumpkins_received"); // v3.4.0
        
        // v3.1.1 - Check config for pumpkin surprise settings
        boolean surpriseEnabled = getConfig().getBoolean("events.pumpkin.enable_pumpkin_surprise", true);
        int surpriseCount = Math.max(1, getConfig().getInt("events.pumpkin.pumpkin_surprise_count", 3));
        
        if (surpriseEnabled && count >= surpriseCount && !data.hasPumpkinPrank(player.getUniqueId())) {
            fillFreeSlotsWithPumpkins(player);
            data.setPumpkinPrank(player.getUniqueId(), true);
            sendEventMessage(player, messages.getRandomMessage("pumpkinEasterEgg", player.getName()));
        }
        data.saveAsync();
    }

    private void fillFreeSlotsWithPumpkins(Player p) {
        ItemStack stack = new ItemStack(Material.CARVED_PUMPKIN, 64);
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            if (p.getInventory().getItem(i) == null) {
                p.getInventory().setItem(i, stack.clone());
            }
        }
        String msg = getMessages().getRandomMessage("pumpkinLoveNote", "");
        p.sendMessage(msg);
    }

    public void ignitePlayerHarmlessly(Player p, int seconds, boolean allowDamage) {
        if (p == null || !p.isOnline()) return;
        
        // v3.0.0 - Only add fire protection if damage is disabled
        if (!allowDamage) {
            fireProtected.add(p.getUniqueId());
        }
        
        p.setFireTicks(seconds * 20);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!allowDamage) {
                fireProtected.remove(p.getUniqueId());
            }
            p.setFireTicks(0);
        }, seconds * 20L + 1);
    }

    private void applyPumpkinBuffs() {
        if (!getConfig().getBoolean("pumpkin_buffs.enabled", true)) {
            return;
        }

        boolean glowing = getConfig().getBoolean("pumpkin_buffs.glowing", true);
        int duration = getConfig().getInt("pumpkin_buffs.duration", 15); // v1.7.2 - configurable duration (default 15s)
        int durationTicks = duration * 20; // Convert to ticks

        for (Player p : Bukkit.getOnlinePlayers()) {
            // v3.0.1 - CRITICAL FIX: Check if player has Halloween enabled
            if (!isEligible(p)) {
                continue;
            }
            
            if (p.getInventory().getHelmet() != null &&
                    p.getInventory().getHelmet().getType() == Material.CARVED_PUMPKIN) {
                stats.increment(p, "pumpkin_time", 5); // v3.4.0 - seconds wearing the pumpkin (this loop runs every 5 s)
                
                // Apply effects from config
                if (getConfig().contains("pumpkin_buffs.effects")) {
                    List<Map<?, ?>> effects = getConfig().getMapList("pumpkin_buffs.effects");
                    for (Map<?, ?> effectMap : effects) {
                        try {
                            if (effectMap.get("enabled") != null && !Boolean.parseBoolean(effectMap.get("enabled").toString())) {
                                continue;
                            }
                            
                            String typeName = effectMap.get("type").toString();
                            int level = Integer.parseInt(effectMap.get("level").toString());
                            // v3.3.0 - config level is the visible effect level (1 = I); Bukkit amplifier is 0-based
                            int amplifier = Math.max(0, level - 1);
                            
                            @SuppressWarnings("deprecation")
                            PotionEffectType effectType = PotionEffectType.getByName(typeName);
                            if (effectType != null) {
                                p.addPotionEffect(new PotionEffect(effectType, durationTicks, amplifier, true, false, true));
                            }
                        } catch (Exception e) {
                            // Skip invalid effect configurations
                        }
                    }
                }
                
                if (glowing) {
                    p.setGlowing(true);
                }
            } else {
                // v3.0.1 - DISABLED: Buff removal interferes with beacon effects
                // The automatic removal of pumpkin buffs when helmet is removed was causing
                // beacon buffs to be removed every 5 seconds, breaking beacon functionality.
                // Players will need to wait for buffs to naturally expire (15 seconds default).
                
                /*
                // v1.7.2 - Immediately remove all pumpkin buffs when helmet is removed
                if (getConfig().contains("pumpkin_buffs.effects")) {
                    List<Map<?, ?>> effects = getConfig().getMapList("pumpkin_buffs.effects");
                    for (Map<?, ?> effectMap : effects) {
                        try {
                            String typeName = effectMap.get("type").toString();
                            @SuppressWarnings("deprecation")
                            PotionEffectType effectType = PotionEffectType.getByName(typeName);
                            if (effectType != null && p.hasPotionEffect(effectType)) {
                                p.removePotionEffect(effectType);
                            }
                        } catch (Exception e) {
                            // Skip invalid effect configurations
                        }
                    }
                }
                */
                
                // v3.0.1 - Only remove glow effect when helmet is removed (glow is safe to remove)
                if (glowing) {
                    p.setGlowing(false);
                }
            }
        }
    }

    /* =========================
       Helmet Reward (Progressive: 3, 6, 9, ... up to max)
       v3.0.0 - Now configurable via config.yml
       ========================= */
    private void checkHelmetRewards() {
        // v3.0.0 - Check if helmet rewards are enabled
        if (!getConfig().getBoolean("helmet_rewards.enabled", true)) {
            return;
        }
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isEligible(p)) continue;
            UUID id = p.getUniqueId();
            
            if (p.getInventory().getHelmet() == null) {
                pumpkinHelmetStart.remove(id);
                continue;
            }
            
            if (p.getInventory().getHelmet().getType() == Material.CARVED_PUMPKIN) {
                pumpkinHelmetStart.putIfAbsent(id, System.currentTimeMillis());
                long start = pumpkinHelmetStart.get(id);
                long elapsed = System.currentTimeMillis() - start;
                long lastReward = data.getLastHelmetReward(id);
                int currentTier = data.getHelmetRewardTier(id);
                int requiredMinutes = data.getRequiredMinutesForTier(currentTier);
                long requiredMillis = requiredMinutes * 60L * 1000L;
                
                // Check if enough time has passed since wearing started or last reward
                long timeSinceLastReward = System.currentTimeMillis() - lastReward;
                boolean firstReward = lastReward == 0;
                
                if ((firstReward && elapsed >= requiredMillis) || 
                    (!firstReward && timeSinceLastReward >= requiredMillis)) {
                    
                    // Give reward
                    p.getInventory().addItem(new ItemStack(Material.CAKE));
                    
                    // Update tier and time
                    data.incrementHelmetRewardTier(id);
                    stats.increment(p, "cakes"); // v3.4.0
                    data.setLastHelmetReward(id, System.currentTimeMillis());
                    
                    // Mark as rewarded (for legacy compatibility)
                    if (!data.hasHelmetReward(id)) {
                        data.setHelmetReward(id, true);
                    }
                    
                    int nextTier = data.getHelmetRewardTier(id);
                    int nextMinutes = data.getRequiredMinutesForTier(nextTier);
                    
                    broadcastAll(messages.getRandomMessage("helmetReward", p.getName()));
                    String msg = messages.getRandomMessage("helmetRewardTimer", "")
                        .replace("%MINUTES%", String.valueOf(nextMinutes));
                    p.sendMessage(msg);
                    
                    data.saveAsync();
                }
            } else {
                pumpkinHelmetStart.remove(id);
            }
        }
    }

    /* =========================
       Commands
       ========================= */
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(msg("pluginInfo"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // Player commands
        if (sub.equals("help")) {
            // v3.3.0 - help lines live in messages.yml (%COMMAND% = command name)
            sender.sendMessage(msg("helpHeader"));
            sender.sendMessage(msg("help_help"));
            sender.sendMessage(msg("help_enableDisable"));
            sender.sendMessage(msg("help_stats"));
            sender.sendMessage(msg("helpAdminHeader"));
            sender.sendMessage(msg("help_onOff"));
            sender.sendMessage(msg("help_set"));
            sender.sendMessage(msg("help_reset"));
            sender.sendMessage(msg("help_reload"));
            sender.sendMessage(msg("help_sound"));
            sender.sendMessage(msg("help_buffsList"));
            sender.sendMessage(msg("help_buffsToggle"));
            sender.sendMessage(msg("help_buffsGlow"));
            sender.sendMessage(msg("help_buffsAdd"));
            sender.sendMessage(msg("help_buffsRemove"));
            sender.sendMessage(msg("help_buffsEnable"));
            sender.sendMessage(msg("help_buffsDisable"));
            sender.sendMessage(msg("help_buffsLevel"));
            sender.sendMessage(msg("help_trigger"));
            sender.sendMessage(msg("help_debug"));
            sender.sendMessage(msg("help_cleanup"));
            return true;
        }

        if (sub.equals("enable") || sub.equals("disable")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(msg("playersOnly"));
                return true;
            }
            Player p = (Player) sender;
            boolean disable = sub.equals("disable");
            data.setDisabled(p.getUniqueId(), disable);
            data.saveAsync();
            p.sendMessage(msg(disable ? "effectsDisabledForYou" : "effectsEnabledForYou"));
            return true;
        }

        // v3.4.0 - statistics (player-facing, own permissions)
        if (sub.equals("stats") || sub.equals("top")) {
            return handleStatsCommand(sender, sub, args);
        }

        // Admin commands
        if (!sender.hasPermission("halloween.use")) {
            sender.sendMessage(msg("noPermission"));
            return true;
        }

        switch (sub) {
            case "on":
                setHalloweenActive(true, sender);
                break;

            case "off":
                setHalloweenActive(false, sender);
                break;

            case "debug":
                // /halloween debug eligible [player] - why does (or doesn't) a player get events?
                if (args.length >= 2 && args[1].equalsIgnoreCase("eligible")) {
                    Player target = args.length >= 3 ? Bukkit.getPlayer(args[2]) : (sender instanceof Player ? (Player) sender : null);
                    if (target == null) { sender.sendMessage(msg("cmdSoundPlayerNotFound", "%PLAYER%", args.length >= 3 ? args[2] : "?")); return true; }
                    sender.sendMessage("§6Eligibility of §e" + target.getName() + "§6:");
                    sender.sendMessage("§7 global enabled: " + data.isGlobalEnabled());
                    sender.sendMessage("§7 player disabled (/hlw disable): " + data.isDisabled(target.getUniqueId()));
                    sender.sendMessage("§7 world '" + target.getWorld().getName() + "' blacklisted: " + isWorldBlacklisted(target));
                    sender.sendMessage("§7 vanished (metadata): " + isVanished(target));
                    sender.sendMessage("§7 spectator skipped: " + isSkippedSpectator(target) + " (gamemode " + target.getGameMode() + ")");
                    sender.sendMessage("§7 location allowed (WorldGuard): " + isLocationAllowed(target.getLocation()) + " (hook: " + (hooks != null && hooks.hasWorldGuard()) + ")");
                    sender.sendMessage("§7 => isEligible: " + (isEligible(target) ? "§atrue" : "§cfalse"));
                    sender.sendMessage("§7 boss bars: " + visuals.bossBarsEnabled() + " (radius " + visuals.bossBarRadius() + "), titles: " + getConfig().getBoolean("visuals.titles.enabled", true));
                    return true;
                }
                sender.sendMessage(msg("cmdDebugUsage"));
                return true;

            case "candy":
                // /halloween candy give <player> <amount>
                if (args.length < 4 || !args[1].equalsIgnoreCase("give")) {
                    sender.sendMessage(msg("cmdCandyUsage"));
                    return true;
                }
                {
                    Player target = Bukkit.getPlayer(args[2]);
                    if (target == null) { sender.sendMessage(msg("cmdSoundPlayerNotFound", "%PLAYER%", args[2])); return true; }
                    int amount;
                    try { amount = Integer.parseInt(args[3]); } catch (NumberFormatException e) { sender.sendMessage(msg("cmdCandyUsage")); return true; }
                    if (amount < 1 || amount > 6400) { sender.sendMessage(msg("cmdCandyUsage")); return true; }
                    candy.give(target, amount);
                    stats.increment(target, "candy_earned", amount);
                    sender.sendMessage(msg("cmdCandyGiven", "%PLAYER%", target.getName(), "%AMOUNT%", String.valueOf(amount)));
                }
                break;

            case "trigger":
                if (!sender.hasPermission("halloween.trigger")) {
                    sender.sendMessage(msg("noPermission"));
                    return true;
                }
                return handleTriggerCommand(sender, args);

            case "reload":
                reloadConfig();
                data.load();
                messages.reload();
                pumpkinVillagerManager.refreshAllTrades(); // v3.4.0 - config-driven trades
                
                // Restart all tasks to apply new intervals
                stopAllTasks();
                if (data.isGlobalEnabled()) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (!data.isDisabled(p.getUniqueId())) {
                            startTasksForPlayer(p);
                        }
                    }
                }
                
                sender.sendMessage(msg("cmdReloadDone"));
                sender.sendMessage(msg("cmdReloadTasksRestarted"));
                // v3.2.2 - Tell the admin in-game when messages.yml could not be parsed
                if (messages.isFileBroken()) {
                    sender.sendMessage(msg("cmdReloadMessagesBroken"));
                    sender.sendMessage(msg("cmdReloadMessagesError", "%ERROR%", String.valueOf(messages.getFileError()).split("\\n")[0].trim()));
                    sender.sendMessage(msg("cmdReloadMessagesHint"));
                }
                break;

            case "set":
                if (args.length != 4) {
                    sender.sendMessage(msg("cmdSetUsage"));
                    return true;
                }
                try {
                    String action = args[1].toLowerCase();
                    int min = Integer.parseInt(args[2]);
                    int max = Integer.parseInt(args[3]);
                    
                    // v3.0.0 - BREAKING CHANGE: Write to config.yml events section
                    getConfig().set("events." + action + ".min", min);
                    getConfig().set("events." + action + ".max", max);
                    saveConfig();
                    
                    // v3.0.0 - Special feedback for interval 0 0 (disabled event)
                    if (min == 0 && max == 0) {
                        sender.sendMessage(msg("cmdSetDisabled", "%ACTION%", action));
                        getLogger().info("✅ Event '" + action + "' has been DISABLED (interval 0 0) by " + sender.getName());
                    } else {
                        sender.sendMessage(msg("cmdSetApplied", "%ACTION%", action, "%MIN%", String.valueOf(min), "%MAX%", String.valueOf(max)));
                    }
                    
                    // Restart all tasks to apply new interval immediately
                    stopAllTasks();
                    if (data.isGlobalEnabled()) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (!data.isDisabled(p.getUniqueId())) {
                                startTasksForPlayer(p);
                            }
                        }
                        sender.sendMessage(msg("cmdSetTasksRestarted"));
                    } else {
                        sender.sendMessage(msg("cmdSetGloballyDisabledNote"));
                    }
                } catch (Exception e) {
                    sender.sendMessage(msg("cmdSetInvalid"));
                }
                break;

            case "reset":
                if (args.length < 2 || args.length > 3) {
                    sender.sendMessage(msg("cmdResetUsage"));
                    return true;
                }
                String what = args[1].toLowerCase();
                switch (what) {
                    case "cooldowns": {
                        // v3.4.0 - boss + grave cooldowns, for one player or everyone
                        if (args.length == 3) {
                            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(args[2]);
                            data.resetCooldowns(op.getUniqueId());
                            sender.sendMessage(msg("cmdResetCooldownsPlayer", "%PLAYER%", op.getName() != null ? op.getName() : args[2]));
                        } else {
                            data.resetCooldowns(null);
                            sender.sendMessage(msg("cmdResetCooldowns"));
                        }
                        break;
                    }
                    case "pumpkin":
                        data.resetPumpkinPranked();
                        sender.sendMessage(msg("cmdResetPumpkin"));
                        break;
                    case "helmet":
                        data.resetHelmetRewarded();
                        sender.sendMessage(msg("cmdResetHelmet"));
                        break;
                    case "all":
                        data.resetAll();
                        sender.sendMessage(msg("cmdResetAll"));
                        break;
                    default:
                        sender.sendMessage(msg("cmdResetInvalid"));
                        return true;
                }
                data.saveAsync();
                break;

            case "buffs":
                return handleBuffsCommand(sender, args);

            case "sound":
                return handleSoundCommand(sender, args);

            case "cleanup":
                // v3.0.0 - Enhanced cleanup command with optional "all" parameter
                boolean includeTraders = args.length > 1 && args[1].equalsIgnoreCase("all");
                cleanupAllEntities(includeTraders);
                if (includeTraders) {
                    sender.sendMessage(msg("cmdCleanupAll"));
                } else {
                    sender.sendMessage(msg("cmdCleanupDone"));
                    sender.sendMessage(msg("cmdCleanupHint"));
                }
                break;

            default:
                sender.sendMessage(msg("cmdUnknown"));
        }
        return true;
    }

    private boolean handleBuffsCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(msg("cmdBuffsUsage"));
            return true;
        }

        String subCmd = args[1].toLowerCase();

        switch (subCmd) {
            case "list":
                sender.sendMessage(msg("cmdBuffsListHeader"));
                sender.sendMessage(msg(getConfig().getBoolean("pumpkin_buffs.enabled", true) ? "cmdBuffsListStatusEnabled" : "cmdBuffsListStatusDisabled"));
                sender.sendMessage(msg(getConfig().getBoolean("pumpkin_buffs.glowing", true) ? "cmdBuffsListGlowOn" : "cmdBuffsListGlowOff"));
                sender.sendMessage(msg("cmdBuffsListEffectsHeader"));
                
                if (getConfig().contains("pumpkin_buffs.effects")) {
                    List<Map<?, ?>> effects = getConfig().getMapList("pumpkin_buffs.effects");
                    for (Map<?, ?> effectMap : effects) {
                        String type = effectMap.get("type").toString();
                        int level = Integer.parseInt(effectMap.get("level").toString());
                        boolean enabled = effectMap.get("enabled") == null || Boolean.parseBoolean(effectMap.get("enabled").toString());
                        sender.sendMessage(msg(enabled ? "cmdBuffsListEffectEnabled" : "cmdBuffsListEffectDisabled", "%EFFECT%", type, "%LEVEL%", String.valueOf(level)));
                    }
                }
                break;

            case "toggle":
                boolean currentEnabled = getConfig().getBoolean("pumpkin_buffs.enabled", true);
                getConfig().set("pumpkin_buffs.enabled", !currentEnabled);
                saveConfig();
                sender.sendMessage(msg(!currentEnabled ? "cmdBuffsToggledOn" : "cmdBuffsToggledOff"));
                break;

            case "glow":
                if (args.length < 3) {
                    sender.sendMessage(msg("cmdBuffsGlowUsage"));
                    return true;
                }
                boolean glowOn = args[2].equalsIgnoreCase("on");
                getConfig().set("pumpkin_buffs.glowing", glowOn);
                saveConfig();
                
                // v3.0.0 - Force glow removal for all players when disabled (critical fix)
                if (!glowOn) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.setGlowing(false);
                    }
                }
                
                sender.sendMessage(msg(glowOn ? "cmdBuffsGlowOn" : "cmdBuffsGlowOff"));
                break;

            case "add":
                if (args.length < 4) {
                    sender.sendMessage(msg("cmdBuffsAddUsage"));
                    return true;
                }
                int addLevel = parseBuffLevel(sender, args[3]);
                if (addLevel < 0) return true;
                return addBuff(sender, args[2].toUpperCase(), addLevel);

            case "remove":
                if (args.length < 3) {
                    sender.sendMessage(msg("cmdBuffsRemoveUsage"));
                    return true;
                }
                return removeBuff(sender, args[2].toUpperCase());

            case "enable":
            case "disable":
                if (args.length < 3) {
                    sender.sendMessage(msg("cmdBuffsEnableDisableUsage", "%SUBCOMMAND%", subCmd));
                    return true;
                }
                return toggleBuff(sender, args[2].toUpperCase(), subCmd.equals("enable"));

            case "level":
                if (args.length < 4) {
                    sender.sendMessage(msg("cmdBuffsLevelUsage"));
                    return true;
                }
                int newLevel = parseBuffLevel(sender, args[3]);
                if (newLevel < 0) return true;
                return setBuffLevel(sender, args[2].toUpperCase(), newLevel);

            default:
                sender.sendMessage(msg("cmdBuffsUnknown"));
        }
        return true;
    }

    /**
     * v3.3.0 - Parse a buff level typed by an admin (1 = Effect I). Returns -1 (after telling the sender) when invalid.
     */
    private int parseBuffLevel(CommandSender sender, String arg) {
        try {
            int level = Integer.parseInt(arg.trim());
            if (level >= 1 && level <= 255) return level;
        } catch (NumberFormatException ignored) {
            // fall through
        }
        sender.sendMessage(msg("cmdBuffsInvalidLevel", "%LEVEL%", arg));
        return -1;
    }

    private boolean addBuff(CommandSender sender, String effectName, int level) {
        List<Map<?, ?>> effects = getConfig().getMapList("pumpkin_buffs.effects");
        
        // Check if effect already exists
        for (Map<?, ?> effectMap : effects) {
            if (effectMap.get("type").toString().equalsIgnoreCase(effectName)) {
                sender.sendMessage(msg("cmdBuffsAlreadyExists", "%EFFECT%", effectName));
                return true;
            }
        }

        // Validate effect type
        @SuppressWarnings("deprecation")
        PotionEffectType effectType = PotionEffectType.getByName(effectName);
        if (effectType == null) {
            sender.sendMessage(msg("cmdBuffsInvalidEffect", "%EFFECT%", effectName));
            return true;
        }

        // Add new effect
        Map<String, Object> newEffect = new HashMap<>();
        newEffect.put("type", effectName);
        newEffect.put("level", level);
        newEffect.put("enabled", true);
        
        List<Map<String, Object>> newEffects = new ArrayList<>();
        for (Map<?, ?> oldMap : effects) {
            Map<String, Object> copy = new HashMap<>();
            copy.put("type", oldMap.get("type"));
            copy.put("level", oldMap.get("level"));
            copy.put("enabled", oldMap.get("enabled"));
            newEffects.add(copy);
        }
        newEffects.add(newEffect);
        
        getConfig().set("pumpkin_buffs.effects", newEffects);
        saveConfig();
        sender.sendMessage(msg("cmdBuffsAdded", "%EFFECT%", effectName, "%LEVEL%", String.valueOf(level)));
        return true;
    }

    private boolean removeBuff(CommandSender sender, String effectName) {
        List<Map<?, ?>> effects = getConfig().getMapList("pumpkin_buffs.effects");
        List<Map<String, Object>> newEffects = new ArrayList<>();
        boolean found = false;

        for (Map<?, ?> effectMap : effects) {
            if (!effectMap.get("type").toString().equalsIgnoreCase(effectName)) {
                Map<String, Object> copy = new HashMap<>();
                copy.put("type", effectMap.get("type"));
                copy.put("level", effectMap.get("level"));
                copy.put("enabled", effectMap.get("enabled"));
                newEffects.add(copy);
            } else {
                found = true;
            }
        }

        if (!found) {
            sender.sendMessage(msg("cmdBuffsNotFound", "%EFFECT%", effectName));
            return true;
        }

        getConfig().set("pumpkin_buffs.effects", newEffects);
        saveConfig();
        sender.sendMessage(msg("cmdBuffsRemoved", "%EFFECT%", effectName));
        return true;
    }

    private boolean toggleBuff(CommandSender sender, String effectName, boolean enable) {
        List<Map<?, ?>> effects = getConfig().getMapList("pumpkin_buffs.effects");
        List<Map<String, Object>> newEffects = new ArrayList<>();
        boolean found = false;

        for (Map<?, ?> effectMap : effects) {
            Map<String, Object> copy = new HashMap<>();
            copy.put("type", effectMap.get("type"));
            copy.put("level", effectMap.get("level"));
            
            if (effectMap.get("type").toString().equalsIgnoreCase(effectName)) {
                copy.put("enabled", enable);
                found = true;
            } else {
                copy.put("enabled", effectMap.get("enabled"));
            }
            newEffects.add(copy);
        }

        if (!found) {
            sender.sendMessage(msg("cmdBuffsNotFound", "%EFFECT%", effectName));
            return true;
        }

        getConfig().set("pumpkin_buffs.effects", newEffects);
        saveConfig();
        sender.sendMessage(msg(enable ? "cmdBuffsEffectEnabled" : "cmdBuffsEffectDisabled", "%EFFECT%", effectName));
        return true;
    }

    private boolean setBuffLevel(CommandSender sender, String effectName, int level) {
        List<Map<?, ?>> effects = getConfig().getMapList("pumpkin_buffs.effects");
        List<Map<String, Object>> newEffects = new ArrayList<>();
        boolean found = false;

        for (Map<?, ?> effectMap : effects) {
            Map<String, Object> copy = new HashMap<>();
            copy.put("type", effectMap.get("type"));
            copy.put("enabled", effectMap.get("enabled"));
            
            if (effectMap.get("type").toString().equalsIgnoreCase(effectName)) {
                copy.put("level", level);
                found = true;
            } else {
                copy.put("level", effectMap.get("level"));
            }
            newEffects.add(copy);
        }

        if (!found) {
            sender.sendMessage(msg("cmdBuffsNotFound", "%EFFECT%", effectName));
            return true;
        }

        getConfig().set("pumpkin_buffs.effects", newEffects);
        saveConfig();
        sender.sendMessage(msg("cmdBuffsLevelSet", "%EFFECT%", effectName, "%LEVEL%", String.valueOf(level)));
        return true;
    }

    /**
     * v1.6.2 - Handle sound command to play horror sounds
     */
    private boolean handleSoundCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(msg("cmdSoundUsage"));
            sender.sendMessage(msg("cmdSoundExample1"));
            sender.sendMessage(msg("cmdSoundExample2"));
            return true;
        }

        String targetName = args[1];
        String soundName = args[2].toLowerCase();

        // Get sound from map
        Sound sound = getHorrorSound(soundName);
        if (sound == null) {
            sender.sendMessage(msg("cmdSoundUnknown", "%SOUND%", soundName));
            sender.sendMessage(msg("cmdSoundTabHint"));
            return true;
        }

        // Play to all players
        if (targetName.equals("@a")) {
            int count = 0;
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
                count++;
            }
            sender.sendMessage(msg("cmdSoundPlayedAll", "%SOUND%", soundName, "%COUNT%", String.valueOf(count)));
            return true;
        }

        // Play to specific player
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(msg("cmdSoundPlayerNotFound", "%PLAYER%", targetName));
            return true;
        }

        target.playSound(target.getLocation(), sound, 1.0f, 1.0f);
        sender.sendMessage(msg("cmdSoundPlayed", "%SOUND%", soundName, "%PLAYER%", target.getName()));
        return true;
    }

    /**
     * v1.6.2 - Get Sound from horror sound name
     */
    private Sound getHorrorSound(String name) {
        switch (name) {
            case "cave_ambience":
            case "cave":
                return Sound.AMBIENT_CAVE;
            case "witch_cackle":
            case "witch":
                return Sound.ENTITY_WITCH_AMBIENT;
            case "ghast_scream":
            case "ghast":
                return Sound.ENTITY_GHAST_SCREAM;
            case "enderman_scream":
            case "enderman":
                return Sound.ENTITY_ENDERMAN_SCREAM;
            case "zombie_growl":
            case "zombie":
                return Sound.ENTITY_ZOMBIE_AMBIENT;
            case "skeleton_rattle":
            case "skeleton":
                return Sound.ENTITY_SKELETON_AMBIENT;
            case "creeper_hiss":
            case "creeper":
                return Sound.ENTITY_CREEPER_PRIMED;
            case "wither":
                return Sound.ENTITY_WITHER_AMBIENT;
            case "ender_dragon":
            case "dragon":
                return Sound.ENTITY_ENDER_DRAGON_GROWL;
            case "thunder":
                return Sound.ENTITY_LIGHTNING_BOLT_THUNDER;
            case "bell":
                return Sound.BLOCK_BELL_USE;
            case "portal":
                return Sound.BLOCK_PORTAL_AMBIENT;
            case "soul":
                return Sound.PARTICLE_SOUL_ESCAPE;
            case "sculk_shriek":
            case "sculk":
                return Sound.BLOCK_SCULK_SHRIEKER_SHRIEK;
            case "warden_heartbeat":
            case "warden":
                return Sound.ENTITY_WARDEN_HEARTBEAT;
            case "horse_death":
            case "dying_horse":
            case "horse":
                return Sound.ENTITY_HORSE_DEATH;
            default:
                return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("help", "enable", "disable", "stats", "top", "on", "off", "set", "reset", "reload", "buffs", "sound", "trigger", "candy", "debug", "cleanup");
        }
        // v3.0.0 - Tab completion for cleanup command
        if (args.length == 2 && args[0].equalsIgnoreCase("cleanup")) {
            return Arrays.asList("all");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return Arrays.asList("pumpkin", "slenderman", "fire", "lightning", "darkness", "herobrine");
        }
        // v3.0.0 - Tab completion for interval values (min)
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return Arrays.asList("0", "1", "5", "10", "20", "30", "60", "120");
        }
        // v3.0.0 - Tab completion for interval values (max)
        if (args.length == 4 && args[0].equalsIgnoreCase("set")) {
            return Arrays.asList("0", "1", "5", "10", "20", "30", "60", "120");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            return Arrays.asList("pumpkin", "helmet", "all");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("buffs")) {
            return Arrays.asList("list", "toggle", "glow", "add", "remove", "enable", "disable", "level");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("buffs") && args[1].equalsIgnoreCase("glow")) {
            return Arrays.asList("on", "off");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("buffs") && 
            (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove") || 
             args[1].equalsIgnoreCase("enable") || args[1].equalsIgnoreCase("disable") || 
             args[1].equalsIgnoreCase("level"))) {
            return Arrays.asList("SPEED", "HASTE", "STRENGTH", "REGENERATION", "RESISTANCE", 
                                "JUMP_BOOST", "NIGHT_VISION", "INVISIBILITY", "FIRE_RESISTANCE", 
                                "WATER_BREATHING", "ABSORPTION", "HEALTH_BOOST", "LUCK", "SLOW_FALLING");
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("buffs") && 
            (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("level"))) {
            return Arrays.asList("1", "2", "3", "4", "5");
        }
        // v1.6.2 - Sound command tab completion
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            return Arrays.asList("pumpkin", "helmet", "cooldowns", "all");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("reset") && args[1].equalsIgnoreCase("cooldowns")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return names;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("candy")) {
            return Collections.singletonList("give");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            return Collections.singletonList("eligible");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("debug")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return names;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("candy")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return names;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
            return StatsManager.STATS;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("stats")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return names;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("trigger")) {
            return TRIGGERABLE;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("trigger")) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("@a");
            for (Player p : Bukkit.getOnlinePlayers()) suggestions.add(p.getName());
            return suggestions;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sound")) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("@a");
            for (Player p : Bukkit.getOnlinePlayers()) {
                suggestions.add(p.getName());
            }
            return suggestions;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("sound")) {
            return Arrays.asList("cave_ambience", "witch_cackle", "ghast_scream", "enderman_scream", 
                                "zombie_growl", "skeleton_rattle", "creeper_hiss", "wither", 
                                "ender_dragon", "thunder", "bell", "portal", "soul", "sculk_shriek", 
                                "warden_heartbeat", "horse_death");
        }
        return Collections.emptyList();
    }

    /* =========================
       Per-player Task Management
       ========================= */
    private void startTasksForPlayer(Player p) {
        UUID id = p.getUniqueId();
        
        // Only start tasks if their intervals are not 0 0 (disabled)
        // When min and max are both 0, the event is considered disabled
        
        if (!pumpkinTasks.containsKey(id)) {
            int[] pumpkinInterval = data.getInterval("pumpkin");
            if (pumpkinInterval[0] > 0 || pumpkinInterval[1] > 0) {
                PumpkinTask pt = new PumpkinTask(this, p);
                pt.scheduleNext();
                pumpkinTasks.put(id, pt);
            }
        }
        
        if (!slendermanTasks.containsKey(id)) {
            int[] slendermanInterval = data.getInterval("slenderman");
            if (slendermanInterval[0] > 0 || slendermanInterval[1] > 0) {
                SlendermanTask st = new SlendermanTask(this, p);
                st.scheduleNext();
                slendermanTasks.put(id, st);
            }
        }
        
        if (!fireTasks.containsKey(id)) {
            int[] fireInterval = data.getInterval("fire");
            if (fireInterval[0] > 0 || fireInterval[1] > 0) {
                FireTask ft = new FireTask(this, p);
                ft.scheduleNext();
                fireTasks.put(id, ft);
            }
        }
        
        if (!lightningTasks.containsKey(id)) {
            int[] lightningInterval = data.getInterval("lightning");
            if (lightningInterval[0] > 0 || lightningInterval[1] > 0) {
                LightningTask lt = new LightningTask(this, p);
                lt.scheduleNext();
                lightningTasks.put(id, lt);
            }
        }
        
        if (!darknessTasks.containsKey(id)) {
            int[] darknessInterval = data.getInterval("darkness");
            if (darknessInterval[0] > 0 || darknessInterval[1] > 0) {
                DarknessTask dt = new DarknessTask(this, p);
                dt.scheduleNext();
                darknessTasks.put(id, dt);
            }
        }
        
        if (!herobrineTasks.containsKey(id)) {
            int[] herobrineInterval = data.getInterval("herobrine");
            if (herobrineInterval[0] > 0 || herobrineInterval[1] > 0) {
                HerobrineTask ht = new HerobrineTask(this, p);
                ht.scheduleNext();
                herobrineTasks.put(id, ht);
            }
        }
    }

    private void stopTasksForPlayer(Player p) {
        UUID id = p.getUniqueId();
        // v3.0.0 - Force glow removal when stopping tasks (critical fix)
        p.setGlowing(false);
        if (pumpkinTasks.containsKey(id)) pumpkinTasks.remove(id).cancel();
        if (slendermanTasks.containsKey(id)) slendermanTasks.remove(id).cancel();
        if (fireTasks.containsKey(id)) fireTasks.remove(id).cancel();
        if (lightningTasks.containsKey(id)) lightningTasks.remove(id).cancel();
        if (darknessTasks.containsKey(id)) darknessTasks.remove(id).cancel();
        if (herobrineTasks.containsKey(id)) herobrineTasks.remove(id).cancel();
    }

    private void stopAllTasks() {
        pumpkinTasks.values().forEach(PumpkinTask::cancel);
        slendermanTasks.values().forEach(SlendermanTask::cancel);
        fireTasks.values().forEach(FireTask::cancel);
        lightningTasks.values().forEach(LightningTask::cancel);
        darknessTasks.values().forEach(DarknessTask::cancel);
        herobrineTasks.values().forEach(HerobrineTask::cancel);
        pumpkinTasks.clear();
        slendermanTasks.clear();
        fireTasks.clear();
        lightningTasks.clear();
        darknessTasks.clear();
        herobrineTasks.clear();
    }

    /**
     * v3.0.0 - Enhanced cleanup for ALL Halloween entities
     * Cleans up Herobrines, bosses, grave mobs, and optionally Pumpkin Traders
     * @param includeTraders If true, also removes Pumpkin Traders
     */
    private void cleanupAllEntities(boolean includeTraders) {
        int herobrineCount = 0;
        
        // Remove all Herobrine entities from all worlds
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (entity instanceof org.bukkit.entity.ArmorStand) {
                    org.bukkit.entity.ArmorStand stand = (org.bukkit.entity.ArmorStand) entity;
                    // Check if it's a Herobrine entity (by custom name)
                    if (stand.getCustomName() != null && stand.getCustomName().contains("Herobrine")) {
                        stand.remove();
                        herobrineCount++;
                    }
                }
            }
        }
        
        // Reset sequence state for all players with Halloween enabled
        for (HerobrineTask task : herobrineTasks.values()) {
            task.resetSequence();
        }
        
        // v3.0.0 - Despawn all bosses
        pumpkinMonsterManager.despawnAllBosses();
        pumpkinWitchManager.despawnAllBosses();
        
        // v3.0.5 - Robust cleanup: Find Halloween entities by characteristics
        int bossesRemoved = 0;
        int graveMobsRemoved = 0;
        
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                boolean shouldRemove = false;
                String entityType = "";
                
                // Check for Halloween bosses by name (works even after server restart)
                if (entity instanceof org.bukkit.entity.LivingEntity) {
                    org.bukkit.entity.LivingEntity living = (org.bukkit.entity.LivingEntity) entity;
                    String customName = living.getCustomName();
                    
                    if (customName != null) {
                        // Halloween bosses by name: "Halloween Boss" (skeleton) or "Pumpkin Monster" (zombie)
                        if (customName.contains("Halloween Boss") || customName.contains("Pumpkin Monster")) {
                            shouldRemove = true;
                            entityType = customName.contains("Halloween Boss") ? "Halloween Boss (Skeleton)" : "Pumpkin Monster (Zombie)";
                            bossesRemoved++;
                        }
                        // Minions by name: "Cursed Bat" or "Haunting Phantom"
                        else if (customName.contains("Cursed Bat") || customName.contains("Haunting Phantom")) {
                            shouldRemove = true;
                            entityType = customName.contains("Cursed Bat") ? "Cursed Bat" : "Haunting Phantom";
                            bossesRemoved++;
                        }
                        // Pumpkin Traders (only if includeTraders is true)
                        else if (includeTraders && customName.contains("Pumpkin Trader")) {
                            shouldRemove = true;
                            entityType = "Pumpkin Trader";
                            bossesRemoved++;
                        }
                        // Grave mobs: have pumpkin helmets and glow effect (no specific names)
                        else if ((entity instanceof org.bukkit.entity.Zombie || entity instanceof org.bukkit.entity.Skeleton) &&
                                living.getEquipment() != null && living.getEquipment().getHelmet() != null &&
                                living.getEquipment().getHelmet().getType() == org.bukkit.Material.CARVED_PUMPKIN &&
                                living.hasPotionEffect(org.bukkit.potion.PotionEffectType.GLOWING)) {
                            shouldRemove = true;
                            entityType = "Grave Mob";
                            graveMobsRemoved++;
                        }
                    }
                }
                // Horse riders from grave system
                else if (entity instanceof org.bukkit.entity.Horse && 
                        ((org.bukkit.entity.Horse) entity).getPassengers().size() > 0) {
                    org.bukkit.entity.Entity passenger = ((org.bukkit.entity.Horse) entity).getPassengers().get(0);
                    if (passenger instanceof org.bukkit.entity.LivingEntity) {
                        org.bukkit.entity.LivingEntity living = (org.bukkit.entity.LivingEntity) passenger;
                        org.bukkit.inventory.ItemStack helmet = living.getEquipment().getHelmet();
                        
                        if (helmet != null && helmet.getType() == org.bukkit.Material.CARVED_PUMPKIN) {
                            shouldRemove = true;
                            entityType = "Grave Horse";
                            graveMobsRemoved++;
                        }
                    }
                }
                
                if (shouldRemove) {
                    entity.remove();
                    getLogger().info("Removed " + entityType + ": " + entity.getType() + " at " + 
                                   entity.getLocation().getWorld().getName() + " " + 
                                   entity.getLocation().getBlockX() + "," + 
                                   entity.getLocation().getBlockY() + "," + 
                                   entity.getLocation().getBlockZ());
                }
            }
        }
        
        // v3.0.0 - Close all active grave events
        graveManager.despawnAllGraves();
        
        // v3.0.0 - Remove all bat transformation witches
        batWitchManager.despawnAllWitches();
        
        // v3.0.0 - Optionally revert Pumpkin Traders
        if (includeTraders) {
            pumpkinVillagerManager.revertAllVillagers();
        }
        
        // Log results
        if (herobrineCount > 0) {
            getLogger().info("🧹 Cleaned up " + herobrineCount + " Herobrine entit" + (herobrineCount == 1 ? "y" : "ies"));
        }
        if (bossesRemoved > 0) {
            getLogger().info("🧹 Removed " + bossesRemoved + " Halloween boss" + (bossesRemoved == 1 ? "" : "es") + " by characteristics");
        }
        if (graveMobsRemoved > 0) {
            getLogger().info("🧹 Removed " + graveMobsRemoved + " grave mob" + (graveMobsRemoved == 1 ? "" : "s") + " by characteristics");
        }
        getLogger().info("🧹 Despawned all bosses and closed all grave events");
        if (includeTraders) {
            getLogger().info("🧹 Reverted all Pumpkin Traders to normal villagers");
        }
    }
    
    /**
     * Legacy method for backwards compatibility - calls enhanced cleanup
     * @deprecated Use cleanupAllEntities(boolean) instead
     */
    @Deprecated
    private void cleanupAllHerobrines() {
        cleanupAllEntities(false);
    }
}
