package net.sievert.item_blacklist.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.sievert.item_blacklist.id.IdText;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The defects the old mod's config handling had, each held to the verdict this mod gives it.
 */
class ConfigDefectsTest {
    private static ConfigParser.Result parse(String text) {
        return ConfigParser.parse(new StringReader(text));
    }

    /** A parsed config cannot be edited, so validation cannot drop entries from it. */
    @Test
    void parsedListsAreUnmodifiable() {
        BlacklistConfig config =
                parse("{\"Items\": [\"minecraft:stone\"], \"Potions\": []}").config();
        assertThrows(UnsupportedOperationException.class,
                () -> config.items().add(new IdText("minecraft", "dirt")));
        assertThrows(UnsupportedOperationException.class,
                () -> config.potions().add(new IdText("minecraft", "water")));
        assertThrows(UnsupportedOperationException.class,
                () -> BlacklistConfig.EMPTY.items().add(new IdText("minecraft", "dirt")));
    }

    /** A config built from lists keeps its own copy. */
    @Test
    void aBuiltConfigCopiesItsLists() {
        List<IdText> items = new ArrayList<>(List.of(new IdText("minecraft", "stone")));
        BlacklistConfig config =
                new BlacklistConfig(false, items, List.of(), List.of(), List.of(), List.of());
        items.add(new IdText("minecraft", "dirt"));
        assertEquals(List.of(new IdText("minecraft", "stone")), config.items());
    }

    /** ",}" was fatal in the old mod and stays a syntax error: EMPTY and one warning. */
    @Test
    void aDanglingCommaInTheObjectGivesTheEmptyConfig() {
        ConfigParser.Result result = parse("{\"Items\": [\"minecraft:stone\"],}");
        assertEquals(BlacklistConfig.EMPTY, result.config());
        assertEquals(1, result.warnings().size());
    }

    /** Kept: text after the root object is ignored, as parseReader(JsonReader) always did. */
    @Test
    void textAfterTheRootIsIgnored() {
        ConfigParser.Result result = parse("{\"Items\": [\"minecraft:stone\"]} trailing");
        assertEquals(List.of(new IdText("minecraft", "stone")), result.config().items());
        assertEquals(List.of(), result.warnings());
    }

    /** An empty file is a root that is no object (Gson reads JsonNull): EMPTY, never null. */
    @Test
    void anEmptyFileGivesTheEmptyConfig() {
        ConfigParser.Result result = parse("");
        assertNotNull(result.config());
        assertEquals(BlacklistConfig.EMPTY, result.config());
        assertTrue(result.config().isEmpty());
        assertEquals(1, result.warnings().size());
    }

    /** The config's summary in its load line: per list, the minecraft count and the other count. */
    @Test
    void summaryCountsByNamespace() {
        BlacklistConfig config = new BlacklistConfig(false,
                List.of(new IdText("minecraft", "stone"), new IdText("minecraft", "dirt"),
                        new IdText("a", "x")),
                List.of(new IdText("minecraft", "logs")),
                List.of(new IdText("a", "p"), new IdText("b", "q"), new IdText("c", "r")),
                List.of(new IdText("minecraft", "mending"), new IdText("minecraft", "unbreaking"),
                        new IdText("minecraft", "sharpness"), new IdText("minecraft", "smite")),
                List.of(new IdText("a", "t"), new IdText("b", "u"), new IdText("c", "v"),
                        new IdText("d", "w"), new IdText("e", "y")));
        List<String> summary = config.summary();
        assertEquals(5, summary.size(), "one entry per list: " + summary);
        int[][] counts = {{2, 1}, {1, 0}, {0, 3}, {4, 0}, {0, 5}};
        for (int i = 0; i < counts.length; i++) {
            String entry = summary.get(i);
            assertTrue(entry.contains(counts[i][0] + " minecraft"), entry);
            assertTrue(entry.contains(counts[i][1] + " other"), entry);
        }
    }
}
