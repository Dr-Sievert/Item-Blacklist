package net.sievert.item_blacklist.fabric.gametest;

import net.sievert.item_blacklist.line.Backends;

/**
 * The Fabric loot fixture on the 1.21.x line: a MODIFY_DROPS listener that adds to one test
 * table, for loot_additions_filtered. Fabric API has that event from its 1.21.6 builds on, so
 * it comes from a backend picked for the running release: {@code LootFixtureUntil1_21_6}
 * (installs nothing) or {@code LootFixtureSince1_21_6} (the listener; v1_21_6's own); both
 * ship in the line's test jar.
 */
final class LootFixture {
    /** The calls that differ; each backend implements them for its window. */
    interface Backend {
        void register();
    }

    private LootFixture() {
    }

    /** Called by the test mod's main entrypoint, after the scenarios are registered. */
    static void register() {
        Backends.pick("LootFixture", Backend.class,
                Backends.until("1.21.6",
                        "net.sievert.item_blacklist.fabric.gametest.LootFixtureUntil1_21_6"),
                Backends.since("1.21.6",
                        "net.sievert.item_blacklist.fabric.gametest.LootFixtureSince1_21_6"))
                .register();
    }
}
