package net.sievert.item_blacklist.id;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Text ids with no game running: the config form needs a ':', the data form defaults the
 * namespace, both apply 26.x's character rules on every release, and the path-first order is
 * total and never throws.
 */
class IdTextTest {
    @Test
    void parsesTheConfigForm() {
        assertEquals(Optional.of(new IdText("minecraft", "stone")),
                IdText.parse("minecraft:stone"));
        assertEquals(Optional.of(new IdText("minecraft", "stone")), IdText.parse(":stone"),
                "an empty namespace means minecraft");
        assertEquals(Optional.of(new IdText(".", "a")), IdText.parse(".:a"),
                "a single dot is a valid namespace");
        assertEquals(Optional.of(new IdText("a", "b/c.d-e_f")), IdText.parse("a:b/c.d-e_f"));
    }

    @Test
    void refusesInvalidConfigText() {
        for (String text : new String[] {"stone", "a:", "A:b", "a:B", "..:a", "#a:b", "a:b:c",
                "a b:c", "a:b c", ""}) {
            assertEquals(Optional.empty(), IdText.parse(text), "parse(\"" + text + "\")");
        }
        assertEquals(Optional.empty(), IdText.parse(null));
    }

    @Test
    void parsesTheDataForm() {
        assertEquals(Optional.of(new IdText("minecraft", "stone")), IdText.parseLenient("stone"),
                "no ':' means namespace minecraft");
        assertEquals(Optional.of(new IdText("a", "b")), IdText.parseLenient("a:b"));
        assertEquals(Optional.empty(), IdText.parseLenient("Stone"));
        assertEquals(Optional.empty(), IdText.parseLenient(""));
        assertEquals(Optional.empty(), IdText.parseLenient(null));
    }

    @Test
    void refusesInvalidParts() {
        assertThrows(IllegalArgumentException.class, () -> new IdText("A", "b"));
        assertThrows(IllegalArgumentException.class, () -> new IdText("a", ""));
        assertThrows(IllegalArgumentException.class, () -> new IdText("..", "b"));
        assertThrows(NullPointerException.class, () -> new IdText(null, "b"));
    }

    @Test
    void printsNamespaceColonPath() {
        assertEquals("minecraft:oak_planks", new IdText("minecraft", "oak_planks").toString());
        assertEquals("a:b/c", IdText.parse("a:b/c").orElseThrow().toString());
    }

    @Test
    void sortsPathFirst() {
        List<String> ids = new ArrayList<>(List.of("b:a", "a:b", "a:a"));
        ids.sort(IdText.PATH_FIRST);
        assertEquals(List.of("a:a", "b:a", "a:b"), ids);
        assertTrue(IdText.PATH_FIRST.compare("#b:a", "a:b") < 0, "a leading '#' is ignored");
    }

    @Test
    void comparesAnyTextTotallyAndConsistentlyWithEquals() {
        List<String> texts = List.of("#a:b", "a:b", "", ":", "x", "a:b:c", "b:a");
        for (String a : texts) {
            for (String b : texts) {
                int ab = IdText.PATH_FIRST.compare(a, b);
                int ba = IdText.PATH_FIRST.compare(b, a);
                assertEquals(Integer.signum(ab), -Integer.signum(ba), a + " against " + b);
                assertEquals(a.equals(b), ab == 0, a + " against " + b);
            }
        }
        // Ties of path and namespace fall back to String order.
        assertTrue(IdText.PATH_FIRST.compare("#a:b", "a:b") < 0);
    }
}
