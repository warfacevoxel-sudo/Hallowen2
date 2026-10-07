package cz.softici.server.minecraft;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PumpkinVillagerManager implements Listener {
    private final HalloweenPlugin plugin;
    private final NamespacedKey pumpkinVillagerKey;
    private final NamespacedKey lastTradeDateKey;
    private final NamespacedKey tradeCountKey;

    public PumpkinVillagerManager(HalloweenPlugin plugin) {
        this.plugin = plugin;
        this.pumpkinVillagerKey = new NamespacedKey(plugin, "pumpkin_villager");
        this.lastTradeDateKey = new NamespacedKey(plugin, "last_trade_date");
        this.tradeCountKey = new NamespacedKey(plugin, "trade_count");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractVillager(PlayerInteractEntityEvent event) {
        if (!plugin.getConfig().getBoolean("pumpkin_villager.enabled", true)) return;
        if (!(event.getRightClicked() instanceof Villager)) return;

        Player player = event.getPlayer();
        
        // v3.1.0 - Check if player is in blacklisted world
        if (plugin.isWorldBlacklisted(player)) return;

        Villager villager = (Villager) event.getRightClicked();
        // v3.4.0 - WorldGuard: halloween-events flag at the villager
        if (!plugin.isLocationAllowed(villager.getLocation())) return;
        ItemStack item = player.getInventory().getItemInMainHand();

        boolean isPumpkinVillager = villager.getPersistentDataContainer().has(pumpkinVillagerKey, PersistentDataType.BYTE);

        // Check if player is holding carved pumpkin AND villager is NOT already converted
        if (item.getType() == Material.CARVED_PUMPKIN && !isPumpkinVillager) {
            // v3.0.2 - Check if Halloween is globally enabled
            if (!plugin.getData().isGlobalEnabled()) {
                return;
            }
            
            // v3.0.2 - Check if player has Halloween disabled
            if (plugin.getData().isDisabled(player.getUniqueId())) {
                return;
            }
            // Convert to pumpkin villager
            event.setCancelled(true);
            
            // Use scheduler to avoid issues with event timing
            Bukkit.getScheduler().runTask(plugin, () -> {
                convertToPumpkinVillager(villager, player, item);
            });
            return;
        }

        // If it's a pumpkin villager: v3.4.0 - make sure its offers match the current config, then daily-limit bookkeeping
        if (isPumpkinVillager) {
            syncTradesWithConfig(villager);
            checkAndResetDailyLimit(villager);
        }
    }

    @EventHandler
    public void onVillagerCareerChange(VillagerCareerChangeEvent event) {
        Villager villager = event.getEntity();
        
        // Prevent pumpkin villagers from changing profession
        if (villager.getPersistentDataContainer().has(pumpkinVillagerKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
            
            // Keep them at level 1 and no experience
            villager.setVillagerLevel(1);
            villager.setVillagerExperience(0);
            
            // Restore their trades if they somehow got cleared
            if (villager.getRecipes().isEmpty()) {
                setupPumpkinTrades(villager);
            }
        }
    }
    
    @EventHandler
    public void onVillagerGainExperience(org.bukkit.event.entity.VillagerAcquireTradeEvent event) {
        if (!(event.getEntity() instanceof Villager)) return;
        Villager villager = (Villager) event.getEntity();
        
        // Prevent pumpkin villagers from gaining experience and leveling up
        if (villager.getPersistentDataContainer().has(pumpkinVillagerKey, PersistentDataType.BYTE)) {
            // Reset experience to 0 to prevent leveling
            Bukkit.getScheduler().runTask(plugin, () -> {
                villager.setVillagerExperience(0);
            });
        }
    }

    private void convertToPumpkinVillager(Villager villager, Player player, ItemStack pumpkin) {
        // Set profession to toolsmith (no hat, so pumpkin shows better)
        // Toolsmith has no headwear that conflicts with the pumpkin helmet
        villager.setProfession(Villager.Profession.TOOLSMITH);
        
        // Set villager level to 1 (Novice) - this is fixed
        villager.setVillagerLevel(1);
        
        // Set experience to 0 to prevent leveling up
        villager.setVillagerExperience(0);
        
        // Mark as pumpkin villager
        villager.getPersistentDataContainer().set(pumpkinVillagerKey, PersistentDataType.BYTE, (byte) 1);
        
        // Set custom name
        villager.setCustomName("§6Pumpkin Trader");
        villager.setCustomNameVisible(true);
        
        // Give villager a pumpkin helmet
        if (villager.getEquipment() != null) {
            villager.getEquipment().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
            villager.getEquipment().setHelmetDropChance(0.0f); // Don't drop on death
        }
        
        // Remove one pumpkin from player's hand
        pumpkin.setAmount(pumpkin.getAmount() - 1);
        
        // Clear any existing trades first
        villager.setRecipes(new ArrayList<>());
        
        // Set up our custom trades
        setupPumpkinTrades(villager);
        
        // Message removed - player can see by villager name
    }

    /**
     * v3.4.0 - Trades come from config (pumpkin_villager.trades). Each entry:
     *   { cost: { item: EMERALD|CANDY|<material>, amount: 1 }, result: { item: ..., amount: 64 }, max_uses: 999999 }
     * "CANDY" means the tagged Halloween Candy item. Falls back to the classic emerald <-> pumpkin pair
     * when the list is missing or empty (configs from before 3.4.0).
     */
    private void setupPumpkinTrades(Villager villager) {
        List<MerchantRecipe> recipes = buildTradesFromConfig();
        if (recipes.isEmpty()) {
            MerchantRecipe recipe1 = new MerchantRecipe(new ItemStack(Material.CARVED_PUMPKIN, 64), 999999);
            recipe1.addIngredient(new ItemStack(Material.EMERALD, 1));
            recipes.add(recipe1);
            MerchantRecipe recipe2 = new MerchantRecipe(new ItemStack(Material.EMERALD, 1), 999999);
            recipe2.addIngredient(new ItemStack(Material.CARVED_PUMPKIN, 64));
            recipes.add(recipe2);
        }
        villager.setRecipes(recipes);
    }

    private List<MerchantRecipe> buildTradesFromConfig() {
        List<MerchantRecipe> recipes = new ArrayList<>();
        for (java.util.Map<?, ?> trade : plugin.getConfig().getMapList("pumpkin_villager.trades")) {
            try {
                ItemStack cost = tradeItem(trade.get("cost"));
                ItemStack result = tradeItem(trade.get("result"));
                if (cost == null || result == null) {
                    plugin.getLogger().warning("Invalid trade in pumpkin_villager.trades (unknown item): " + trade);
                    continue;
                }
                int maxUses = trade.containsKey("max_uses") ? Integer.parseInt(String.valueOf(trade.get("max_uses"))) : 999999;
                MerchantRecipe recipe = new MerchantRecipe(result, Math.max(1, maxUses));
                recipe.addIngredient(cost);
                recipes.add(recipe);
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid trade in pumpkin_villager.trades: " + trade + " (" + e.getMessage() + ")");
            }
        }
        return recipes;
    }

    /** { item: X, amount: n } -> ItemStack (CANDY = Halloween Candy). */
    private ItemStack tradeItem(Object spec) {
        if (!(spec instanceof java.util.Map)) return null;
        java.util.Map<?, ?> m = (java.util.Map<?, ?>) spec;
        String item = String.valueOf(m.get("item"));
        int amount = m.containsKey("amount") ? Integer.parseInt(String.valueOf(m.get("amount"))) : 1;
        amount = Math.max(1, Math.min(64, amount));
        if (item.equalsIgnoreCase("CANDY")) {
            return plugin.getCandy().create(amount);
        }
        Material material = Material.matchMaterial(item);
        if (material == null || !material.isItem()) return null;
        return new ItemStack(material, amount);
    }

    /** v3.4.0 - Re-apply config trades to every loaded Pumpkin Trader (startup and /halloween reload). */
    public void refreshAllTrades() {
        int refreshed = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Villager v : world.getEntitiesByClass(Villager.class)) {
                if (v.getPersistentDataContainer().has(pumpkinVillagerKey, PersistentDataType.BYTE)) {
                    if (syncTradesWithConfig(v)) refreshed++;
                }
            }
        }
        if (refreshed > 0) plugin.getLogger().info("✓ Updated trades of " + refreshed + " Pumpkin Trader(s) from config");
    }

    /**
     * v3.4.0 - Make the villager's offers match pumpkin_villager.trades. Only rewrites when they differ
     * (so max_uses progress of matching offers is kept). Returns true when something changed.
     */
    private boolean syncTradesWithConfig(Villager villager) {
        List<MerchantRecipe> wanted = buildTradesFromConfig();
        if (wanted.isEmpty()) return false; // no config list -> keep whatever the trader has (legacy defaults)
        List<MerchantRecipe> current = villager.getRecipes();
        boolean same = current.size() == wanted.size();
        for (int i = 0; same && i < wanted.size(); i++) {
            MerchantRecipe a = current.get(i), b = wanted.get(i);
            same = a.getResult().isSimilar(b.getResult()) && a.getResult().getAmount() == b.getResult().getAmount()
                && a.getIngredients().size() == b.getIngredients().size()
                && a.getIngredients().get(0).isSimilar(b.getIngredients().get(0))
                && a.getIngredients().get(0).getAmount() == b.getIngredients().get(0).getAmount()
                && a.getMaxUses() == b.getMaxUses();
        }
        if (same) return false;
        villager.setRecipes(wanted);
        return true;
    }

    private void checkAndResetDailyLimit(Villager villager) {
        String today = LocalDate.now().toString();
        String lastTradeDate = villager.getPersistentDataContainer().get(lastTradeDateKey, PersistentDataType.STRING);
        
        if (lastTradeDate == null || !lastTradeDate.equals(today)) {
            // New day, reset counter
            villager.getPersistentDataContainer().set(lastTradeDateKey, PersistentDataType.STRING, today);
            villager.getPersistentDataContainer().set(tradeCountKey, PersistentDataType.INTEGER, 0);
            // Note: Don't call setupPumpkinTrades here - trades are already set during conversion
        }
        // Note: We check limit on actual trade, not on opening trade window
    }

    // This should be called on trade completion - we'll need to add this event
    public void incrementTradeCount(Villager villager) {
        if (!villager.getPersistentDataContainer().has(pumpkinVillagerKey, PersistentDataType.BYTE)) return;
        
        int currentCount = villager.getPersistentDataContainer().getOrDefault(tradeCountKey, PersistentDataType.INTEGER, 0);
        villager.getPersistentDataContainer().set(tradeCountKey, PersistentDataType.INTEGER, currentCount + 1);
        
        int dailyLimit = plugin.getConfig().getInt("pumpkin_villager.daily_limit", 64);
        if (currentCount + 1 >= dailyLimit) {
            // Disable further trades
            villager.setRecipes(new ArrayList<>());
        }
    }

    /**
     * Revert all pumpkin villagers back to normal villagers (called when Halloween is disabled)
     */
    public void revertAllVillagers() {
        int count = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (villager.getPersistentDataContainer().has(pumpkinVillagerKey, PersistentDataType.BYTE)) {
                    // Remove pumpkin helmet
                    if (villager.getEquipment() != null) {
                        villager.getEquipment().setHelmet(null);
                    }
                    
                    // Remove custom name
                    villager.setCustomName(null);
                    villager.setCustomNameVisible(false);
                    
                    // Clear custom trades
                    villager.setRecipes(new ArrayList<>());
                    
                    // Remove persistent data marker
                    villager.getPersistentDataContainer().remove(pumpkinVillagerKey);
                    villager.getPersistentDataContainer().remove(lastTradeDateKey);
                    villager.getPersistentDataContainer().remove(tradeCountKey);
                    
                    // Reset to normal villager state
                    // Keep their profession but let them behave normally again
                    villager.setVillagerLevel(1);
                    villager.setVillagerExperience(0);
                    
                    count++;
                }
            }
        }
        if (count > 0) {
            plugin.getLogger().info("Reverted " + count + " pumpkin villagers to normal (Halloween disabled)");
        }
    }
}
