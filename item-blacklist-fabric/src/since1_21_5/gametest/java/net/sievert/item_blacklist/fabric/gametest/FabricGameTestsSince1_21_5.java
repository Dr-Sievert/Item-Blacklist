package net.sievert.item_blacklist.fabric.gametest;

import net.sievert.item_blacklist.gametest.ItemBlacklistGameTests;
import net.sievert.item_blacklist.line.Keys;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;

/**
 * The instances era on Fabric (1.21.5 to 1.21.11): the scenarios go into the vanilla
 * TEST_FUNCTION registry, where the test instances in the test mod's data
 * (data/item_blacklist_gametest/test_instance) find them, as on 26.x. The ids come from the line's
 * Keys, which builds them with the id class of the running release.
 */
public final class FabricGameTestsSince1_21_5 implements GameTestRegistration.Backend {
    @Override
    public void register() {
        ItemBlacklistGameTests.register((path, function) -> Registry.register(
                BuiltInRegistries.TEST_FUNCTION,
                Keys.of(Registries.TEST_FUNCTION, ItemBlacklistGameTests.NAMESPACE, path),
                function));
    }
}
