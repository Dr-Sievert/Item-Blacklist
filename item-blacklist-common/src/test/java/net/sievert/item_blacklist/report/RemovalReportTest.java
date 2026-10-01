package net.sievert.item_blacklist.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.sievert.item_blacklist.report.ReportSnapshot.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * The per-server report: every Recorder call lands as its (kind, subject, cause) pair, counts
 * are of distinct pairs, records from many threads are all kept, and a flush numbers, copies
 * and resets.
 */
class RemovalReportTest {
    @Test
    void mapsEveryCallToItsKindSubjectAndCause() {
        RemovalReport report = new RemovalReport();
        report.blacklistedItem("minecraft:stone");
        report.blacklistedPotion("minecraft:strength");
        report.blacklistedEnchantment("minecraft:mending");
        report.blacklistedTag("item", "minecraft:planks");
        report.tagRemoval("block", "minecraft:mineable/axe", "minecraft:oak_planks");
        report.recipeRemoval("minecraft:stick", "#minecraft:planks");
        report.lootRemoval("minecraft:chests/village", "minecraft:oak_planks");
        report.brewingRemoval("minecraft:awkward+minecraft:blaze_powder", "minecraft:strength");
        report.loaderRemoval("compost", "minecraft:beetroot_seeds");
        report.tradeRefusal("minecraft:cleric", "minecraft:rabbit_foot");
        ReportSnapshot s = report.flush(false);

        assertEquals(pairs(Map.of("minecraft:stone", Set.of(""))), s.entries().get(Kind.ITEM));
        assertEquals(pairs(Map.of("minecraft:strength", Set.of(""))), s.entries().get(Kind.POTION));
        assertEquals(pairs(Map.of("minecraft:mending", Set.of(""))),
                s.entries().get(Kind.ENCHANTMENT));
        assertEquals(pairs(Map.of("#minecraft:planks", Set.of("item"))), s.entries().get(Kind.TAG));
        assertEquals(pairs(Map.of("minecraft:oak_planks", Set.of("block #minecraft:mineable/axe"))),
                s.entries().get(Kind.TAG_ENTRY));
        assertEquals(pairs(Map.of("minecraft:stick", Set.of("#minecraft:planks"))),
                s.entries().get(Kind.RECIPE));
        assertEquals(pairs(Map.of("minecraft:chests/village", Set.of("minecraft:oak_planks"))),
                s.entries().get(Kind.LOOT));
        assertEquals(pairs(Map.of("minecraft:awkward+minecraft:blaze_powder",
                Set.of("minecraft:strength"))), s.entries().get(Kind.BREWING));
        assertEquals(pairs(Map.of("compost", Set.of("minecraft:beetroot_seeds"))),
                s.entries().get(Kind.LOADER));
        assertEquals(pairs(Map.of("minecraft:cleric", Set.of("minecraft:rabbit_foot"))),
                s.entries().get(Kind.TRADE));
        for (Kind kind : Kind.values()) {
            assertEquals(1, s.count(kind), kind.name());
        }
    }

    @Test
    void countsDistinctPairs() {
        RemovalReport report = new RemovalReport();
        report.recipeRemoval("minecraft:stick", "#minecraft:planks");
        report.recipeRemoval("minecraft:stick", "#minecraft:planks");
        report.recipeRemoval("minecraft:stick", "minecraft:oak_planks");
        report.blacklistedTag("item", "minecraft:planks");
        report.blacklistedTag("block", "minecraft:planks");
        report.blacklistedTag("item", "minecraft:planks");
        ReportSnapshot s = report.flush(false);
        assertEquals(2, s.count(Kind.RECIPE), "one subject, two causes");
        assertEquals(Set.of("minecraft:stick"), s.subjects(Kind.RECIPE));
        assertEquals(2, s.count(Kind.TAG), "an item tag and a block tag of one id");
        assertEquals(0, s.count(Kind.LOOT));
        assertEquals(Set.of(), s.subjects(Kind.LOOT));
    }

    @Test
    void keepsEveryRecordOfEightThreads() throws Exception {
        RemovalReport report = new RemovalReport();
        int threads = 8;
        int calls = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> done = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int thread = t;
                done.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < calls; i++) {
                        report.lootRemoval("t:" + thread, "i:" + i);
                        report.blacklistedItem("i:" + i);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : done) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }
        ReportSnapshot s = report.flush(false);
        assertEquals(threads * calls, s.count(Kind.LOOT));
        assertEquals(threads, s.subjects(Kind.LOOT).size());
        assertEquals(calls, s.count(Kind.ITEM), "the same item from eight threads counts once");
    }

    @Test
    void flushNumbersAndResetsAlsoWhenEmpty() {
        RemovalReport report = new RemovalReport();
        assertEquals(0, report.lastFlush().number());
        assertTrue(report.lastFlush().entries().isEmpty());

        report.blacklistedItem("minecraft:stone");
        ReportSnapshot first = report.flush(false);
        assertEquals(1, first.number());
        assertFalse(first.reloadFailed());
        assertEquals(1, first.count(Kind.ITEM));
        assertEquals(first, report.lastFlush());

        ReportSnapshot second = report.flush(true);
        assertEquals(2, second.number());
        assertTrue(second.reloadFailed());
        assertTrue(second.entries().isEmpty(), "the first flush reset the records");
        assertEquals(0, second.count(Kind.ITEM));

        ReportSnapshot third = report.flush(false);
        assertEquals(3, third.number());
        assertEquals(third, report.lastFlush());
        assertEquals(1, first.count(Kind.ITEM), "a flushed snapshot does not change afterwards");
    }

    @Test
    void recordsNullAsEmptyText() {
        RemovalReport report = new RemovalReport();
        report.blacklistedItem(null);
        report.recipeRemoval(null, null);
        ReportSnapshot s = report.flush(false);
        assertEquals(Set.of(""), s.subjects(Kind.ITEM));
        assertEquals(pairs(Map.of("", Set.of(""))), s.entries().get(Kind.RECIPE));
    }

    @Test
    void ordersSubjectsPathFirst() {
        RemovalReport report = new RemovalReport();
        report.blacklistedItem("b:a");
        report.blacklistedItem("a:b");
        report.blacklistedItem("a:a");
        ReportSnapshot s = report.flush(false);
        assertEquals(List.of("a:a", "b:a", "a:b"), new ArrayList<>(s.subjects(Kind.ITEM)));
    }

    @Test
    void aSnapshotCannotBeChanged() {
        RemovalReport report = new RemovalReport();
        report.recipeRemoval("minecraft:stick", "#minecraft:planks");
        ReportSnapshot s = report.flush(false);
        assertThrows(UnsupportedOperationException.class, () -> s.entries().remove(Kind.RECIPE));
        assertThrows(UnsupportedOperationException.class,
                () -> s.entries().get(Kind.RECIPE).put("x:y", null));
        SortedSet<String> causes = s.entries().get(Kind.RECIPE).get("minecraft:stick");
        assertThrows(UnsupportedOperationException.class, () -> causes.add("x:y"));
        assertThrows(UnsupportedOperationException.class,
                () -> s.subjects(Kind.RECIPE).add("x:y"));
    }

    /** A plain map of the expected pairs, compared with the snapshot's sorted maps by content. */
    private static Map<String, Set<String>> pairs(Map<String, Set<String>> expected) {
        return new TreeMap<>(expected);
    }
}
