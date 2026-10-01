package net.sievert.item_blacklist.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

@Mixin(VillagerTrades.class)
public interface VillagerTradesAccessor {

    @Accessor("TRADES")
    @Mutable
    static void item_blacklist$setTrades(
            Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> trades
    ) {
        throw new AssertionError();
    }

    @Accessor("WANDERING_TRADER_TRADES")
    @Mutable
    static void item_blacklist$setWanderingTraderTrades(
            Int2ObjectMap<VillagerTrades.ItemListing[]> trades
    ) {
        throw new AssertionError();
    }

    @Accessor("EXPERIMENTAL_TRADES")
    @Mutable
    static void item_blacklist$setExperimentalTrades(
            Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> trades
    ) {
        throw new AssertionError();
    }

    @Accessor("EXPERIMENTAL_WANDERING_TRADER_TRADES")
    @Mutable
    static void item_blacklist$setExperimentalWanderingTraderTrades(
            List<Pair<VillagerTrades.ItemListing[], Integer>> trades
    ) {
        throw new AssertionError();
    }
}