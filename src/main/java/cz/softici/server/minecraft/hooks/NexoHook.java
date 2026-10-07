package cz.softici.server.minecraft.hooks;

import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.items.ItemBuilder;
import cz.softici.server.minecraft.HalloweenPlugin;
import org.bukkit.inventory.ItemStack;

/**
 * Optional Nexo integration.
 *
 * Converts a Nexo item ID into a fully built ItemStack.
 *
 * Supported config examples:
 *
 *   nexo: inferno_sword
 *   nexo: inferno:inferno_sword
 */
public class NexoHook {

    private final HalloweenPlugin plugin;

    public NexoHook(HalloweenPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Builds a Nexo item from its configured ID.
     *
     * Supports both:
     *   item_id
     *   namespace:item_id
     *
     * @param itemId Nexo item ID
     * @return built ItemStack, or null if the item does not exist
     */
    public ItemStack buildItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }

        itemId = itemId.trim();

        ItemBuilder builder = null;

        // First try the ID exactly as supplied.
        try {
            builder = NexoItems.itemFromId(itemId);
        } catch (Throwable ignored) {
            // Try the namespace-stripped form below.
        }

        /*
         * Depending on the Nexo item registration, the API may internally
         * use the plain item ID while users commonly refer to the item as
         * namespace:item_id.
         *
         * Therefore:
         *
         *   inferno:inferno_sword
         *
         * will also try:
         *
         *   inferno_sword
         */
        if (builder == null && itemId.contains(":")) {
            String plainId = itemId.substring(itemId.indexOf(':') + 1);

            try {
                builder = NexoItems.itemFromId(plainId);
            } catch (Throwable ignored) {
                // Invalid Nexo item.
            }
        }

        if (builder == null) {
            return null;
        }

        try {
            ItemStack item = builder.build();

            if (item == null || item.getType().isAir()) {
                return null;
            }

            return item;
        } catch (Throwable throwable) {
            plugin.getLogger().warning(
                "Failed to build Nexo item '" + itemId + "': " +
                throwable.getMessage()
            );

            return null;
        }
    }

    /**
     * Checks whether a Nexo item exists.
     */
    public boolean exists(String itemId) {
        return buildItem(itemId) != null;
    }
}