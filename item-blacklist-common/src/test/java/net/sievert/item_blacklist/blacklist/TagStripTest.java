package net.sievert.item_blacklist.blacklist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * The strip of one tag load without the game: every key stays, a cleared key is bound empty,
 * a list that lost every value is reported as emptied with what it held, each removal is
 * reported once, the input is only read, and a list that loses nothing is not copied.
 */
class TagStripTest {
    /** a:[1,2], b:[2], c:[], d:[3], in that order, as immutable lists. */
    private static Map<String, List<Integer>> incoming() {
        Map<String, List<Integer>> map = new LinkedHashMap<>();
        map.put("a", List.of(1, 2));
        map.put("b", List.of(2));
        map.put("c", List.of());
        map.put("d", List.of(3));
        return map;
    }

    /** Clears "d", removes even values, reports nothing. */
    private static TagStrip.Outcome<String, Integer> strip(Map<String, List<Integer>> in) {
        return TagStrip.strip(in, "d"::equals, value -> value % 2 == 0, key -> {
        }, (key, value) -> {
        });
    }

    @Test
    void keepsEveryKey() {
        TagStrip.Outcome<String, Integer> out = strip(incoming());
        assertEquals(List.of("a", "b", "c", "d"), new ArrayList<>(out.tags().keySet()));
        assertEquals(List.of(1), out.tags().get("a"));
        assertEquals(List.of(), out.tags().get("b"));
        assertEquals(List.of(), out.tags().get("c"));
        assertEquals(List.of(), out.tags().get("d"));
    }

    @Test
    void emptiedOnlyWhenEverythingWent() {
        TagStrip.Outcome<String, Integer> out = strip(incoming());
        assertEquals(Map.of("b", List.of(2)), out.emptied(),
                "not c (it was empty), not d (cleared), not a (kept a value)");
    }

    @Test
    void unchangedListIsTheSameInstance() {
        Map<String, List<Integer>> in = incoming();
        in.put("e", List.of(1, 3));
        TagStrip.Outcome<String, Integer> out = strip(in);
        assertSame(in.get("c"), out.tags().get("c"));
        assertSame(in.get("e"), out.tags().get("e"));
    }

    @Test
    void inputIsNotChanged() {
        Map<String, List<Integer>> in = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : incoming().entrySet()) {
            in.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        Map<String, List<Integer>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : in.entrySet()) {
            copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        strip(in);
        assertEquals(copy, in);
    }

    @Test
    void resultIsUnmodifiable() {
        Map<String, List<Integer>> in = new LinkedHashMap<>();
        for (Map.Entry<String, List<Integer>> entry : incoming().entrySet()) {
            in.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        TagStrip.Outcome<String, Integer> out = strip(in);
        assertThrows(UnsupportedOperationException.class, () -> out.tags().put("x", List.of()));
        assertThrows(UnsupportedOperationException.class, () -> out.tags().get("a").add(5));
        assertThrows(UnsupportedOperationException.class, () -> out.emptied().put("x", List.of()));
    }

    @Test
    void reportsEachRemovalAndClear() {
        List<String> calls = new ArrayList<>();
        Consumer<String> onCleared = key -> calls.add("cleared " + key);
        BiConsumer<String, Integer> onRemoved = (key, value) -> calls.add("removed " + key + "="
                + value);
        TagStrip.strip(incoming(), "d"::equals, value -> value % 2 == 0, onCleared, onRemoved);
        assertEquals(List.of("removed a=2", "removed b=2", "cleared d", "removed d=3"), calls,
                "incoming order, a cleared key before its values, cleared values included");
    }

    @Test
    void nothingToDo() {
        Map<String, List<Integer>> in = incoming();
        List<String> calls = new ArrayList<>();
        TagStrip.Outcome<String, Integer> out = TagStrip.strip(in, key -> false, value -> false,
                key -> calls.add("cleared " + key), (key, value) -> calls.add("removed " + key));
        for (Map.Entry<String, List<Integer>> entry : in.entrySet()) {
            assertSame(entry.getValue(), out.tags().get(entry.getKey()), entry.getKey());
        }
        assertTrue(out.emptied().isEmpty(), "nothing emptied");
        assertTrue(calls.isEmpty(), "no callback");
    }
}
