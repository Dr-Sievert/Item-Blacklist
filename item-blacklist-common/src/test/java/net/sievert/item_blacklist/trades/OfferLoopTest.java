package net.sievert.item_blacklist.trades;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * 26.2's offer loop, which the 26.1.2 guard runs: an entry that gives no offer leaves the pool,
 * one that gives an offer stays and may give again, and the loop ends when enough offers were
 * made or the pool is empty. So a set whose every entry gives none ends after one roll per
 * entry, where 26.1.2's own loop never ends. No game runs here.
 */
class OfferLoopTest {
    /** A scripted random source: records each bound, answers the next scripted index. */
    private static final class Rolls implements IntUnaryOperator {
        private final Deque<Integer> script;
        private final int otherwise;
        final List<Integer> bounds = new ArrayList<>();

        /** Answers the script in order, then {@code otherwise}. */
        Rolls(int otherwise, Integer... script) {
            this.otherwise = otherwise;
            this.script = new ArrayDeque<>(List.of(script));
        }

        @Override
        public int applyAsInt(int bound) {
            bounds.add(bound);
            Integer next = script.poll();
            return next != null ? next : otherwise;
        }
    }

    /** Each entry gives itself as its offer, except the entries named, which give none. */
    private static Function<String, String> nullFor(String... entries) {
        Set<String> none = Set.of(entries);
        return entry -> none.contains(entry) ? null : entry;
    }

    @Test
    void everyEntryNullEnds() {
        List<String> pool = new ArrayList<>(List.of("a", "b", "c"));
        List<String> sink = new ArrayList<>();
        Rolls rolls = new Rolls(0);
        int found = OfferLoop.fill(pool, 2, rolls, nullFor("a", "b", "c"), sink::add);
        assertEquals(0, found, "no entry gave an offer");
        assertTrue(sink.isEmpty(), "nothing was added");
        assertTrue(pool.isEmpty(), "every entry left the pool");
        assertEquals(List.of(3, 2, 1), rolls.bounds, "each entry was tried once");
    }

    @Test
    void stopsAtWanted() {
        List<String> pool = new ArrayList<>(List.of("a", "b"));
        List<String> sink = new ArrayList<>();
        Rolls rolls = new Rolls(0);
        int found = OfferLoop.fill(pool, 2, rolls, nullFor(), sink::add);
        assertEquals(2, found, "two offers were wanted");
        assertEquals(List.of("a", "a"), sink, "an entry that gives an offer stays in the pool");
        assertEquals(List.of("a", "b"), pool, "no entry left the pool");
        assertEquals(List.of(2, 2), rolls.bounds, "one roll per offer");
    }

    @Test
    void dropsOnlyNullEntries() {
        List<String> pool = new ArrayList<>(List.of("a", "b", "c"));
        List<String> sink = new ArrayList<>();
        Rolls rolls = new Rolls(0, 1, 1, 0);
        int found = OfferLoop.fill(pool, 2, rolls, nullFor("b"), sink::add);
        assertEquals(2, found, "two offers were wanted");
        assertEquals(List.of("c", "a"), sink, "the offers in the order rolled");
        assertEquals(List.of("a", "c"), pool, "only b, which gave none, left the pool");
        assertEquals(List.of(3, 2, 2), rolls.bounds, "the pool shrank once");
    }

    @Test
    void emptyPool() {
        List<String> pool = new ArrayList<>();
        List<String> sink = new ArrayList<>();
        Rolls rolls = new Rolls(0);
        int found = OfferLoop.fill(pool, 3, rolls, nullFor(), sink::add);
        assertEquals(0, found, "an empty pool gives nothing");
        assertTrue(sink.isEmpty(), "nothing was added");
        assertTrue(rolls.bounds.isEmpty(), "the random source was not asked");
    }

    @Test
    void wantedZero() {
        List<String> pool = new ArrayList<>(List.of("a"));
        List<String> sink = new ArrayList<>();
        Rolls rolls = new Rolls(0);
        int found = OfferLoop.fill(pool, 0, rolls, nullFor(), sink::add);
        assertEquals(0, found, "no offer was wanted");
        assertTrue(sink.isEmpty(), "nothing was added");
        assertTrue(rolls.bounds.isEmpty(), "the random source was not asked");
        assertEquals(List.of("a"), pool, "the pool is unchanged");
    }

    @Test
    void fewerThanWanted() {
        List<String> pool = new ArrayList<>(List.of("a", "b"));
        List<String> sink = new ArrayList<>();
        Rolls rolls = new Rolls(0);
        int found = OfferLoop.fill(pool, 5, rolls, nullFor("a"), sink::add);
        assertEquals(5, found, "b gives every time it is rolled");
        assertEquals(List.of("b", "b", "b", "b", "b"), sink, "five offers of b");
        assertEquals(List.of("b"), pool, "a, which gave none, left the pool at the first roll");
        assertEquals(List.of(2, 1, 1, 1, 1, 1), rolls.bounds, "one roll per try");
    }
}
