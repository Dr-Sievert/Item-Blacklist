package net.sievert.item_blacklist.brewing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The text of a removed brewing mix or recipe, the report's subject: the old log's form, for
 * vanilla mixes (a potion or item input) and NeoForge recipes (a list of input items), and the
 * marker of a keyless holder, which can never be taken for an id.
 */
class BrewingTextTest {
    @Test
    void potionMixText() {
        assertEquals("input=minecraft:awkward, ingredient=[minecraft:blaze_powder],"
                        + " output=minecraft:strength",
                BrewingText.mix("minecraft:awkward", List.of("minecraft:blaze_powder"),
                        "minecraft:strength"));
    }

    @Test
    void neoForgeRecipeText() {
        assertEquals("input=[minecraft:stone], ingredient=[minecraft:rabbit_foot],"
                        + " output=minecraft:iron_ingot",
                BrewingText.mix(List.of("minecraft:stone").toString(),
                        List.of("minecraft:rabbit_foot"), "minecraft:iron_ingot"));
    }

    @Test
    void severalAlternatives() {
        assertEquals("input=a:x, ingredient=[a:b, c:d], output=a:y",
                BrewingText.mix("a:x", List.of("a:b", "c:d"), "a:y"));
    }

    @Test
    void emptyReagent() {
        assertEquals("input=a:x, ingredient=[], output=a:y",
                BrewingText.mix("a:x", List.of(), "a:y"));
    }

    @Test
    void direct() {
        assertEquals("[direct]", BrewingText.DIRECT);
        assertFalse(BrewingText.DIRECT.contains(":"), "DIRECT can never equal an id");
    }
}
