package net.sievert.item_blacklist.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.sievert.item_blacklist.id.IdText;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every rule of the config parse (README.md, "The config"), each with its warning text: a bad
 * input costs one warning and itself, never the rest of the file.
 */
class ConfigParserTest {
    private static ConfigParser.Result parse(String text) {
        return ConfigParser.parse(new StringReader(text));
    }

    private static IdText id(String namespace, String path) {
        return new IdText(namespace, path);
    }

    @Test
    void invalidJsoncGivesTheEmptyConfig() {
        ConfigParser.Result result = parse("{\"Items\": [\"minecraft:stone\"");
        assertEquals(BlacklistConfig.EMPTY, result.config());
        assertEquals(1, result.warnings().size(), result.warnings().toString());
        String warning = result.warnings().get(0);
        assertTrue(warning.startsWith("The config is not valid JSONC ("), warning);
        assertTrue(warning.endsWith("); using an empty blacklist"), warning);
    }

    @Test
    void aRootThatIsNoObjectGivesTheEmptyConfig() {
        ConfigParser.Result result = parse("[\"minecraft:stone\"]");
        assertEquals(BlacklistConfig.EMPTY, result.config());
        assertEquals(List.of("The config's root is not an object; using an empty blacklist"),
                result.warnings());
    }

    @Test
    void anEmptyFileIsARootThatIsNoObject() {
        ConfigParser.Result result = parse("");
        assertEquals(BlacklistConfig.EMPTY, result.config());
        assertEquals(List.of("The config's root is not an object; using an empty blacklist"),
                result.warnings());
    }

    @Test
    void detailedLogMustBeABoolean() {
        ConfigParser.Result result = parse("{\"Detailed Log\": \"yes\", \"Items\": [\"a:b\"]}");
        assertFalse(result.config().detailedLog());
        assertEquals(List.of("\"Detailed Log\" is not true or false (\"yes\"); using false"),
                result.warnings());
        assertEquals(List.of(id("a", "b")), result.config().items(), "the lists are kept");

        assertTrue(parse("{\"Detailed Log\": true}").config().detailedLog());
        assertFalse(parse("{\"Detailed Log\": false}").config().detailedLog());
        assertEquals(List.of(), parse("{\"Detailed Log\": null}").warnings());
        assertEquals(List.of(), parse("{}").warnings());
    }

    @Test
    void aListThatIsNoArrayIsIgnoredAndTheOthersKept() {
        ConfigParser.Result result = parse(
                "{\"Items\": \"minecraft:stone\", \"Potions\": [\"minecraft:strength\"]}");
        assertEquals(List.of("\"Items\" is not a list (\"minecraft:stone\"); ignored"),
                result.warnings());
        assertEquals(List.of(), result.config().items());
        assertEquals(List.of(id("minecraft", "strength")), result.config().potions());
    }

    @Test
    void aNonStringEntryWarns() {
        ConfigParser.Result result = parse(
                "{\"Items\": [5, \"a:b\"], \"Potions\": [true], \"Enchantments\": [{}]}");
        assertEquals(List.of(
                "Ignoring non-string item entry: 5",
                "Ignoring non-string potion entry: true",
                "Ignoring non-string enchantment entry: {}"), result.warnings());
        assertEquals(List.of(id("a", "b")), result.config().items());
    }

    @Test
    void anEmptyEntryWarns() {
        ConfigParser.Result result = parse(
                "{\"Items\": [\"  \"], \"Potions\": [\"\"], \"Enchantments\": [\" \"]}");
        assertEquals(List.of(
                "Ignoring empty item entry",
                "Ignoring empty potion entry",
                "Ignoring empty enchantment entry"), result.warnings());
        assertTrue(result.config().isEmpty());
    }

    @Test
    void anInvalidIdWarnsNamingTheEntryAsWritten() {
        ConfigParser.Result result = parse("{"
                + "\"Items\": [\"Stone\", \"#Bad:tag\", \"stone\"],"
                + "\"Potions\": [\"bad potion\"],"
                + "\"Enchantments\": [\"x\", \"#..:y\"]}");
        assertEquals(List.of(
                "Ignoring invalid item entry: Stone",
                "Ignoring invalid tag entry: #Bad:tag",
                "Ignoring invalid item entry: stone",
                "Ignoring invalid potion entry: bad potion",
                "Ignoring invalid enchantment entry: x",
                "Ignoring invalid enchantment tag entry: #..:y"), result.warnings());
        assertTrue(result.config().isEmpty());
    }

    @Test
    void aPotionHasNoTags() {
        ConfigParser.Result result = parse("{\"Potions\": [\"#minecraft:tradeable\"]}");
        assertEquals(List.of("Ignoring potion entry #minecraft:tradeable: potions have no tags"),
                result.warnings());
        assertEquals(List.of(), result.config().potions());
    }

    @Test
    void duplicatesAreDroppedWithoutAWarning() {
        ConfigParser.Result result = parse(
                "{\"Items\": [\"a:b\", \" a:b \", \"#a:b\", \"#a:b\", \":b\", \"minecraft:b\"]}");
        assertEquals(List.of(), result.warnings());
        assertEquals(List.of(id("a", "b"), id("minecraft", "b")), result.config().items());
        assertEquals(List.of(id("a", "b")), result.config().itemTags());
    }

    @Test
    void aLeadingHashRoutesToTheTagLists() {
        ConfigParser.Result result = parse("{"
                + "\"Items\": [\"minecraft:oak_planks\", \"#minecraft:planks\"],"
                + "\"Enchantments\": [\"minecraft:mending\", \"#minecraft:curse\"]}");
        assertEquals(List.of(), result.warnings());
        BlacklistConfig config = result.config();
        assertEquals(List.of(id("minecraft", "oak_planks")), config.items());
        assertEquals(List.of(id("minecraft", "planks")), config.itemTags());
        assertEquals(List.of(id("minecraft", "mending")), config.enchantments());
        assertEquals(List.of(id("minecraft", "curse")), config.enchantmentTags());
    }

    @Test
    void entriesAreTrimmed() {
        ConfigParser.Result result = parse("{\"Items\": [\"  minecraft:stone \"]}");
        assertEquals(List.of(), result.warnings());
        assertEquals(List.of(id("minecraft", "stone")), result.config().items());
    }

    @Test
    void commentsTrailingCommasAndABomAreAccepted() {
        String text = "\uFEFF{\n"
                + "  // a line comment\n"
                + "  /* a block comment */\n"
                + "  \"Items\": [\n"
                + "    \"minecraft:stone\", // after an entry\n"
                + "    \"minecraft:dirt\",\n"
                + "  ],\n"
                + "  \"Potions\": [\n"
                + "    // \"minecraft:strength\"\n"
                + "  ]\n"
                + "}\n";
        ConfigParser.Result result = parse(text);
        assertEquals(List.of(), result.warnings());
        assertEquals(List.of(id("minecraft", "stone"), id("minecraft", "dirt")),
                result.config().items());
        assertEquals(List.of(), result.config().potions());
    }

    @Test
    void fileOrderIsKept() {
        ConfigParser.Result result = parse(
                "{\"Items\": [\"z:z\", \"a:a\", \"m:m\"], \"Potions\": [\"b:b\", \"a:a\"]}");
        assertEquals(List.of(id("z", "z"), id("a", "a"), id("m", "m")), result.config().items());
        assertEquals(List.of(id("b", "b"), id("a", "a")), result.config().potions());
    }

    @Test
    void theDefaultTextIsTheEmptyConfig() {
        ConfigParser.Result result = parse(ConfigFile.defaultText());
        assertEquals(List.of(), result.warnings());
        assertEquals(BlacklistConfig.EMPTY, result.config());
    }
}
