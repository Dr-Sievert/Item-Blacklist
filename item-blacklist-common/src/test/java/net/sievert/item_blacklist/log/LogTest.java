package net.sievert.item_blacklist.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The line format every log line of the mod has: the mod's name, the tag in brackets,
 * the message, and no colour codes, which the old mod wrote and a log file shows as garbage.
 */
class LogTest {
    @Test
    void formatsNameTagMessage() {
        assertEquals("Item Blacklist [TAG] x", Log.format(LogTag.TAG, "x"));
        assertEquals("Item Blacklist [CONFIG] Loaded a: b",
                Log.format(LogTag.CONFIG, "Loaded a: b"));
    }

    @Test
    void everyTagStartsTheSameWayWithoutColourCodes() {
        for (LogTag tag : LogTag.values()) {
            String line = Log.format(tag, "message");
            assertTrue(line.startsWith("Item Blacklist ["), line);
            assertFalse(line.contains("\u001B"), line);
        }
    }

    @Test
    void theNineOldTagsInOrder() {
        assertEquals(List.of("CONFIG", "ENCHANTMENT", "INIT", "ITEM", "LOOT", "POTION", "RECIPE",
                "TAG", "TRADE"), Arrays.stream(LogTag.values()).map(Enum::name).toList());
    }
}
