package net.sievert.loot_blacklist;

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

public final class VillagerTradeBlacklist {
    private VillagerTradeBlacklist() {}

    // --- Central counters (set from mixins and local logic) ---
    private static final AtomicInteger FABRIC_API_REMOVED = new AtomicInteger();
    private static final AtomicInteger OTHER_REMOVED = new AtomicInteger();

    /** Called from the Fabric API mixin when a trade is removed. */
    public static void incrementFabricRemoved() {
        FABRIC_API_REMOVED.incrementAndGet();
    }

    /** Called from our own filtering logic. */
    private static void incrementOtherRemoved(int count) {
        OTHER_REMOVED.addAndGet(count);
    }

    public static int getFabricRemovedTotal() {
        return FABRIC_API_REMOVED.get();
    }

    public static int getOtherRemovedTotal() {
        return OTHER_REMOVED.get();
    }

    public static void init() {
        // Main trade blacklist filtering (after all modded items are present)
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            applyBlacklist();

            int fabricRemoved = getFabricRemovedTotal();
            if (fabricRemoved > 0) {
                LootBlacklist.LOGGER.info("Fabric API trade blacklist applied: {} trades removed.", fabricRemoved);
            } else {
                LootBlacklist.LOGGER.info("No Fabric API trades found to blacklist.");
            }

            int otherRemoved = getOtherRemovedTotal();
            if (otherRemoved > 0) {
                LootBlacklist.LOGGER.info("Other modded trade blacklist applied: {} trades removed.", otherRemoved);
            } else {
                LootBlacklist.LOGGER.info("No other modded trades found to blacklist.");
            }
        });
    }

    /**
     * Applies blacklist filtering to non-vanilla, non-Fabric trades.
     * Assumes the blacklist has already been validated (by BlacklistValidator).
     */
    private static void applyBlacklist() {
        if (LootBlacklist.CONFIG == null) {
            LootBlacklist.LOGGER.warn("Config not loaded; skipping other modded trade blacklist.");
            return;
        }

        AtomicInteger removed = new AtomicInteger();

        // --- Professions ---
        for (Map<VillagerProfession, Int2ObjectMap<TradeOffers.Factory[]>> map :
                java.util.List.of(TradeOffers.PROFESSION_TO_LEVELED_TRADE)) {
            for (var entry : map.entrySet()) {
                Int2ObjectMap<TradeOffers.Factory[]> byLevel = entry.getValue();
                for (int level : byLevel.keySet()) {
                    TradeOffers.Factory[] oldArr = byLevel.get(level);
                    if (oldArr == null || oldArr.length == 0) continue;

                    TradeOffers.Factory[] newArr = Arrays.stream(oldArr)
                            .filter(VillagerTradeBlacklist::shouldKeepFactory)
                            .toArray(TradeOffers.Factory[]::new);
                    removed.addAndGet(oldArr.length - newArr.length);
                    byLevel.put(level, newArr);
                }
            }
        }

        // --- Wandering trader ---
        for (int level : TradeOffers.WANDERING_TRADER_TRADES.keySet()) {
            TradeOffers.Factory[] oldArr = TradeOffers.WANDERING_TRADER_TRADES.get(level);
            if (oldArr == null || oldArr.length == 0) continue;

            TradeOffers.Factory[] newArr = Arrays.stream(oldArr)
                    .filter(VillagerTradeBlacklist::shouldKeepFactory)
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
                        .filter(VillagerTradeBlacklist::isBlacklisted)
                        .isPresent()) {
                    return false;
                }
            }
        } catch (Throwable t) {
            LootBlacklist.LOGGER.debug("Skipped filtering a factory due to error: {}", t.toString());
        }
        return true;
    }

    /** Checks if a given item stack is blacklisted. */
    private static boolean isBlacklisted(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var id = Registries.ITEM.getId(stack.getItem());
        return LootBlacklist.CONFIG.blacklist.contains(id);
    }
}
