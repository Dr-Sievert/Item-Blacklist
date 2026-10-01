package net.sievert.item_blacklist.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.sievert.item_blacklist.log.LogTag;
import net.sievert.item_blacklist.report.ReportRenderer.Line;
import net.sievert.item_blacklist.report.ReportSnapshot.Kind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * The report's text: the templates at singular and plural, distinct counts, tag registries
 * kept apart, the detail sections and their order, and the record mapping the renderer reads.
 * The scenario log_renderer_on_this_release renders the same records on every release's Java
 * and jar; its SUMMARY and DETAILED are this class's text.
 */
class ReportRendererTest {
    /** The canonical records; LoggingScenarios.record is the same text. */
    static void record(RemovalReport report) {
        report.blacklistedItem("minecraft:oak_planks");
        report.blacklistedPotion("minecraft:strength");
        report.blacklistedEnchantment("minecraft:mending");
        report.blacklistedTag("item", "minecraft:planks");
        report.tagRemoval("item", "minecraft:coals", "minecraft:charcoal");
        report.tagRemoval("block", "minecraft:mineable/axe", "minecraft:oak_planks");
        report.tagRemoval("enchantment", "minecraft:treasure", "minecraft:mending");
        report.recipeRemoval("minecraft:stick", "#minecraft:planks");
        report.recipeRemoval("minecraft:oak_planks", "minecraft:oak_planks");
        report.brewingRemoval("input=minecraft:awkward, ingredient=[minecraft:blaze_powder],"
                + " output=minecraft:strength", "minecraft:strength");
        report.lootRemoval("minecraft:entities/rabbit", "minecraft:rabbit_foot");
        report.loaderRemoval("compost", "minecraft:beetroot_seeds");
        report.tradeRefusal("minecraft:villager", "minecraft:rabbit_foot");
    }

    /** The summary of the canonical records, flushed once. */
    static final List<String> SUMMARY = List.of(
            "[INIT] Removal report 1",
            "[TAG] Cleared 1 blacklisted tag",
            "[TAG] Removed 1 blacklisted item entry from item tags",
            "[TAG] Removed 1 blacklisted block entry from block tags",
            "[ENCHANTMENT] Removed 1 blacklisted enchantment entry from enchantment tags",
            "[RECIPE] Removed 2 disabled recipes",
            "[RECIPE] Removed 1 disabled brewing recipe",
            "[LOOT] Removed 1 blacklisted loot entry from 1 loot table",
            "[ITEM] Removed 1 blacklisted entry from 1 loader table",
            "[TRADE] Refused 1 blacklisted trade offer");

    /** The summary, then the 17 detail lines of the canonical records. */
    static final List<String> DETAILED = concat(SUMMARY, List.of(
            "[TAG] Blacklist details for #minecraft:planks",
            "[TAG]   Cleared as a blacklisted item tag",
            "[RECIPE]   Removed recipe: minecraft:stick",
            "[ITEM] Blacklist details for minecraft:beetroot_seeds",
            "[ITEM]   Removed from loader table: compost",
            "[ITEM] Blacklist details for minecraft:charcoal",
            "[TAG]   Removed from item tag: #minecraft:coals",
            "[ITEM] Blacklist details for minecraft:oak_planks",
            "[TAG]   Removed from block tag: #minecraft:mineable/axe",
            "[RECIPE]   Removed recipe: minecraft:oak_planks",
            "[ITEM] Blacklist details for minecraft:rabbit_foot",
            "[LOOT]   Removed from loot table: minecraft:entities/rabbit",
            "[TRADE]   Refused trade: minecraft:villager",
            "[POTION] Blacklist details for minecraft:strength",
            "[RECIPE]   Removed brewing recipe: input=minecraft:awkward,"
                    + " ingredient=[minecraft:blaze_powder], output=minecraft:strength",
            "[ENCHANTMENT] Blacklist details for minecraft:mending",
            "[TAG]   Removed from enchantment tag: #minecraft:treasure"));

    /** a, then b, unmodifiable. */
    private static List<String> concat(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return List.copyOf(all);
    }

    /** A report of its own with the given records, flushed once. */
    private static ReportSnapshot flushed(boolean failed, Consumer<RemovalReport> records) {
        RemovalReport report = new RemovalReport();
        records.accept(report);
        return report.flush(failed);
    }

    @Test
    void rendersTheSummary() {
        assertEquals(SUMMARY,
                ReportRenderer.render(flushed(false, ReportRendererTest::record), false));
    }

    @Test
    void rendersTheDetailsAfterTheSummary() {
        List<String> detailed =
                ReportRenderer.render(flushed(false, ReportRendererTest::record), true);
        assertEquals(DETAILED, detailed);
        assertEquals(SUMMARY.size() + 17, detailed.size());
    }

    @Test
    void linesCarryTheirTags() {
        List<Line> lines = ReportRenderer.lines(flushed(false, ReportRendererTest::record), true);
        assertEquals(ReportRenderer.render(flushed(false, ReportRendererTest::record), true),
                lines.stream().map(Line::text).toList());
        assertEquals(new Line(LogTag.INIT, "Removal report 1"), lines.get(0));
        assertEquals(LogTag.ENCHANTMENT, lines.get(4).tag());
        assertEquals(new Line(LogTag.RECIPE, "  Removed recipe: minecraft:stick"), lines.get(12));
        assertEquals("[TAG] x", new Line(LogTag.TAG, "x").text());
    }

    @Test
    void anEmptyReportIsOneLine() {
        ReportSnapshot empty = new RemovalReport().flush(false);
        List<String> expected = List.of("[INIT] Removal report 1, nothing removed");
        assertEquals(expected, ReportRenderer.render(empty, false));
        assertEquals(expected, ReportRenderer.render(empty, true));
    }

    @Test
    void aFailedReloadIsNamed() {
        assertEquals(List.of("[INIT] Removal report 1 after a failed reload, nothing removed"),
                ReportRenderer.render(new RemovalReport().flush(true), false));
        List<String> lines =
                ReportRenderer.render(flushed(true, ReportRendererTest::record), false);
        assertEquals("[INIT] Removal report 1 after a failed reload", lines.get(0));
        assertEquals(SUMMARY.subList(1, 10), lines.subList(1, 10));
        assertEquals(10, lines.size());
    }

    @Test
    void numbersFollowTheFlushes() {
        RemovalReport report = new RemovalReport();
        report.flush(false);
        ReportSnapshot second = report.flush(false);
        assertEquals(List.of("[INIT] Removal report 2, nothing removed"),
                ReportRenderer.render(second, false));
        assertEquals(2, report.lastFlush().number());
    }

    @Test
    void headersAreNoRemovals() {
        ReportSnapshot s = flushed(false, r -> r.blacklistedItem("minecraft:stone"));
        assertEquals(List.of("[INIT] Removal report 1, nothing removed"),
                ReportRenderer.render(s, false));
        assertEquals(List.of("[INIT] Removal report 1, nothing removed",
                        "[ITEM] Blacklist details for minecraft:stone"),
                ReportRenderer.render(s, true));
    }

    @Test
    void pluralsFromTwo() {
        ReportSnapshot s = flushed(false, r -> {
            r.blacklistedTag("item", "a:t1");
            r.blacklistedTag("item", "a:t2");
            r.tagRemoval("item", "a:t3", "a:i1");
            r.tagRemoval("item", "a:t3", "a:i2");
            r.recipeRemoval("a:r1", "a:i1");
            r.recipeRemoval("a:r2", "a:i1");
            r.brewingRemoval("m1", "a:p");
            r.brewingRemoval("m2", "a:p");
            r.lootRemoval("a:l1", "a:i1");
            r.lootRemoval("a:l2", "a:i1");
            r.loaderRemoval("compost", "a:i1");
            r.loaderRemoval("fuel", "a:i1");
            r.tradeRefusal("v1", "a:i1");
            r.tradeRefusal("v2", "a:i1");
        });
        assertEquals(List.of(
                "[INIT] Removal report 1",
                "[TAG] Cleared 2 blacklisted tags",
                "[TAG] Removed 2 blacklisted item entries from item tags",
                "[RECIPE] Removed 2 disabled recipes",
                "[RECIPE] Removed 2 disabled brewing recipes",
                "[LOOT] Removed 2 blacklisted loot entries from 2 loot tables",
                "[ITEM] Removed 2 blacklisted entries from 2 loader tables",
                "[TRADE] Refused 2 blacklisted trade offers"),
                ReportRenderer.render(s, false));
    }

    @Test
    void countsDistinctPairs() {
        ReportSnapshot s = flushed(false, r -> {
            r.lootRemoval("t:a", "x:i");
            r.lootRemoval("t:a", "x:i");
            r.lootRemoval("t:a", "x:j");
            r.lootRemoval("t:b", "x:i");
            r.recipeRemoval("r:a", "x:i");
            r.recipeRemoval("r:a", "x:j");
            r.tagRemoval("item", "t:x", "x:i");
            r.tagRemoval("item", "t:x", "x:i");
        });
        assertEquals(List.of(
                "[INIT] Removal report 1",
                "[TAG] Removed 1 blacklisted item entry from item tags",
                "[RECIPE] Removed 1 disabled recipe",
                "[LOOT] Removed 3 blacklisted loot entries from 2 loot tables"),
                ReportRenderer.render(s, false));
    }

    @Test
    void keepsTagRegistriesApart() {
        ReportSnapshot s = flushed(false, r -> {
            r.tagRemoval("block", "minecraft:logs", "minecraft:oak_log");
            r.tagRemoval("item", "minecraft:logs", "minecraft:oak_log");
        });
        assertEquals(List.of(
                "[INIT] Removal report 1",
                "[TAG] Removed 1 blacklisted item entry from item tags",
                "[TAG] Removed 1 blacklisted block entry from block tags",
                "[ITEM] Blacklist details for minecraft:oak_log",
                "[TAG]   Removed from item tag: #minecraft:logs",
                "[TAG]   Removed from block tag: #minecraft:logs"),
                ReportRenderer.render(s, true));
    }

    @Test
    void otherRegistriesFollowTheKnownOnes() {
        ReportSnapshot s = flushed(false, r -> {
            r.tagRemoval("banner_pattern", "minecraft:no_item_required", "minecraft:globe");
            r.tagRemoval("potion", "minecraft:tradeable", "minecraft:strength");
        });
        assertEquals(List.of(
                "[INIT] Removal report 1",
                "[POTION] Removed 1 blacklisted potion entry from potion tags",
                "[TAG] Removed 1 blacklisted banner_pattern entry from banner_pattern tags"),
                ReportRenderer.render(s, false));
    }

    @Test
    void detailHeadersArePathFirst() {
        ReportSnapshot s = flushed(false, r -> {
            r.blacklistedItem("b:a");
            r.blacklistedItem("a:b");
            r.blacklistedItem("a:a");
        });
        assertEquals(List.of(
                "[INIT] Removal report 1, nothing removed",
                "[ITEM] Blacklist details for a:a",
                "[ITEM] Blacklist details for b:a",
                "[ITEM] Blacklist details for a:b"),
                ReportRenderer.render(s, true));
    }

    @Test
    void idOrderPutsTextWithoutColonLast() {
        List<String> sorted = new ArrayList<>(Arrays.asList("plain text", "b:a", "#a:c", "a:a"));
        sorted.sort(ReportRenderer.ID_ORDER);
        assertEquals(List.of("a:a", "b:a", "#a:c", "plain text"), sorted);
        assertNotEquals(0, ReportRenderer.ID_ORDER.compare("#a:b", "a:b"));
    }

    @Test
    void aBareCauseIsAnItem() {
        // Without the potion's own header record, a potion that is only a brewing cause is
        // filed among the items: why Lifecycle records the configured potions before a flush.
        List<String> lines = ReportRenderer.render(
                flushed(false, r -> r.brewingRemoval("mix", "minecraft:swiftness")), true);
        assertEquals(List.of("[ITEM] Blacklist details for minecraft:swiftness",
                        "[RECIPE]   Removed brewing recipe: mix"),
                lines.subList(lines.size() - 2, lines.size()));
    }

    @Test
    void recordsMapAsTheRendererReads() {
        ReportSnapshot s = flushed(false, ReportRendererTest::record);
        assertEquals(Set.of(""), s.entries().get(Kind.ITEM).get("minecraft:oak_planks"));
        assertEquals(Set.of("item"), s.entries().get(Kind.TAG).get("#minecraft:planks"));
        assertEquals(Set.of("block #minecraft:mineable/axe"),
                s.entries().get(Kind.TAG_ENTRY).get("minecraft:oak_planks"));
        assertEquals(Set.of("#minecraft:planks"),
                s.entries().get(Kind.RECIPE).get("minecraft:stick"));
        assertEquals(Set.of("minecraft:beetroot_seeds"),
                s.entries().get(Kind.LOADER).get("compost"));
        assertEquals(1, s.count(Kind.ITEM));
        assertEquals(3, s.count(Kind.TAG_ENTRY));
        assertEquals(Set.of("minecraft:stick", "minecraft:oak_planks"), s.subjects(Kind.RECIPE));
    }

    @Test
    void rendersASnapshotBuiltDirectly() {
        ReportSnapshot s = new ReportSnapshot(3, false, Map.of());
        List<String> expected = List.of("[INIT] Removal report 3, nothing removed");
        assertEquals(expected, ReportRenderer.render(s, false));
        assertEquals(expected, ReportRenderer.render(s, true));
    }

    @Test
    void noAnsiAndNoModPrefix() {
        for (String line : ReportRenderer.render(flushed(true, ReportRendererTest::record), true)) {
            assertTrue(line.startsWith("["), line);
            assertFalse(line.contains("\u001B"), line);
            assertFalse(line.startsWith("Item Blacklist"), line);
        }
    }

    @Test
    void aTextCauseIsItsOwnHeader() {
        ReportSnapshot s = flushed(false, r -> {
            r.loaderRemoval("neoforge:brewing", "input=[minecraft:stone],"
                    + " ingredient=[minecraft:stick], output=minecraft:lingering_potion");
            r.tradeRefusal("minecraft:villager", "tipped arrow without a potion");
        });
        assertEquals(List.of(
                "[INIT] Removal report 1",
                "[ITEM] Removed 1 blacklisted entry from 1 loader table",
                "[TRADE] Refused 1 blacklisted trade offer",
                "[ITEM] Blacklist details for input=[minecraft:stone],"
                        + " ingredient=[minecraft:stick], output=minecraft:lingering_potion",
                "[ITEM]   Removed from loader table: neoforge:brewing",
                "[ITEM] Blacklist details for tipped arrow without a potion",
                "[TRADE]   Refused trade: minecraft:villager"),
                ReportRenderer.render(s, true));
    }

    @Test
    void logTagsAreTheOldNine() {
        assertEquals(List.of("CONFIG", "ENCHANTMENT", "INIT", "ITEM", "LOOT", "POTION", "RECIPE",
                        "TAG", "TRADE"),
                Arrays.stream(LogTag.values()).map(LogTag::name).toList());
    }
}
