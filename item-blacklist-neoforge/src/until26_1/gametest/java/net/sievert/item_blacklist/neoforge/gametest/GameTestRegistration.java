package net.sievert.item_blacklist.neoforge.gametest;

import net.sievert.item_blacklist.line.Backends;
import net.neoforged.bus.api.IEventBus;

/**
 * The NeoForge test mod's registration on the 1.21.x line. The GameTest framework changed era
 * at 1.21.5, so it comes from a backend picked for the running release:
 * {@code NeoForgeGameTestsUntil1_21_5} (annotations: vanilla's GameTestRegistry; the line
 * module's own) or {@code NeoForgeGameTestsSince1_21_5} (instances: a DeferredRegister on
 * TEST_FUNCTION; v1_21_5's own); both ship in the line's test jar.
 */
final class GameTestRegistration {
    /** The calls that differ; each backend implements them for its window. */
    interface Backend {
        void register(IEventBus modBus);
    }

    private static final Backend BACKEND = Backends.pick("GameTestRegistration", Backend.class,
            Backends.until("1.21.5",
                    "net.sievert.item_blacklist.neoforge.gametest.NeoForgeGameTestsUntil1_21_5"),
            Backends.since("1.21.5",
                    "net.sievert.item_blacklist.neoforge.gametest.NeoForgeGameTestsSince1_21_5"));

    private GameTestRegistration() {
    }

    /** Registers the scenarios the way the running release's GameTest era wants. */
    static void register(IEventBus modBus) {
        BACKEND.register(modBus);
    }
}
