package net.sievert.loot_blacklist;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class VillagerTradeBlacklist {
    private static final Logger LOGGER = LoggerFactory.getLogger(LootBlacklist.MOD_ID);

    private VillagerTradeBlacklist() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.info("[{}] Applying trade blacklist…", LootBlacklist.MOD_ID);
            applyBlacklist();
        });
    }

    private static void applyBlacklist() {
        if (LootBlacklist.CONFIG == null) {
            LOGGER.warn("[{}] Config not loaded; skipping trade blacklist", LootBlacklist.MOD_ID);
            return;
        }

        AtomicInteger removed = new AtomicInteger();

        // --- 1) Professions ---
        for (Map<VillagerProfession, Int2ObjectMap<TradeOffers.Factory[]>> map :
                java.util.List.of(TradeOffers.PROFESSION_TO_LEVELED_TRADE)) {
            for (var entry : map.entrySet()) {
                Int2ObjectMap<TradeOffers.Factory[]> byLevel = entry.getValue();
                for (int level : byLevel.keySet()) {
                    TradeOffers.Factory[] oldArr = byLevel.get(level);
                    TradeOffers.Factory[] newArr = Arrays.stream(oldArr)
                            .filter(VillagerTradeBlacklist::shouldKeepFactory)
                            .toArray(TradeOffers.Factory[]::new);
                    removed.addAndGet(oldArr.length - newArr.length);
                    byLevel.put(level, newArr);
                }
            }
        }

        // --- 2) Wandering trader ---
        for (int level : TradeOffers.WANDERING_TRADER_TRADES.keySet()) {
            TradeOffers.Factory[] oldArr = TradeOffers.WANDERING_TRADER_TRADES.get(level);
            TradeOffers.Factory[] newArr = Arrays.stream(oldArr)
                    .filter(VillagerTradeBlacklist::shouldKeepFactory)
                    .toArray(TradeOffers.Factory[]::new);
            removed.addAndGet(oldArr.length - newArr.length);
            TradeOffers.WANDERING_TRADER_TRADES.put(level, newArr);
        }

        LOGGER.info("[{}] Trade blacklist applied: {} trade(s) removed.", LootBlacklist.MOD_ID, removed.get());
    }

    public static boolean shouldKeepFactory(TradeOffers.Factory factory) {
        try {
            var rng = net.minecraft.util.math.random.Random.create();
            var offer = factory.create(null, rng);

            if (offer != null) {
                // Output
                if (isBlacklisted(offer.getSellItem())) return false;

                // Input A
                if (isBlacklisted(offer.getOriginalFirstBuyItem())) return false;

                // Input B (optional)
                if (offer.getSecondBuyItem()
                        .map(TradedItem::itemStack) // convert to ItemStack
                        .filter(VillagerTradeBlacklist::isBlacklisted) // only true if blacklisted
                        .isPresent()) {
                    return false;
                }
            }
        } catch (Throwable t) {
            // safest: keep trade on error
        }
        return true;
    }

    private static boolean isBlacklisted(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var id = Registries.ITEM.getId(stack.getItem());
        return LootBlacklist.CONFIG.blacklist.contains(id);
    }









}
