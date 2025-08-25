package net.sievert.item_blacklist;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;

import java.util.Arrays;
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
                Int2ObjectMap<TradeOffers.Factory[]> byLevel = entry.getValue();
                for (int level : byLevel.keySet()) {
                    TradeOffers.Factory[] oldArr = byLevel.get(level);
                    if (oldArr == null || oldArr.length == 0) continue;

                    TradeOffers.Factory[] newArr = Arrays.stream(oldArr)
                            .filter(BlacklistVillagerTrades::shouldKeepFactory)
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
                    .filter(BlacklistVillagerTrades::shouldKeepFactory)
                    .toArray(TradeOffers.Factory[]::new);
            removed.addAndGet(oldArr.length - newArr.length);
            TradeOffers.WANDERING_TRADER_TRADES.put(level, newArr);
        }

        incrementOtherRemoved(removed.get());
    }

    /** Determines whether a trade factory should be kept. */
    public static boolean shouldKeepFactory(TradeOffers.Factory factory) {
        try {
            var rng = net.minecraft.util.math.random.Random.create();
            var offer = factory.create(null, rng);

            if (offer != null) {
                if (isBlacklisted(offer.getSellItem())) return false;
                if (isBlacklisted(offer.getOriginalFirstBuyItem())) return false;
                if (offer.getSecondBuyItem()
                        .map(TradedItem::itemStack)
                        .filter(BlacklistVillagerTrades::isBlacklisted)
                        .isPresent()) {
                    return false;
                }
            }
        } catch (Throwable t) {
            debug(TRADE, "Skipped filtering a factory due to error: " + t);
        }
        return true;
    }

    /** Checks if a given item stack is blacklisted. */
    private static boolean isBlacklisted(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var id = Registries.ITEM.getId(stack.getItem());
        return ItemBlacklist.CONFIG.blacklist.contains(id);
    }
}
