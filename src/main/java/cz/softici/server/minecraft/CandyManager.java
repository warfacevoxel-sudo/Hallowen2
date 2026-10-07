package cz.softici.server.minecraft;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * v3.4.0 - "Halloween Candy": a tagged item that drops from Halloween mobs/bosses/graves and is the currency
 * of the Pumpkin Trader (item: CANDY in pumpkin_villager.trades). Tagged with PDC so ordinary cookies can't be
 * used instead.
 *
 * config.yml:
 *   candy: { enabled, material, name, lore, edible, drops: { <source>: { chance, min, max } } }
 */
public class CandyManager implements Listener {
    private final HalloweenPlugin plugin;
    private final NamespacedKey candyKey;

    public CandyManager(HalloweenPlugin plugin) {
        this.plugin = plugin;
        this.candyKey = new NamespacedKey(plugin, "halloween_candy");
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("candy.enabled", true);
    }

    public Material material() {
        Material m = Material.matchMaterial(plugin.getConfig().getString("candy.material", "COOKIE"));
        return m != null && m.isItem() ? m : Material.COOKIE;
    }

    /** Build a candy stack (amount is capped to the material's max stack size by the caller if needed). */
    public ItemStack create(int amount) {
        ItemStack item = new ItemStack(material(), Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(MessageManager.colorize(plugin.getConfig().getString("candy.name", "&6&lHalloween Candy")));
        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("candy.lore")) lore.add(MessageManager.colorize(line));
        if (!lore.isEmpty()) meta.setLore(lore);
        meta.getPersistentDataContainer().set(candyKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isCandy(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(candyKey, PersistentDataType.BYTE);
    }

    /**
     * Roll the candy drop for a source (candy.drops.<source>). Returns the amount (0 = nothing).
     * Sources used: grave_mob, random_pumpkin_mob, boss_defeat, bat_witch
     */
    public int roll(String source) {
        if (!isEnabled()) return 0;
        String base = "candy.drops." + source + ".";
        if (!plugin.getConfig().contains("candy.drops." + source)) return 0;
        double chance = plugin.getConfig().getDouble(base + "chance", 0.0);
        if (plugin.getRandom().nextDouble() > chance) return 0;
        int min = plugin.getConfig().getInt(base + "min", 1);
        int max = Math.max(min, plugin.getConfig().getInt(base + "max", min));
        return min + plugin.getRandom().nextInt(max - min + 1);
    }

    /** Roll + add to a drop list + count the statistic for the killer. */
    public void dropFor(String source, Player killer, List<ItemStack> drops) {
        int amount = roll(source);
        if (amount <= 0) return;
        drops.add(create(amount));
        if (killer != null) plugin.getStats().increment(killer, "candy_earned", amount);
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (isCandy(event.getItem()) && !plugin.getConfig().getBoolean("candy.edible", false)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getMessages().get("candyNotEdible"));
        }
    }

    /** Count candy items in a player's inventory (for messages/stats). */
    public int count(Player player) {
        int n = 0;
        for (ItemStack it : player.getInventory().getContents()) if (isCandy(it)) n += it.getAmount();
        return n;
    }

    /** Give candy directly to a player (admin command); overflow is dropped at their feet. */
    public void give(Player player, int amount) {
        int maxStack = Math.max(1, material().getMaxStackSize());
        while (amount > 0) {
            int n = Math.min(amount, maxStack);
            Map<Integer, ItemStack> left = player.getInventory().addItem(create(n));
            for (ItemStack rest : left.values()) player.getWorld().dropItemNaturally(player.getLocation(), rest);
            amount -= n;
        }
    }
}
