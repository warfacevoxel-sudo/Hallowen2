package cz.softici.server.minecraft;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.function.Consumer;

/**
 * v3.4.0 - One implementation for every "rewards:" list in config.yml.
 *
 * Supported entries:
 *
 *   - material: DIAMOND
 *     min: 1
 *     max: 3
 *     chance: 0.5
 *
 *   - nexo: inferno:inferno_sword
 *     min: 1
 *     max: 1
 *     chance: 0.1
 *
 *   - candy: 5
 *     min/max optional
 *
 *   - command: "give %player% cake 1"
 *     chance: 1.0
 *
 *   - money: 100
 *     chance: 1.0
 *
 * Nexo is optional. If Nexo is not installed, "nexo:" rewards are skipped.
 */
public class RewardService {

    private final HalloweenPlugin plugin;
    private boolean warnedNoEconomy = false;
    private boolean warnedNoNexo = false;

    public RewardService(HalloweenPlugin plugin) {
        this.plugin = plugin;
    }

    /** Who gets the reward and where items go. */
    public static class Context {

        final String configPath;

        Player single;

        Map<UUID, Integer> shares;

        double multiplier = 1.0;

        Consumer<ItemStack> itemSink = item -> {
        };

        public Context(String configPath) {
            this.configPath = configPath;
        }

        public Context forPlayer(Player p) {
            this.single = p;
            return this;
        }

        public Context forParticipants(Map<UUID, Integer> shares) {
            this.shares = shares;
            return this;
        }

        public Context multiplier(double m) {
            this.multiplier = m;
            return this;
        }

        public Context items(Consumer<ItemStack> sink) {
            this.itemSink = sink;
            return this;
        }

        /** Receiver name for %player% - single player, or "the team" fallback. */
        List<Player> receivers() {
            List<Player> list = new ArrayList<>();

            if (single != null && single.isOnline()) {
                list.add(single);
            }

            if (shares != null) {
                for (UUID id : shares.keySet()) {
                    Player p = Bukkit.getPlayer(id);

                    if (p != null && p.isOnline() && !list.contains(p)) {
                        list.add(p);
                    }
                }
            }

            return list;
        }
    }

    public void give(List<Map<?, ?>> rewards, Context ctx) {
        if (rewards == null) {
            return;
        }

        Random rand = plugin.getRandom();

        for (Map<?, ?> entry : rewards) {
            try {
                double chance = entry.containsKey("chance")
                    ? Double.parseDouble(String.valueOf(entry.get("chance")))
                    : 1.0;

                if (rand.nextDouble() > chance) {
                    continue;
                }

                if (entry.containsKey("material")) {

                    giveMaterial(entry, ctx, rand);

                } else if (entry.containsKey("nexo")) {

                    giveNexo(entry, ctx, rand);

                } else if (entry.containsKey("candy")) {

                    int amount = rollAmount(entry, "candy", ctx, rand);

                    if (amount > 0 && plugin.getCandy().isEnabled()) {
                        ctx.itemSink.accept(plugin.getCandy().create(amount));

                        for (Player p : ctx.receivers()) {
                            plugin.getStats().increment(
                                p,
                                "candy_earned",
                                ctx.shares == null ? amount : 0
                            );
                        }
                    }

                } else if (entry.containsKey("command")) {

                    String cmd = String.valueOf(entry.get("command"));

                    for (Player p : ctx.receivers()) {
                        String resolved = cmd
                            .replace("%PLAYER%", p.getName())
                            .replace("%player%", p.getName());

                        if (resolved.startsWith("/")) {
                            resolved = resolved.substring(1);
                        }

                        Bukkit.dispatchCommand(
                            Bukkit.getConsoleSender(),
                            resolved
                        );
                    }

                } else if (entry.containsKey("money")) {

                    giveMoney(
                        Double.parseDouble(String.valueOf(entry.get("money"))),
                        ctx
                    );

                } else {

                    plugin.getLogger().warning(
                        "Reward entry in " + ctx.configPath +
                        " has no material/nexo/candy/command/money key: " +
                        entry
                    );
                }

            } catch (Exception e) {

                plugin.getLogger().warning(
                    "Invalid reward entry in " + ctx.configPath +
                    ": " + entry +
                    " (" + e.getMessage() + ")"
                );
            }
        }
    }

    private int rollAmount(
        Map<?, ?> entry,
        String fixedKey,
        Context ctx,
        Random rand
    ) {
        int min;
        int max;

        if (entry.containsKey("min") || entry.containsKey("max")) {

            Object minRaw = entry.containsKey("min")
                ? entry.get("min")
                : entry.get("max");

            Object maxRaw = entry.containsKey("max")
                ? entry.get("max")
                : entry.get("min");

            min = Integer.parseInt(String.valueOf(minRaw));
            max = Integer.parseInt(String.valueOf(maxRaw));

        } else {

            min = max = Integer.parseInt(
                String.valueOf(entry.get(fixedKey))
            );
        }

        if (ctx.multiplier != 1.0) {
            min = (int) Math.ceil(min * ctx.multiplier);
            max = (int) Math.ceil(max * ctx.multiplier);
        }

        if (max < min) {
            max = min;
        }

        return min == max
            ? min
            : min + rand.nextInt(max - min + 1);
    }

    private void giveMaterial(
        Map<?, ?> entry,
        Context ctx,
        Random rand
    ) {
        String materialName = String.valueOf(entry.get("material"));

        Material material = Material.matchMaterial(materialName);

        if (material == null) {
            plugin.getLogger().warning(
                "Unknown material '" +
                materialName +
                "' in " +
                ctx.configPath
            );

            return;
        }

        int amount = rollAmount(entry, "amount", ctx, rand);

        if (amount <= 0) {
            return;
        }

        if (material == Material.POTION) {

            for (int i = 0; i < amount; i++) {
                ctx.itemSink.accept(createRandomPotion());
            }

            return;
        }

        int maxStack = Math.max(
            1,
            material.getMaxStackSize()
        );

        while (amount > 0) {

            int n = Math.min(
                amount,
                maxStack
            );

            ctx.itemSink.accept(
                new ItemStack(material, n)
            );

            amount -= n;
        }
    }

    /**
     * Gives a Nexo item into the reward destination.
     *
     * Supports:
     *
     *   nexo: inferno_sword
     *
     * and:
     *
     *   nexo: inferno:inferno_sword
     */
    private void giveNexo(
        Map<?, ?> entry,
        Context ctx,
        Random rand
    ) {
        if (!plugin.getHooks().hasNexo()) {

            if (!warnedNoNexo) {

                plugin.getLogger().warning(
                    "'nexo:' reward in " +
                    ctx.configPath +
                    " skipped - Nexo is not installed or failed to load"
                );

                warnedNoNexo = true;
            }

            return;
        }

        String itemId = String
            .valueOf(entry.get("nexo"))
            .trim();

        if (itemId.isEmpty() || itemId.equalsIgnoreCase("null")) {

            plugin.getLogger().warning(
                "Empty Nexo item ID in " +
                ctx.configPath
            );

            return;
        }

        ItemStack template =
            plugin.getHooks()
                .getNexo()
                .buildItem(itemId);

        if (template == null) {

            plugin.getLogger().warning(
                "Unknown Nexo item '" +
                itemId +
                "' in " +
                ctx.configPath
            );

            return;
        }

        int amount = rollAmount(
            entry,
            "amount",
            ctx,
            rand
        );

        if (amount <= 0) {
            return;
        }

        /*
         * Respect the Nexo item's own max stack size.
         *
         * For example:
         *
         * sword -> max 1
         * custom material -> potentially 64
         */
        int maxStack = Math.max(
            1,
            template.getMaxStackSize()
        );

        while (amount > 0) {

            int stackAmount = Math.min(
                amount,
                maxStack
            );

            ItemStack item = template.clone();

            item.setAmount(stackAmount);

            ctx.itemSink.accept(item);

            amount -= stackAmount;
        }
    }

    private void giveMoney(
        double amount,
        Context ctx
    ) {
        if (amount <= 0) {
            return;
        }

        if (!plugin.getHooks().hasEconomy()) {

            if (!warnedNoEconomy) {

                plugin.getLogger().warning(
                    "'money:' reward in " +
                    ctx.configPath +
                    " skipped - Vault with an economy plugin is not installed"
                );

                warnedNoEconomy = true;
            }

            return;
        }

        double total = amount * ctx.multiplier;

        if (ctx.shares != null && !ctx.shares.isEmpty()) {

            int totalWeight =
                ctx.shares.values()
                    .stream()
                    .mapToInt(Integer::intValue)
                    .sum();

            for (Map.Entry<UUID, Integer> e : ctx.shares.entrySet()) {

                Player p = Bukkit.getPlayer(
                    e.getKey()
                );

                if (p == null || totalWeight <= 0) {
                    continue;
                }

                double part =
                    total *
                    e.getValue() /
                    totalWeight;

                part =
                    Math.round(part * 100.0) /
                    100.0;

                if (
                    part > 0 &&
                    plugin.getHooks().deposit(p, part)
                ) {

                    p.sendMessage(
                        plugin.getMessages().get(
                            "rewardMoney",
                            "%AMOUNT%",
                            plugin.getHooks().formatMoney(part)
                        )
                    );
                }
            }

        } else if (ctx.single != null) {

            total =
                Math.round(total * 100.0) /
                100.0;

            if (
                plugin.getHooks().deposit(
                    ctx.single,
                    total
                )
            ) {

                ctx.single.sendMessage(
                    plugin.getMessages().get(
                        "rewardMoney",
                        "%AMOUNT%",
                        plugin.getHooks().formatMoney(total)
                    )
                );
            }
        }
    }

    /** "Witch's Brew" - random beneficial potion. */
    public ItemStack createRandomPotion() {

        PotionEffectType[] potionTypes = {
            PotionEffectType.SPEED,
            PotionEffectType.STRENGTH,
            PotionEffectType.REGENERATION,
            PotionEffectType.FIRE_RESISTANCE,
            PotionEffectType.NIGHT_VISION,
            PotionEffectType.INVISIBILITY,
            PotionEffectType.WATER_BREATHING,
            PotionEffectType.JUMP_BOOST
        };

        PotionEffectType selectedType =
            potionTypes[
                plugin.getRandom().nextInt(
                    potionTypes.length
                )
            ];

        ItemStack potion =
            new ItemStack(Material.POTION);

        org.bukkit.inventory.meta.PotionMeta meta =
            (org.bukkit.inventory.meta.PotionMeta)
                potion.getItemMeta();

        meta.addCustomEffect(
            new PotionEffect(
                selectedType,
                3600,
                1
            ),
            true
        );

        meta.setDisplayName(
            MessageManager.colorize(
                plugin.getConfig().getString(
                    "pumpkin_skeleton_boss.potion_name",
                    "§5Witch's Brew"
                )
            )
        );

        potion.setItemMeta(meta);

        return potion;
    }
}