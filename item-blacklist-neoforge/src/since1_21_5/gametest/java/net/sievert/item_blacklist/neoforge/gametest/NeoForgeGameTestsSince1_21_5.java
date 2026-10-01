package net.sievert.item_blacklist.neoforge.gametest;

import net.sievert.item_blacklist.gametest.ItemBlacklistGameTests;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The instances era on NeoForge (1.21.5 to 1.21.11): the scenarios go into the vanilla
 * TEST_FUNCTION registry through a DeferredRegister, and the test instances in the test
 * mod's data (data/item_blacklist_gametest/test_instance) name them, as on 26.x.
 */
public final class NeoForgeGameTestsSince1_21_5 implements GameTestRegistration.Backend {
    @Override
    public void register(IEventBus modBus) {
        DeferredRegister<Consumer<GameTestHelper>> functions =
                DeferredRegister.create(Registries.TEST_FUNCTION,
                        ItemBlacklistGameTests.NAMESPACE);
        ItemBlacklistGameTests.register(
                (path, function) -> functions.register(path, () -> function));
        functions.register(modBus);
    }
}
