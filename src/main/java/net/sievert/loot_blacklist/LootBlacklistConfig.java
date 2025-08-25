package net.sievert.loot_blacklist;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static net.sievert.loot_blacklist.LootBlacklistLogger.*;
import static net.sievert.loot_blacklist.LootBlacklistLogger.Group.*;

/**
 * Configuration class for Loot Blacklist.
 * Loads blacklist entries from JSON config and
 * provides both raw and validated sets.
 */
public class LootBlacklistConfig {
    private static final String CONFIG_FILE = "loot_blacklist.json";
    private static final List<String> COMMENT_EXAMPLES = List.of(
            "    // \"minecraft:iron_ingot\",",
            "    // \"mod_id:mod_item\""
    );

    /** Raw identifiers from JSON (never mutated after load). */
    public final Set<String> rawBlacklist = new HashSet<>();

    /** Working/validated list of identifiers set by validation logic. */
    public Set<net.minecraft.util.Identifier> blacklist = new HashSet<>();

    /**
     * Loads the loot blacklist config from file, or creates a default one if missing.
     * Does not perform validation on entries.
     */
    public static LootBlacklistConfig loadOrCreate() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configPath = configDir.resolve(CONFIG_FILE);

        LootBlacklistConfig config = new LootBlacklistConfig();

        if (configPath.toFile().exists()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(configPath.toFile()), StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (!root.isJsonObject()) throw new JsonSyntaxException("Root element is not a JSON object");

                JsonArray arr = root.getAsJsonObject().getAsJsonArray("blacklist");
                if (arr != null) {
                    for (JsonElement el : arr) {
                        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                            config.rawBlacklist.add(el.getAsString());
                        }
                    }
                }
            } catch (Exception e) {
                warn(INIT, "Failed to load config: malformed JSON. Using empty blacklist.");
            }
        } else {
            File configDirFile = configDir.toFile();
            if (!configDirFile.exists() && !configDirFile.mkdirs()) {
                warn(INIT, "Failed to create config directory: " + configDirFile);
            }

            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(configPath.toFile()), StandardCharsets.UTF_8))) {
                writer.println("{");
                writer.println("  \"blacklist\": [");
                for (String example : COMMENT_EXAMPLES) writer.println(example);
                writer.println("  ]");
                writer.println("}");
                info(INIT, "Created default loot_blacklist config at " + configPath);
            } catch (Exception e) {
                error(INIT, "Failed to write default loot_blacklist config!");
            }
        }

        info(INIT, "Loaded blacklist config with " +
                config.rawBlacklist.size() + " " +
                pluralize(config.rawBlacklist.size(), "entry", "entries"));
        return config;
    }
}
