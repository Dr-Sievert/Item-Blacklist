package net.sievert.item_blacklist.config;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * config/item_blacklist.jsonc: read it, or write the old mod's default text when there is
 * none, so an existing config keeps working and a new one shows its examples. It never throws
 * and logs nothing: ItemBlacklistMod logs what {@link Loaded} says.
 */
public final class ConfigFile {
    /** The file's name inside the loader's config folder, unchanged from the old mod. */
    public static final String NAME = "item_blacklist.jsonc";

    /**
     * The old mod's default file, line by line: an empty blacklist whose lists hold commented
     * examples. Two spaces before a key, four before a "//".
     */
    private static final List<String> LINES = List.of(
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

    /**
     * What {@link #loadOrCreate} did.
     *
     * @param path the file it read or wrote
     * @param config the config; EMPTY when the file was created, unreadable or not written
     * @param warnings the parse's warnings, or the one warning of a file it could not read
     * @param created the default file was written, since none existed
     * @param writeFailed none existed and the default file could not be written
     */
    public record Loaded(Path path, BlacklistConfig config, List<String> warnings,
            boolean created, boolean writeFailed) {
        /** Copies the warnings, so the result cannot change afterwards. */
        public Loaded {
            warnings = List.copyOf(warnings);
        }
    }

    private ConfigFile() {
    }

    /**
     * Reads {@code configDir/item_blacklist.jsonc}, or writes the default text there when the
     * file does not exist. Never throws and never returns null: a file it cannot read gives
     * EMPTY with one warning, a default it cannot write gives EMPTY with writeFailed.
     */
    public static Loaded loadOrCreate(Path configDir) {
        Path path = configDir.resolve(NAME);
        if (Files.exists(path)) {
            // Read whole first, so a fault of the disk is "Cannot read" on every platform and
            // never reaches the parser as a JSON error (Linux fails a folder only at read).
            String text;
            try {
                text = Files.readString(path, StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException e) {
                return new Loaded(path, BlacklistConfig.EMPTY,
                        List.of("Cannot read " + path + " (" + e + "); using an empty blacklist"),
                        false, false);
            }
            ConfigParser.Result parsed = ConfigParser.parse(new StringReader(text));
            return new Loaded(path, parsed.config(), parsed.warnings(), false, false);
        }
        try {
            Files.createDirectories(configDir);
            // CREATE_NEW: a file another process wrote meanwhile is never overwritten.
            Files.writeString(path, defaultText(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            return new Loaded(path, BlacklistConfig.EMPTY, List.of(), true, false);
        } catch (IOException | RuntimeException e) {
            return new Loaded(path, BlacklistConfig.EMPTY, List.of(), false, true);
        }
    }

    /**
     * The old default file, byte for byte: its 23 lines, each ended by the platform's line
     * separator, as the old mod's BufferedWriter.newLine() wrote them.
     */
    public static String defaultText() {
        return String.join(System.lineSeparator(), LINES) + System.lineSeparator();
    }
}
