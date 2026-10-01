package net.sievert.item_blacklist.gametest;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.ServerState;
import net.sievert.item_blacklist.report.RemovalReport;
import net.sievert.item_blacklist.report.ReportRenderer;
import net.sievert.item_blacklist.report.ReportSnapshot;
import net.sievert.item_blacklist.report.ReportSnapshot.Kind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The logging scenarios (prefix log_): the running server's reports as the fixture made them,
 * and the renderer on this release's Java and jar. Each is synchronous and holds before and
 * after a reload: it reads the last flush at call time, or builds a report of its own, and
 * changes no state. No expectation differs by release, since the ids read are fixture entries
 * or vanilla tag and loot contents present on every release.
 */
public final class LoggingScenarios {
    /** The summary of the canonical records; ReportRendererTest.SUMMARY is the same text. */
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

    /** The summary, then the detail lines; ReportRendererTest.DETAILED is the same text. */
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

    private LoggingScenarios() {
    }

    /**
     * The log_ scenarios; called by ItemBlacklistGameTests.register. log_report_flushed_at_start
     * and log_report_rendered read the records of recipes, loot and brewing.
     */
    public static void register(BiConsumer<String, Consumer<GameTestHelper>> sink) {
        sink.accept("log_renderer_on_this_release", LoggingScenarios::rendererOnThisRelease);
        sink.accept("log_report_config_headers", LoggingScenarios::reportConfigHeaders);
        sink.accept("log_report_flushed_at_start", LoggingScenarios::reportFlushedAtStart);
        sink.accept("log_report_registries_apart", LoggingScenarios::reportRegistriesApart);
        sink.accept("log_report_rendered", LoggingScenarios::reportRendered);
    }

    /**
     * One flush per completed reload, the last one of a good reload, with the records every
     * reload makes: tag entries, recipes, loot entries and brewing mixes. The old start-only
     * report would give number 1 against two reloads after reload_keeps_filters.
     */
    static void reportFlushedAtStart(GameTestHelper helper) {
        ServerState state = state(helper);
        ReportSnapshot last = state.report().lastFlush();
        Check.isTrue(helper, last.number() >= 1,
                "Expected a flushed report, the last is number " + last.number());
        Check.equal(helper, last.number(), state.reloads(),
                "the last report's number (one flush per reload)");
        Check.isTrue(helper, !last.reloadFailed(),
                "Expected report " + last.number() + " of a good reload");
        Check.isTrue(helper, Blacklist.recorder() == state.report(),
                "Expected Blacklist.recorder() to be the running server's report");
        for (Kind kind : List.of(Kind.TAG_ENTRY, Kind.RECIPE, Kind.LOOT, Kind.BREWING)) {
            Check.isTrue(helper, last.count(kind) > 0, "Expected " + kind + " records in report "
                    + last.number() + ": " + counts(last));
        }
        helper.succeed();
    }

    /**
     * Tag entries keep their registry, and cleared tags name theirs: charcoal leaves the item
     * tag #coals, oak planks the block tag #mineable/axe (and no item tag of that id), mending
     * the enchantment tag #treasure; #planks is cleared as an item tag, #curse as an
     * enchantment tag.
     */
    static void reportRegistriesApart(GameTestHelper helper) {
        ReportSnapshot last = state(helper).report().lastFlush();
        SortedSet<String> charcoal = causes(last, Kind.TAG_ENTRY, "minecraft:charcoal");
        Check.isTrue(helper, charcoal.contains("item #minecraft:coals"),
                "Expected charcoal removed from the item tag #minecraft:coals, was " + charcoal);
        SortedSet<String> planks = causes(last, Kind.TAG_ENTRY, "minecraft:oak_planks");
        Check.isTrue(helper, planks.contains("block #minecraft:mineable/axe"),
                "Expected oak planks removed from the block tag #minecraft:mineable/axe, was "
                        + planks);
        Check.isTrue(helper, !planks.contains("item #minecraft:mineable/axe"),
                "Expected no item entry for the block tag #minecraft:mineable/axe, was "
                        + planks);
        SortedSet<String> mending = causes(last, Kind.TAG_ENTRY, "minecraft:mending");
        Check.isTrue(helper, mending.contains("enchantment #minecraft:treasure"),
                "Expected mending removed from the enchantment tag #minecraft:treasure, was "
                        + mending);
        SortedSet<String> plankTag = causes(last, Kind.TAG, "#minecraft:planks");
        Check.isTrue(helper, plankTag.contains("item"),
                "Expected #minecraft:planks cleared as an item tag, was " + plankTag);
        SortedSet<String> curse = causes(last, Kind.TAG, "#minecraft:curse");
        Check.isTrue(helper, curse.contains("enchantment"),
                "Expected #minecraft:curse cleared as an enchantment tag, was " + curse);
        helper.succeed();
    }

    /**
     * The live report renders: the header, the summary lines, the summary before the details,
     * the three kinds of header, and no ANSI escape.
     */
    static void reportRendered(GameTestHelper helper) {
        ReportSnapshot last = state(helper).report().lastFlush();
        List<String> summary = ReportRenderer.render(last, false);
        List<String> detailed = ReportRenderer.render(last, true);
        Check.equal(helper, summary.get(0), "[INIT] Removal report " + last.number(),
                "the header line");
        for (String start : List.of("[TAG] Cleared ", "[TAG] Removed ", "[RECIPE] Removed ",
                "[LOOT] Removed ")) {
            Check.isTrue(helper, summary.stream().anyMatch(line -> line.startsWith(start)),
                    "Expected a summary line starting '" + start + "' in " + summary);
        }
        Check.isTrue(helper, summary.stream().anyMatch(line -> line.startsWith("[RECIPE] Removed ")
                        && line.contains(" disabled brewing ")),
                "Expected the brewing line in " + summary);
        Check.equal(helper, detailed.subList(0, summary.size()), summary,
                "the detailed report's first lines");
        for (String header : List.of("[TAG] Blacklist details for #minecraft:planks",
                "[ITEM] Blacklist details for minecraft:charcoal",
                "[ENCHANTMENT] Blacklist details for minecraft:mending")) {
            Check.isTrue(helper, detailed.contains(header),
                    "Expected '" + header + "' in the detailed report");
        }
        Check.isTrue(helper, detailed.stream().noneMatch(line -> line.indexOf('\u001B') >= 0),
                "Expected no ANSI escape in the report");
        helper.succeed();
    }

    /**
     * Every report names the configured entries as headers, as the old detailed log did for
     * every configured entry, and files the potions among the potions; the fixture's unknown
     * entries never appear.
     */
    static void reportConfigHeaders(GameTestHelper helper) {
        ReportSnapshot last = state(helper).report().lastFlush();
        for (String item : List.of("minecraft:oak_planks", "minecraft:charcoal",
                "minecraft:soul_sand", "minecraft:soul_soil", "minecraft:rabbit_foot",
                "minecraft:lingering_potion", "minecraft:beetroot_seeds",
                "minecraft:phantom_membrane")) {
            Check.isTrue(helper, last.subjects(Kind.ITEM).contains(item),
                    "Expected the item header " + item + " in report " + last.number() + ": "
                            + last.subjects(Kind.ITEM));
        }
        for (String potion : List.of("minecraft:strength", "minecraft:strong_strength",
                "minecraft:long_strength")) {
            Check.isTrue(helper, last.subjects(Kind.POTION).contains(potion),
                    "Expected the potion header " + potion + ": " + last.subjects(Kind.POTION));
        }
        Check.isTrue(helper, last.subjects(Kind.ENCHANTMENT).contains("minecraft:mending"),
                "Expected the enchantment header minecraft:mending: "
                        + last.subjects(Kind.ENCHANTMENT));
        for (Kind kind : List.of(Kind.ITEM, Kind.POTION, Kind.ENCHANTMENT)) {
            Check.isTrue(helper,
                    last.subjects(kind).stream().noneMatch(id -> id.startsWith("mod_id:")),
                    "Expected no unknown entry among the " + kind + " headers: "
                            + last.subjects(kind));
        }
        Check.isTrue(helper, ReportRenderer.render(last, true)
                        .contains("[POTION] Blacklist details for minecraft:strength"),
                "Expected the potion section to hold minecraft:strength");
        helper.succeed();
    }

    /**
     * The renderer and the record mapping give the same text on every release's Java and in
     * each loader's shipped jar; the unit tests run on the 26.x line only. A report of its own,
     * so no hook and no live state is read.
     */
    static void rendererOnThisRelease(GameTestHelper helper) {
        RemovalReport report = new RemovalReport();
        record(report);
        ReportSnapshot flushed = report.flush(false);
        Check.equal(helper, ReportRenderer.render(flushed, false), SUMMARY,
                "the summary of the built report");
        Check.equal(helper, ReportRenderer.render(flushed, true), DETAILED,
                "the detailed built report");
        Check.equal(helper, report.lastFlush().number(), 1, "the built report's number");
        helper.succeed();
    }

    /**
     * The canonical records, in the shapes the callers pass them (tags without '#', the mix
     * text of BrewingText.mix, a merchant's entity type id); ReportRendererTest.record is the
     * same text.
     */
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

    /** The running server's state; fails the scenario without one. */
    private static ServerState state(GameTestHelper helper) {
        ServerState state = Blacklist.serverState();
        Check.isTrue(helper, state != null, "Expected a running server state");
        return state;
    }

    /** The causes of one subject of a kind; empty when the subject or the kind is absent. */
    private static SortedSet<String> causes(ReportSnapshot report, Kind kind, String subject) {
        SortedMap<String, SortedSet<String>> map = report.entries().get(kind);
        SortedSet<String> causes = map == null ? null : map.get(subject);
        return causes == null ? Collections.emptySortedSet() : causes;
    }

    /** "ITEM=8 TAG=2 ..." for failure messages. */
    private static String counts(ReportSnapshot report) {
        StringBuilder text = new StringBuilder();
        for (Kind kind : Kind.values()) {
            text.append(kind).append('=').append(report.count(kind)).append(' ');
        }
        return text.toString().trim();
    }

    /** a, then b, unmodifiable. */
    private static List<String> concat(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return List.copyOf(all);
    }
}
