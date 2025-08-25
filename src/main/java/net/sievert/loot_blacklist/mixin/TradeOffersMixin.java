package net.sievert.loot_blacklist.mixin;

import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.TradeOffers.Factory;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import net.sievert.loot_blacklist.LootBlacklist;
import net.sievert.loot_blacklist.VillagerTradeBlacklist;

/**
 * Mixin for {@link TradeOffers}.
 * Filters vanilla and rebalanced villager/wandering trader
 * trades against the blacklist after static initialization.
 */
@Mixin(TradeOffers.class)
public abstract class TradeOffersMixin {

    @Final @Shadow
    public static Map<VillagerProfession, Int2ObjectMap<Factory[]>> PROFESSION_TO_LEVELED_TRADE;

    @Final @Shadow
    public static Int2ObjectMap<Factory[]> WANDERING_TRADER_TRADES;

    @Final @Shadow
    public static Map<VillagerProfession, Int2ObjectMap<Factory[]>> REBALANCED_PROFESSION_TO_LEVELED_TRADE;

    @Mutable @Final @Shadow
    public static List<Pair<Factory[], Integer>> REBALANCED_WANDERING_TRADER_TRADES;

    /**
     * Injects after TradeOffers static init to filter
     * blacklisted trades from all vanilla trade maps.
     */
    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void loot_blacklist$filterVanilla(CallbackInfo ci) {
        var config = LootBlacklist.CONFIG;
        if (config == null) return;

        AtomicInteger removed = new AtomicInteger();

        for (Map<VillagerProfession, Int2ObjectMap<Factory[]>> map :
                List.of(PROFESSION_TO_LEVELED_TRADE, REBALANCED_PROFESSION_TO_LEVELED_TRADE)) {
            for (var entry : map.entrySet()) {
                Int2ObjectMap<Factory[]> byLevel = entry.getValue();
                for (int lvl : byLevel.keySet()) {
                    Factory[] arr = byLevel.get(lvl);
                    if (arr == null || arr.length == 0) continue;

                    Factory[] filtered = Arrays.stream(arr)
                            .filter(VillagerTradeBlacklist::shouldKeepFactory)
                            .toArray(Factory[]::new);
                    removed.addAndGet(arr.length - filtered.length);
                    byLevel.put(lvl, filtered);
                }
            }
        }

        for (int lvl : WANDERING_TRADER_TRADES.keySet()) {
            Factory[] arr = WANDERING_TRADER_TRADES.get(lvl);
            if (arr == null || arr.length == 0) continue;

            Factory[] filtered = Arrays.stream(arr)
                    .filter(VillagerTradeBlacklist::shouldKeepFactory)
                    .toArray(Factory[]::new);
            removed.addAndGet(arr.length - filtered.length);
            WANDERING_TRADER_TRADES.put(lvl, filtered);
        }

        REBALANCED_WANDERING_TRADER_TRADES =
                REBALANCED_WANDERING_TRADER_TRADES.stream()
                        .map(pair -> {
                            Factory[] arr = pair.getLeft();
                            Factory[] filtered = Arrays.stream(arr)
                                    .filter(VillagerTradeBlacklist::shouldKeepFactory)
                                    .toArray(Factory[]::new);
                            int lost = arr.length - filtered.length;
                            if (lost > 0) removed.addAndGet(lost);
                            return Pair.of(filtered, pair.getRight());
                        })
                        .collect(Collectors.toList());

        VillagerTradeBlacklist.incrementVanillaRemoved(removed.get());
    }
}
