package net.sievert.item_blacklist.neoforge.gametest;

import net.sievert.item_blacklist.gametest.Fixtures;
import net.sievert.item_blacklist.gametest.ItemBlacklistGameTests;
import net.sievert.item_blacklist.gametest.TestConfig;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;

/**
 * The NeoForge test mod: installs the config fixture as every server's config (TestConfig),
 * registers the common scenarios the way the running release wants
 * (GameTestRegistration; RegisterGameTestsEvent is never posted in production), and ticks the
 * vanilla GameTest ticker where NeoForge does not. It ships in the test jar only, which
 * scripts/run.py loads for GameTest runs alone.
 */
@Mod(ItemBlacklistGameTests.NAMESPACE)
public final class ItemBlacklistGameTestsNeoForge {
    public ItemBlacklistGameTestsNeoForge(IEventBus modBus) {
        TestConfig.install();
        GameTestRegistration.register(modBus);
        LootFixture.register(modBus);
        NeoForgeBrewingFixture.register();
        // Not a fixture: the mod's own burn-time listener judges stacks on this loader.
        Fixtures.loaderFuelStackHook = true;
        NeoForge.EVENT_BUS.addListener(
                ServerTickEvent.Post.class, ItemBlacklistGameTestsNeoForge::tickGameTests);
    }

    /**
     * NeoForge (21.1 to 21.11 and 26.x alike) patches MinecraftServer#tickChildren to call
     * GameTestTicker.SINGLETON.tick() only when GameTestHooks.isGametestEnabled(), which is
     * false whenever FML runs in production. A production server then never advances a test:
     * neither vanilla's GameTestServer nor, from 1.21.5 on, tests started with the /test
     * command on a plain server (the only production path NeoForge 21.5 to 21.8 has). Tick it
     * here in exactly that case, with the vanilla guard; in dev (hooks enabled) NeoForge ticks
     * it itself. Should a later NeoForge drop the gate, the tests tick twice: the shim then
     * moves into an until folder of that release.
     */
    private static void tickGameTests(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!GameTestHooks.isGametestEnabled() && server.tickRateManager().runsNormally()) {
            GameTestTicker.SINGLETON.tick();
        }
    }
}
