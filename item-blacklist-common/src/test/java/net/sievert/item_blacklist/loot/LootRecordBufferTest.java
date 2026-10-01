package net.sievert.item_blacklist.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.sievert.item_blacklist.report.RemovalReport;
import net.sievert.item_blacklist.report.ReportSnapshot;
import net.sievert.item_blacklist.report.ReportSnapshot.Kind;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The buffer in front of the report: loot records wait for commit(), which hands them on once
 * and in order, a buffer dropped without commit leaves nothing, and every other kind of record
 * passes at once.
 */
class LootRecordBufferTest {
    @Test
    void holdsLootUntilCommit() {
        RemovalReport report = new RemovalReport();
        LootRecordBuffer buffer = new LootRecordBuffer(report);
        buffer.lootRemoval("t:x", "minecraft:b");
        buffer.lootRemoval("t:x", "minecraft:a");
        buffer.lootRemoval("t:y", "#minecraft:c");
        assertEquals(Map.of(), report.flush(false).entries());
        buffer.commit();
        assertEquals(Map.of(Kind.LOOT, Map.of(
                        "t:x", Set.of("minecraft:a", "minecraft:b"),
                        "t:y", Set.of("#minecraft:c"))),
                report.flush(false).entries());
    }

    @Test
    void commitOnce() {
        RemovalReport report = new RemovalReport();
        LootRecordBuffer buffer = new LootRecordBuffer(report);
        buffer.lootRemoval("t:x", "minecraft:oak_planks");
        buffer.commit();
        assertEquals(1, report.flush(false).count(Kind.LOOT));
        buffer.commit();
        assertEquals(0, report.flush(false).count(Kind.LOOT));
    }

    @Test
    void forwardsOtherKinds() {
        RemovalReport report = new RemovalReport();
        LootRecordBuffer buffer = new LootRecordBuffer(report);
        buffer.blacklistedItem("minecraft:oak_planks");
        buffer.blacklistedTag("item", "minecraft:planks");
        buffer.blacklistedPotion("minecraft:strength");
        buffer.blacklistedEnchantment("minecraft:mending");
        buffer.tagRemoval("item", "minecraft:planks", "minecraft:oak_planks");
        buffer.recipeRemoval("minecraft:stick", "#minecraft:planks");
        buffer.brewingRemoval("minecraft:awkward+minecraft:blaze_powder", "minecraft:strength");
        buffer.loaderRemoval("compost", "minecraft:beetroot_seeds");
        buffer.tradeRefusal("minecraft:cleric", "minecraft:rabbit_foot");
        // No commit: these reached the report at once.
        ReportSnapshot flushed = report.flush(false);
        for (Kind kind : Kind.values()) {
            assertEquals(kind == Kind.LOOT ? 0 : 1, flushed.count(kind), kind.name());
        }
    }

    @Test
    void noCommitNoRecords() {
        RemovalReport report = new RemovalReport();
        new LootRecordBuffer(report).lootRemoval("t:x", "minecraft:oak_planks");
        assertEquals(0, report.flush(false).count(Kind.LOOT));
    }
}
