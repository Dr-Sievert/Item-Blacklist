package net.sievert.item_blacklist.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.sievert.item_blacklist.id.IdText;
import net.sievert.item_blacklist.layout.ProjectFiles;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The GameTests' config fixture, the test mod's resource, parses without a warning and holds
 * exactly the entries the scenarios rely on (CLAUDE.md, "The GameTest blacklist"). A change of
 * the fixture changes this test in the same change.
 */
class FixtureTest {
    private static final String FIXTURE =
            "item-blacklist-common/src/gametest/resources/item_blacklist_gametest/fixture.jsonc";

    private static List<IdText> ids(String... texts) {
        return Arrays.stream(texts)
                .map(text -> IdText.parse(text).orElseThrow())
                .toList();
    }

    @Test
    void theFixtureHoldsExactlyItsEntries() throws IOException {
        Path file = ProjectFiles.root().resolve(FIXTURE);
        assertTrue(Files.isRegularFile(file), file + " is missing");
        ConfigParser.Result result;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            result = ConfigParser.parse(reader);
        }
        assertEquals(List.of(), result.warnings());
        BlacklistConfig config = result.config();
        assertTrue(config.detailedLog());
        assertEquals(ids("minecraft:oak_planks", "minecraft:charcoal", "minecraft:soul_sand",
                "minecraft:soul_soil", "minecraft:rabbit_foot", "minecraft:lingering_potion",
                "minecraft:beetroot_seeds", "minecraft:phantom_membrane", "mod_id:mod_item"),
                config.items());
        assertEquals(ids("minecraft:planks", "mod_id:mod_tag"), config.itemTags());
        assertEquals(ids("minecraft:strength", "minecraft:strong_strength",
                "minecraft:long_strength", "mod_id:mod_potion"), config.potions());
        assertEquals(ids("minecraft:mending", "mod_id:mod_enchantment"), config.enchantments());
        assertEquals(ids("minecraft:curse", "mod_id:mod_enchantment_tag"),
                config.enchantmentTags());
    }
}
