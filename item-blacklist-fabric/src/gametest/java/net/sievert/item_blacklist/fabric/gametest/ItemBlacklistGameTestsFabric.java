package net.sievert.item_blacklist.fabric.gametest;

import net.sievert.item_blacklist.gametest.TestConfig;
import net.fabricmc.api.ModInitializer;

/**
 * The Fabric test mod: installs the config fixture as every server's config (TestConfig), then
 * registers the common scenarios the way the running release wants (GameTestRegistration).
 * One test-mod fabric.mod.json names this class as its main entrypoint on every line; no
 * fabric-gametest entrypoint is used. It ships in the test jar only, which scripts/run.py
 * loads for GameTest runs alone.
 */
public final class ItemBlacklistGameTestsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        TestConfig.install();
        GameTestRegistration.register();
        LootFixture.register();
    }
}
