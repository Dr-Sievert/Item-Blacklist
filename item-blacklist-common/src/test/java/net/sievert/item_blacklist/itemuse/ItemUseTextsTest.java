package net.sievert.item_blacklist.itemuse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The player-facing texts of item-use, with no game running: namespaced, distinct keys, and the
 * old mod's English as fallbacks, colourless, with one name placeholder where a name is shown.
 */
class ItemUseTextsTest {
    @Test
    void keysAreNamespaced() {
        List<String> keys = List.of(ItemUseTexts.REMOVED_KEY, ItemUseTexts.BLOCK_KEY,
                ItemUseTexts.TOOLTIP_KEY);
        for (String key : keys) {
            assertTrue(key.startsWith("item_blacklist."), key + " starts with item_blacklist.");
        }
        assertEquals(keys.size(), Set.copyOf(keys).size(), "the keys are distinct");
    }

    @Test
    void removalTextsTakeTheName() {
        for (String fallback : List.of(ItemUseTexts.REMOVED_FALLBACK,
                ItemUseTexts.BLOCK_FALLBACK)) {
            assertEquals(1, fallback.split("%s", -1).length - 1,
                    fallback + " holds exactly one %s");
        }
        assertEquals("Oak Planks is disabled by blacklist.",
                String.format(ItemUseTexts.REMOVED_FALLBACK, "Oak Planks"));
        assertEquals("Oak Planks is disabled by blacklist.",
                String.format(ItemUseTexts.BLOCK_FALLBACK, "Oak Planks"));
    }

    @Test
    void tooltipTextIsTheOldOne() {
        assertEquals("Disabled by blacklist.", ItemUseTexts.TOOLTIP_FALLBACK);
        assertFalse(ItemUseTexts.TOOLTIP_FALLBACK.contains("%"), "the tooltip takes no argument");
    }

    @Test
    void noColourCodes() {
        for (String fallback : List.of(ItemUseTexts.REMOVED_FALLBACK,
                ItemUseTexts.BLOCK_FALLBACK, ItemUseTexts.TOOLTIP_FALLBACK)) {
            assertFalse(fallback.contains("§"), fallback + " holds a section sign");
            assertFalse(fallback.contains("\u001B"), fallback + " holds an ANSI escape");
        }
    }
}
