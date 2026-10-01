package net.sievert.item_blacklist.neoforge.gametest;

import net.sievert.item_blacklist.gametest.Fixtures;
import net.sievert.item_blacklist.gametest.ItemBlacklistGameTests;
import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The NeoForge loot fixture, for loot_additions_filtered: registers the codec
 * item_blacklist_gametest:add_items of LootAdditionsModifier; its instance is data
 * (data/item_blacklist_gametest/loot_modifiers/add_items.json, listed on 1.21.x in
 * neoforge:loot_modifiers/global_loot_modifiers.json), limited to the one test table. Global
 * loot modifiers exist on every release, so the flag is set on all 13.
 */
final class LootFixture {
    private LootFixture() {
    }

    /** Called by the test mod's constructor with its mod bus. */
    static void register(IEventBus modBus) {
        DeferredRegister<MapCodec<? extends IGlobalLootModifier>> codecs = DeferredRegister.create(
                NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS,
                ItemBlacklistGameTests.NAMESPACE);
        codecs.register("add_items", () -> LootAdditionsModifier.CODEC);
        codecs.register(modBus);
        Fixtures.lootAdditions = true;
    }
}
