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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles loading, validating, and optionally generating the loot_blacklist config file.
 */
public class LootBlacklistConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(LootBlacklist.MOD_ID);
    private static final String CONFIG_FILE = "loot_blacklist.json";

    private static final List<String> COMMENT_EXAMPLES = List.of(
            "    // \"minecraft:iron_ingot\",",
            "    // \"mod_id:mod_item\""
    );

    /** Raw identifiers from JSON (validated later) */
    public Set<Identifier> blacklist = new HashSet<>();

    /** Load or create config, but do NOT validate yet */
    public static LootBlacklistConfig loadOrCreate() {
        LOGGER.info("Loading blacklist config...");
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configPath = configDir.resolve(CONFIG_FILE);

        LootBlacklistConfig config = new LootBlacklistConfig();

        // Attempt to read existing config
        if (configPath.toFile().exists()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(configPath.toFile()), StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (!root.isJsonObject()) throw new JsonSyntaxException("Root element is not a JSON object");

                JsonArray arr = root.getAsJsonObject().getAsJsonArray("blacklist");
                if (arr != null) {
                    for (JsonElement el : arr) {
                        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                            String raw = el.getAsString();
                            try {
                                config.blacklist.add(Identifier.of(raw));
                            } catch (Exception ex) {
                                LOGGER.warn("Invalid identifier in config: {}", raw);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to load config: malformed JSON. Using empty blacklist.");
            }
        } else {
            // Generate default config
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
                LOGGER.error("Failed to write default loot_blacklist config!", e);
            }
        }

        LOGGER.info("Loaded blacklist config with {} raw entries", config.blacklist.size());
        return config;
    }

    /** Filters out invalid item IDs and logs results */
    public void validateEntries() {
        Set<Identifier> valid = new HashSet<>();
        Set<Identifier> invalid = new HashSet<>();

        for (Identifier id : blacklist) {
            if (Registries.ITEM.containsId(id)) {
                valid.add(id);
            } else {
                invalid.add(id);
            }
        }

        blacklist = valid;
        LOGGER.info("Blacklist validated: {} item(s) loaded", valid.size());
        for (Identifier bad : invalid) {
            LOGGER.warn("Invalid blacklist entry: not found in item registry: {}", bad);
        }
    }
}
