package net.sievert.item_blacklist.fabric.gametest;

import net.sievert.item_blacklist.gametest.ItemBlacklistGameTests;
import net.sievert.item_blacklist.line.Keys;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;

/**
 * The Fabric test mod's registration on this line, whose releases are all 1.21.5 or later:
 * the scenarios go into the vanilla TEST_FUNCTION registry, where the test instances in the
 * test mod's data (data/item_blacklist_gametest/test_instance) find them. The ids come from the
 * line's Keys.
 */
final class GameTestRegistration {
    private GameTestRegistration() {
    }

    static void register() {
        ItemBlacklistGameTests.register((path, function) -> Registry.register(
                BuiltInRegistries.TEST_FUNCTION,
                Keys.of(Registries.TEST_FUNCTION, ItemBlacklistGameTests.NAMESPACE, path),
                function));
    }
}
