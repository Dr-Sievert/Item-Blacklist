package net.sievert.item_blacklist.neoforge.gametest;

import net.sievert.item_blacklist.gametest.ItemBlacklistGameTests;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The NeoForge test mod's registration on this line, whose releases are all 1.21.5 or later:
 * the scenarios go into the vanilla TEST_FUNCTION registry through a DeferredRegister, and
 * the test instances in the test mod's data (data/item_blacklist_gametest/test_instance) name them.
 */
final class GameTestRegistration {
    private GameTestRegistration() {
    }

    static void register(IEventBus modBus) {
        DeferredRegister<Consumer<GameTestHelper>> functions =
                DeferredRegister.create(Registries.TEST_FUNCTION,
                        ItemBlacklistGameTests.NAMESPACE);
        ItemBlacklistGameTests.register(
                (path, function) -> functions.register(path, () -> function));
        functions.register(modBus);
    }
}
