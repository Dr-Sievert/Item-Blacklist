package net.sievert.item_blacklist.fabric.gametest;

/**
 * No loot fixture on 1.21.1 to 1.21.5: the Fabric API builds of these releases have no
 * MODIFY_DROPS event, so nothing can add to a roll behind the table's back, and
 * loot_additions_filtered passes on its control with Fixtures.lootAdditions false.
 */
public final class LootFixtureUntil1_21_6 implements LootFixture.Backend {
    @Override
    public void register() {
    }
}
