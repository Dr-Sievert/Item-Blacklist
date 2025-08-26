package net.sievert.item_blacklist;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static net.sievert.item_blacklist.BlacklistLogger.*;
import static net.sievert.item_blacklist.BlacklistLogger.Group.*;

/**
 * Handles filtering villager and wandering trader trades
 * against the configured blacklist.
 */
public final class BlacklistVillagerTrades {
    private BlacklistVillagerTrades() {}

    private static final AtomicInteger VANILLA_REMOVED = new AtomicInteger();
    private static final AtomicInteger FABRIC_API_REMOVED = new AtomicInteger();
    private static final AtomicInteger OTHER_REMOVED = new AtomicInteger();

    /** Called from the vanilla mixin when a trade is removed. */
    public static void incrementVanillaRemoved(int count) {
        VANILLA_REMOVED.addAndGet(count);
    }

    /** Called from the Fabric API mixin when a trade is removed. */
    public static void incrementFabricRemoved() {
        FABRIC_API_REMOVED.incrementAndGet();
    }

    /** Called from our own filtering logic. */
    private static void incrementOtherRemoved(int count) {
        OTHER_REMOVED.addAndGet(count);
    }

    public static int getVanillaRemovedTotal() { return VANILLA_REMOVED.get(); }
    public static int getFabricRemovedTotal() { return FABRIC_API_REMOVED.get(); }
    public static int getOtherRemovedTotal() { return OTHER_REMOVED.get(); }

    /** Initializes trade blacklist filtering and logs results when server starts. */
    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            applyBlacklist();
            logVillagerTradeSummary();
        });
    }

    /** Logs a summary of trades removed across all sources. */
    private static void logVillagerTradeSummary() {
        int vanillaRemoved = getVanillaRemovedTotal();
        int moddedRemoved = getFabricRemovedTotal() + getOtherRemovedTotal();
        int totalRemoved = vanillaRemoved + moddedRemoved;
        String label = pluralize(totalRemoved, "trade", "trades");
        BlacklistConfig config = ItemBlacklist.CONFIG;
        if (config == null || config.blacklist.isEmpty()) {
            info(TRADE, "No blacklist config present. Skipping villager trade filter.");
            return;
        }
        if (totalRemoved > 0) {
            info(TRADE, "Villager trade blacklist: " + totalRemoved + " " + label + " removed");
        } else {
            info(TRADE, "No blacklisted villager trades found.");
        }
    }

    /**
     * Applies blacklist filtering to non-vanilla, non-Fabric trades.
     * Assumes the blacklist has already been validated.
     */
    private static void applyBlacklist() {
        if (ItemBlacklist.CONFIG == null) {
            warn(TRADE, "Config not loaded; skipping other modded trade blacklist.");
            return;
        }

        AtomicInteger removed = new AtomicInteger();

        for (Map<VillagerProfession, Int2ObjectMap<TradeOffers.Factory[]>> map :
                java.util.List.of(TradeOffers.PROFESSION_TO_LEVELED_TRADE)) {
            for (var entry : map.entrySet()) {
                VillagerProfession profession = entry.getKey();
                Int2ObjectMap<TradeOffers.Factory[]> byLevel = entry.getValue();
                for (int level : byLevel.keySet()) {
                    TradeOffers.Factory[] oldArr = byLevel.get(level);
                    if (oldArr == null || oldArr.length == 0) continue;

                    TradeOffers.Factory[] newArr = Arrays.stream(oldArr)
                            .filter(f -> shouldKeepFactory(f, profession, level))
                            .toArray(TradeOffers.Factory[]::new);
                    removed.addAndGet(oldArr.length - newArr.length);
                    byLevel.put(level, newArr);
                }
            }
        }

        for (int level : TradeOffers.WANDERING_TRADER_TRADES.keySet()) {
            TradeOffers.Factory[] oldArr = TradeOffers.WANDERING_TRADER_TRADES.get(level);
            if (oldArr == null || oldArr.length == 0) continue;

            TradeOffers.Factory[] newArr = Arrays.stream(oldArr)
                    .filter(f -> shouldKeepFactory(f, null, level))
                    .toArray(TradeOffers.Factory[]::new);
            removed.addAndGet(oldArr.length - newArr.length);
            TradeOffers.WANDERING_TRADER_TRADES.put(level, newArr);
        }

        incrementOtherRemoved(removed.get());
    }

    /** Determines whether a trade factory should be kept. */
    public static boolean shouldKeepFactory(TradeOffers.Factory factory, VillagerProfession profession, int level) {
        try {
            var rng = net.minecraft.util.math.random.Random.create();
            var offer = factory.create(null, rng);

            if (offer != null) {
                var config = ItemBlacklist.CONFIG;
                String context = describeContext(profession, level);

                if (isBlacklisted(offer.getSellItem())) {
                    if (config.detailedTradeLog) {
                        var id = Registries.ITEM.getId(offer.getSellItem().getItem());
                        info(TRADE, "Removed output " + id + " from " + context);
                    }
                    return false;
                }

                if (isBlacklisted(offer.getOriginalFirstBuyItem())) {
                    if (config.detailedTradeLog) {
                        var id = Registries.ITEM.getId(offer.getOriginalFirstBuyItem().getItem());
                        info(TRADE, "Removed first buy " + id + " from " + context);
                    }
                    return false;
                }

                if (offer.getSecondBuyItem()
                        .map(TradedItem::itemStack)
                        .filter(BlacklistVillagerTrades::isBlacklisted)
                        .isPresent()) {
                    if (config.detailedTradeLog) {
                        info(TRADE, "Removed second buy (blacklisted item) from " + context);
                    }
                    return false;
                }
            }
        } catch (Throwable t) {
            debug(TRADE, "Skipped filtering a factory due to error: " + t);
        }
        return true;
    }

    /** Builds human-readable context like "Novice Armorer" or "Wandering Trader". */
    private static String describeContext(VillagerProfession profession, int level) {
        if (profession == null) {
            return "Wandering Trader (level " + level + ")";
        }

        String levelName = Text.translatable("merchant.level." + level).getString();

        String profName = Text.translatable("entity.minecraft.villager." + profession.id()).getString();

        return levelName + " " + profName;
    }


    /** Checks if a given item stack is blacklisted. */
    private static boolean isBlacklisted(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var id = Registries.ITEM.getId(stack.getItem());
        return ItemBlacklist.CONFIG.blacklist.contains(id);
    }
}
