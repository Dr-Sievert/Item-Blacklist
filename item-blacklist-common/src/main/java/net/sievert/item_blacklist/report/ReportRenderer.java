package net.sievert.item_blacklist.report;

import net.sievert.item_blacklist.id.IdText;
import net.sievert.item_blacklist.log.LogTag;
import net.sievert.item_blacklist.report.ReportSnapshot.Kind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * The text of a flushed report: a header line and the summary lines, then with "Detailed Log"
 * the detail lines, grouped by what was blacklisted. Pure: a snapshot in, lines out, no game
 * type and no log call, so the unit tests and every release's scenarios read the same text.
 * Lifecycle logs each line through {@code Log.info(line.tag(), "{}", line.message())}, outside
 * the report's lock; the message is an argument, never a format, so a text holding "{}" stays.
 */
public final class ReportRenderer {
    /**
     * One log line: its tag and its message, without the "Item Blacklist [TAG] " prefix that
     * Log adds.
     *
     * @param tag the line's tag
     * @param message the text after the tag
     */
    public record Line(LogTag tag, String message) {
        /** "[TAG] message": what render returns, and what follows "Item Blacklist " in the log. */
        public String text() {
            return "[" + tag.name() + "] " + message;
        }
    }

    /** Tag registries in the order the summary and the details print them; others follow. */
    static final List<String> REGISTRY_ORDER = List.of("item", "block", "enchantment", "potion");

    /** Registries: the known ones in REGISTRY_ORDER, then the others in String order. */
    static final Comparator<String> REGISTRY = Comparator
            .comparingInt(ReportRenderer::registryRank)
            .thenComparing(Comparator.naturalOrder());

    /**
     * Ids and tags ("ns:path", "#ns:path") by IdText.PATH_FIRST, the old detailed log's order;
     * a text without ':' after all of them, in String order. String order breaks ties, so no two
     * different strings compare equal. PATH_FIRST also sees texts that hold a ':' but are no id
     * (NeoForge's brewing mix text), which it compares without throwing.
     */
    static final Comparator<String> ID_ORDER = Comparator
            .comparingInt((String text) -> text.indexOf(':') >= 0 ? 0 : 1)
            .thenComparing((String a, String b) -> a.indexOf(':') >= 0 && b.indexOf(':') >= 0
                    ? IdText.PATH_FIRST.compare(a, b) : 0)
            .thenComparing(Comparator.naturalOrder());

    /** TAG_ENTRY causes "registry #tag": by registry, then by tag. */
    static final Comparator<String> TAG_ENTRY_ORDER = Comparator
            .comparing(ReportRenderer::registryOf, REGISTRY)
            .thenComparing(ReportRenderer::tagOf, ID_ORDER);

    /** The indent of a detail line under its header, as the old detailed log had it. */
    private static final String INDENT = "  ";

    private ReportRenderer() {
    }

    /**
     * The lines as "[TAG] message", without Log's "Item Blacklist " prefix: the summary, then
     * with {@code detailed} the details.
     */
    public static List<String> render(ReportSnapshot snapshot, boolean detailed) {
        List<String> texts = new ArrayList<>();
        for (Line line : lines(snapshot, detailed)) {
            texts.add(line.text());
        }
        return List.copyOf(texts);
    }

    /**
     * The same lines with their tags, for Lifecycle to log: the header, the summary lines whose
     * number is above 0, then with {@code detailed} one block per blacklisted thing.
     */
    public static List<Line> lines(ReportSnapshot snapshot, boolean detailed) {
        List<Line> out = new ArrayList<>();
        summary(snapshot, out);
        if (detailed) {
            details(snapshot, out);
        }
        return List.copyOf(out);
    }

    /** One when n is 1, else many. */
    static String plural(int n, String one, String many) {
        return n == 1 ? one : many;
    }

    /** "item" of the TAG_ENTRY cause "item #minecraft:logs"; the whole text without a space. */
    static String registryOf(String cause) {
        int space = cause.indexOf(' ');
        return space < 0 ? cause : cause.substring(0, space);
    }

    /** "#minecraft:logs" of the TAG_ENTRY cause "item #minecraft:logs"; "" without a space. */
    static String tagOf(String cause) {
        int space = cause.indexOf(' ');
        return space < 0 ? "" : cause.substring(space + 1);
    }

    /** The index in REGISTRY_ORDER, else its size, so unknown registries follow the known. */
    static int registryRank(String registry) {
        int index = REGISTRY_ORDER.indexOf(registry);
        return index < 0 ? REGISTRY_ORDER.size() : index;
    }

    /** The tag of a registry's lines: ENCHANTMENT, POTION, else TAG (items, blocks, others). */
    static LogTag registryTag(String registry) {
        return switch (registry) {
            case "enchantment" -> LogTag.ENCHANTMENT;
            case "potion" -> LogTag.POTION;
            default -> LogTag.TAG;
        };
    }

    /**
     * The header, then one line per removal kind with a number above 0. The header always
     * prints, so every flush shows in the log, an empty one too; it says ", nothing removed"
     * when no line follows it.
     */
    private static void summary(ReportSnapshot s, List<Line> out) {
        List<Line> removals = new ArrayList<>();

        int cleared = s.count(Kind.TAG);
        if (cleared > 0) {
            removals.add(new Line(LogTag.TAG,
                    "Cleared " + cleared + " blacklisted " + plural(cleared, "tag", "tags")));
        }

        // One line per registry: an item tag and a block tag of one id are counted apart.
        Map<String, Integer> perRegistry = new TreeMap<>(REGISTRY);
        for (SortedSet<String> causes : entries(s, Kind.TAG_ENTRY).values()) {
            for (String cause : causes) {
                perRegistry.merge(registryOf(cause), 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> registry : perRegistry.entrySet()) {
            int n = registry.getValue();
            removals.add(new Line(registryTag(registry.getKey()),
                    "Removed " + n + " blacklisted " + registry.getKey() + " "
                            + plural(n, "entry", "entries") + " from " + registry.getKey()
                            + " tags"));
        }

        // Recipes count subjects: a recipe removed for two causes is one recipe.
        int recipes = s.subjects(Kind.RECIPE).size();
        if (recipes > 0) {
            removals.add(new Line(LogTag.RECIPE,
                    "Removed " + recipes + " disabled " + plural(recipes, "recipe", "recipes")));
        }

        int brewing = s.subjects(Kind.BREWING).size();
        if (brewing > 0) {
            removals.add(new Line(LogTag.RECIPE, "Removed " + brewing + " disabled brewing "
                    + plural(brewing, "recipe", "recipes")));
        }

        int loot = s.count(Kind.LOOT);
        if (loot > 0) {
            int tables = s.subjects(Kind.LOOT).size();
            removals.add(new Line(LogTag.LOOT, "Removed " + loot + " blacklisted loot "
                    + plural(loot, "entry", "entries") + " from " + tables + " loot "
                    + plural(tables, "table", "tables")));
        }

        int loader = s.count(Kind.LOADER);
        if (loader > 0) {
            int tables = s.subjects(Kind.LOADER).size();
            removals.add(new Line(LogTag.ITEM, "Removed " + loader + " blacklisted "
                    + plural(loader, "entry", "entries") + " from " + tables + " loader "
                    + plural(tables, "table", "tables")));
        }

        int trades = s.count(Kind.TRADE);
        if (trades > 0) {
            removals.add(new Line(LogTag.TRADE, "Refused " + trades + " blacklisted trade "
                    + plural(trades, "offer", "offers")));
        }

        out.add(new Line(LogTag.INIT, "Removal report " + s.number()
                + (s.reloadFailed() ? " after a failed reload" : "")
                + (removals.isEmpty() ? ", nothing removed" : "")));
        out.addAll(removals);
    }

    /**
     * One block per header: a tag, an item, a potion or an enchantment, in four sections, each
     * sorted by ID_ORDER; under each header what happened to it. A cause that is a text and no
     * id (a NeoForge brewing mix, a trade reason) is a header of its own among the items.
     */
    private static void details(ReportSnapshot s, List<Line> out) {
        Map<String, Detail> byHeader = new HashMap<>();
        Function<String, Detail> detail = header -> byHeader.computeIfAbsent(header,
                h -> new Detail());

        // The configured entries are headers even when nothing was removed for them.
        for (Kind kind : List.of(Kind.ITEM, Kind.POTION, Kind.ENCHANTMENT)) {
            for (String subject : entries(s, kind).keySet()) {
                detail.apply(subject);
            }
        }
        for (Map.Entry<String, SortedSet<String>> tag : entries(s, Kind.TAG).entrySet()) {
            for (String registry : tag.getValue()) {
                if (!registry.isEmpty()) {
                    detail.apply(tag.getKey()).cleared.add(registry);
                }
            }
        }
        for (Map.Entry<String, SortedSet<String>> entry : entries(s, Kind.TAG_ENTRY).entrySet()) {
            for (String cause : entry.getValue()) {
                if (!cause.isEmpty()) {
                    detail.apply(entry.getKey()).tagEntries.add(cause);
                }
            }
        }
        // The removal kinds are filed under their causes: the recipe under the item it needed.
        byCause(s, Kind.RECIPE, detail, d -> d.recipes);
        byCause(s, Kind.BREWING, detail, d -> d.brewing);
        byCause(s, Kind.LOOT, detail, d -> d.loot);
        byCause(s, Kind.LOADER, detail, d -> d.loader);
        byCause(s, Kind.TRADE, detail, d -> d.trades);

        Set<String> enchantments = new HashSet<>(entries(s, Kind.ENCHANTMENT).keySet());
        Set<String> potions = new HashSet<>(entries(s, Kind.POTION).keySet());
        for (Map.Entry<String, SortedSet<String>> entry : entries(s, Kind.TAG_ENTRY).entrySet()) {
            for (String cause : entry.getValue()) {
                String registry = registryOf(cause);
                if (registry.equals("enchantment")) {
                    enchantments.add(entry.getKey());
                } else if (registry.equals("potion")) {
                    potions.add(entry.getKey());
                }
            }
        }

        Map<Section, SortedSet<String>> sections = new EnumMap<>(Section.class);
        for (Section section : Section.values()) {
            sections.put(section, new TreeSet<>(ID_ORDER));
        }
        for (String header : byHeader.keySet()) {
            Section section;
            if (header.startsWith("#")) {
                section = Section.TAGS;
            } else if (enchantments.contains(header)) {
                section = Section.ENCHANTMENTS;
            } else if (potions.contains(header)) {
                section = Section.POTIONS;
            } else {
                section = Section.ITEMS;
            }
            sections.get(section).add(header);
        }

        for (Section section : Section.values()) {
            for (String header : sections.get(section)) {
                out.add(new Line(section.tag, "Blacklist details for " + header));
                Detail d = byHeader.get(header);
                for (String registry : d.cleared) {
                    out.add(new Line(LogTag.TAG,
                            INDENT + "Cleared as a blacklisted " + registry + " tag"));
                }
                for (String cause : d.tagEntries) {
                    out.add(new Line(LogTag.TAG, INDENT + "Removed from " + registryOf(cause)
                            + " tag: " + tagOf(cause)));
                }
                for (String recipe : d.recipes) {
                    out.add(new Line(LogTag.RECIPE, INDENT + "Removed recipe: " + recipe));
                }
                for (String mix : d.brewing) {
                    out.add(new Line(LogTag.RECIPE, INDENT + "Removed brewing recipe: " + mix));
                }
                for (String table : d.loot) {
                    out.add(new Line(LogTag.LOOT, INDENT + "Removed from loot table: " + table));
                }
                for (String table : d.loader) {
                    out.add(new Line(LogTag.ITEM,
                            INDENT + "Removed from loader table: " + table));
                }
                for (String merchant : d.trades) {
                    out.add(new Line(LogTag.TRADE, INDENT + "Refused trade: " + merchant));
                }
            }
        }
    }

    /** Files every subject of a kind under each of its non-empty causes, in the set it picks. */
    private static void byCause(ReportSnapshot s, Kind kind, Function<String, Detail> detail,
            Function<Detail, SortedSet<String>> set) {
        for (Map.Entry<String, SortedSet<String>> subject : entries(s, kind).entrySet()) {
            for (String cause : subject.getValue()) {
                if (!cause.isEmpty()) {
                    set.apply(detail.apply(cause)).add(subject.getKey());
                }
            }
        }
    }

    /** The subjects of a kind with their causes; empty for a kind without a record. */
    private static SortedMap<String, SortedSet<String>> entries(ReportSnapshot s, Kind kind) {
        SortedMap<String, SortedSet<String>> map = s.entries().get(kind);
        return map == null ? Collections.emptySortedMap() : map;
    }

    /** What one header collects; one set per kind of detail line, each in its print order. */
    private static final class Detail {
        final SortedSet<String> cleared = new TreeSet<>(REGISTRY);
        final SortedSet<String> tagEntries = new TreeSet<>(TAG_ENTRY_ORDER);
        final SortedSet<String> recipes = new TreeSet<>(ID_ORDER);
        final SortedSet<String> brewing = new TreeSet<>();
        final SortedSet<String> loot = new TreeSet<>(ID_ORDER);
        final SortedSet<String> loader = new TreeSet<>();
        final SortedSet<String> trades = new TreeSet<>();
    }

    /** The four header sections, in print order, with the tag of their header line. */
    private enum Section {
        TAGS(LogTag.TAG),
        ITEMS(LogTag.ITEM),
        POTIONS(LogTag.POTION),
        ENCHANTMENTS(LogTag.ENCHANTMENT);

        final LogTag tag;

        Section(LogTag tag) {
            this.tag = tag;
        }
    }
}
