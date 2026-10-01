package net.sievert.item_blacklist.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.sievert.item_blacklist.id.IdText;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The config file on disk: a missing one is created with the old default text, an existing
 * one is read and never rewritten, and a folder that cannot hold it costs no exception.
 */
class ConfigFileTest {
    /** The old mod's default file, line by line (old BlacklistConfig, 23 newLine calls). */
    private static final List<String> OLD_DEFAULT = List.of(
            "{",
            "  \"Detailed Log\": false,",
            "",
            "  \"Items\": [",
            "    // \"minecraft:oak_planks\",",
            "    // \"#minecraft:planks\",",
            "    // \"mod_id:mod_item\",",
            "    // \"#mod_id:mod_tag\"",
            "  ],",
            "",
            "  \"Potions\": [",
            "    // \"minecraft:strength\",",
            "    // \"minecraft:strong_strength\",",
            "    // \"minecraft:long_strength\"",
            "  ],",
            "",
            "  \"Enchantments\": [",
            "    // \"minecraft:mending\",",
            "    // \"#minecraft:curse\",",
            "    // \"mod_id:mod_enchantment\",",
            "    // \"#mod_id:mod_enchantment_tag\"",
            "  ]",
            "}");

    @Test
    void theDefaultTextIsTheOldTemplate() {
        assertEquals(23, OLD_DEFAULT.size());
        String expected = String.join(System.lineSeparator(), OLD_DEFAULT)
                + System.lineSeparator();
        assertEquals(expected, ConfigFile.defaultText());
    }

    @Test
    void aMissingFileIsCreatedWithTheDefaultText(@TempDir Path dir) throws IOException {
        Path configDir = dir.resolve("config");
        ConfigFile.Loaded loaded = ConfigFile.loadOrCreate(configDir);
        Path file = configDir.resolve(ConfigFile.NAME);
        assertEquals(file, loaded.path());
        assertTrue(loaded.created());
        assertFalse(loaded.writeFailed());
        assertEquals(BlacklistConfig.EMPTY, loaded.config());
        assertEquals(List.of(), loaded.warnings());
        assertEquals(ConfigFile.defaultText(), Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void anExistingFileIsReadAndNotRewritten(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(ConfigFile.NAME);
        byte[] bytes = "{ \"Items\": [\"minecraft:stone\", \"Bad\"] }"
                .getBytes(StandardCharsets.UTF_8);
        Files.write(file, bytes);
        ConfigFile.Loaded loaded = ConfigFile.loadOrCreate(dir);
        assertFalse(loaded.created());
        assertFalse(loaded.writeFailed());
        assertEquals(List.of(new IdText("minecraft", "stone")), loaded.config().items());
        assertEquals(List.of("Ignoring invalid item entry: Bad"), loaded.warnings());
        assertArrayEquals(bytes, Files.readAllBytes(file));
    }

    @Test
    void aConfigFolderThatIsAFileGivesWriteFailed(@TempDir Path dir) throws IOException {
        Path notAFolder = dir.resolve("config");
        Files.writeString(notAFolder, "not a folder");
        ConfigFile.Loaded loaded = ConfigFile.loadOrCreate(notAFolder);
        assertTrue(loaded.writeFailed());
        assertFalse(loaded.created());
        assertEquals(BlacklistConfig.EMPTY, loaded.config());
        assertEquals(List.of(), loaded.warnings());
    }

    @Test
    void aFolderInPlaceOfTheFileGivesOneCannotReadWarning(@TempDir Path dir) throws IOException {
        Files.createDirectory(dir.resolve(ConfigFile.NAME));
        ConfigFile.Loaded loaded = ConfigFile.loadOrCreate(dir);
        assertEquals(BlacklistConfig.EMPTY, loaded.config());
        assertFalse(loaded.created());
        assertFalse(loaded.writeFailed());
        assertEquals(1, loaded.warnings().size(), loaded.warnings().toString());
        assertTrue(loaded.warnings().get(0).startsWith("Cannot read "), loaded.warnings().get(0));
    }
}
