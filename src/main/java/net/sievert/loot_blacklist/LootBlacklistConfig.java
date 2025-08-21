package net.sievert.loot_blacklist;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles loading and creating the config file for loot item blacklisting.
 */
public class LootBlacklistConfig {

    /** Logger for config operations */
    private static final Logger LOGGER = LoggerFactory.getLogger(LootBlacklist.MOD_ID);

    /** Config file name */
    private static final String CONFIG_FILE = "loot_blacklist.json";

    /** Example entries for new config */
    private static final List<String> COMMENT_EXAMPLES = List.of(
            "    // \"minecraft:iron_ingot\",",
            "    // \"mod_id:mod_item\""
    );

    /** Set of item identifiers to blacklist */
    public Set<Identifier> blacklist = new HashSet<>();

    /**
     * Loads config from disk, or creates a new one if missing.
     */
    public static LootBlacklistConfig loadOrCreate() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configPath = configDir.resolve(CONFIG_FILE);

        // Case 1: File exists, try to read
        if (configPath.toFile().exists()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(configPath.toFile()), StandardCharsets.UTF_8)) {
                JsonElement rootElement = JsonParser.parseReader(reader);
                if (!rootElement.isJsonObject()) throw new JsonSyntaxException("Root is not object");

                LootBlacklistConfig config = new LootBlacklistConfig();
                JsonArray arr = rootElement.getAsJsonObject().getAsJsonArray("blacklist");
                if (arr != null) {
                    for (JsonElement el : arr) {
                        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                            String raw = el.getAsString();
                            try {
                                Identifier id = Identifier.of(raw);
                                config.blacklist.add(id);
                            } catch (Exception ex) {
                                LOGGER.warn("Invalid identifier syntax in config: {}", raw);
                            }
                        }
                    }
                }

                Set<Identifier> valid = new HashSet<>();
                Set<Identifier> invalid = new HashSet<>();
                for (Identifier id : config.blacklist) {
                    if (Registries.ITEM.containsId(id)) valid.add(id);
                    else invalid.add(id);
                }
                config.blacklist = valid;

                LOGGER.info("Loaded blacklist config: {} valid entries", valid.size());
                for (Identifier bad : invalid) {
                    LOGGER.warn("Blacklist entry not found in registry: {}", bad);
                }

                return config;
            } catch (Exception e) {
                LOGGER.warn("Malformed config file. Using empty blacklist. Fix JSON syntax to enable.");
                return new LootBlacklistConfig();
            }
        }

        // Case 2: Missing file, generate default
        File configDirFile = configDir.toFile();
        if (!configDirFile.exists() && !configDirFile.mkdirs()) {
            LOGGER.warn("Failed to create config directory: {}", configDirFile);
        }

        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(configPath.toFile()), StandardCharsets.UTF_8))) {
            writer.println("{");
            writer.println("  \"blacklist\": [");
            for (String example : COMMENT_EXAMPLES) writer.println(example);
            writer.println("  ]");
            writer.println("}");
            LOGGER.info("Created default loot_blacklist config at {}", configPath);
        } catch (Exception e) {
            LOGGER.error("Failed to create default loot_blacklist config!", e);
        }

        return new LootBlacklistConfig();
    }
}
