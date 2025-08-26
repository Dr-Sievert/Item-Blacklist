package net.sievert.item_blacklist;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sievert.item_blacklist.BlacklistLogger;
import net.sievert.item_blacklist.BlacklistLogger.Group;

/**
 * Configuration class for Item Blacklist.
 * Loads blacklist entries from JSON config and
 * provides both raw and validated sets.
 */
public class BlacklistConfig {
    private static final String CONFIG_FILE = "item_blacklist.json";
    private static final List<String> COMMENT_EXAMPLES = List.of(
            "    // \"minecraft:iron_ingot\",",
            "    // \"mod_id:mod_item\""
    );

    /** Raw identifiers from JSON (never mutated after load). */
    public final Set<String> rawBlacklist = new HashSet<>();

    /** Working/validated list of identifiers set by validation logic. */
    public Set<Identifier> blacklist = new HashSet<>();

    /** If true, logs every individual loot entry removed and which table it was removed from. */
    public boolean detailedLootTableLog = false;

    /** If true, logs every individual recipe removed and which entry caused the removal. */
    public boolean detailedRecipeLog = false;

    /** If true, logs every individual villager trade removed and which item caused the removal. */
    public boolean detailedTradeLog = false;

    /** If true, logs every individual tag entry removed and which tag it was removed from. */
    public boolean detailedTagLog = false;

    /**
     * Loads the item blacklist config from file, or creates a default one if missing.
     * Does not perform validation on entries.
     */
    public static BlacklistConfig loadOrCreate() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configPath = configDir.resolve(CONFIG_FILE);

        BlacklistConfig config = new BlacklistConfig();

        if (configPath.toFile().exists()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(configPath.toFile()), StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (!root.isJsonObject()) throw new JsonSyntaxException("Root element is not a JSON object");

                JsonObject obj = root.getAsJsonObject();

                JsonElement lootLog = obj.get("Detailed Loot Table Log");
                if (lootLog != null && lootLog.isJsonPrimitive()) {
                    config.detailedLootTableLog = lootLog.getAsBoolean();
                }

                JsonElement recipeLog = obj.get("Detailed Recipe Log");
                if (recipeLog != null && recipeLog.isJsonPrimitive()) {
                    config.detailedRecipeLog = recipeLog.getAsBoolean();
                }

                JsonElement tradeLog = obj.get("Detailed Trade Log");
                if (tradeLog != null && tradeLog.isJsonPrimitive()) {
                    config.detailedTradeLog = tradeLog.getAsBoolean();
                }

                JsonElement tagLog = obj.get("Detailed Tag Log");
                if (tagLog != null && tagLog.isJsonPrimitive()) {
                    config.detailedTagLog = tagLog.getAsBoolean();
                }

                JsonArray arr = obj.getAsJsonArray("Blacklist");
                if (arr != null) {
                    for (JsonElement el : arr) {
                        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                            config.rawBlacklist.add(el.getAsString());
                        }
                    }
                }
            } catch (Exception e) {
                BlacklistLogger.warn(Group.INIT, "Failed to load config: malformed JSON. Using empty blacklist.");
            }
        } else {
            File configDirFile = configDir.toFile();
            if (!configDirFile.exists() && !configDirFile.mkdirs()) {
                BlacklistLogger.warn(Group.INIT, "Failed to create config directory: " + configDirFile);
            }

            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(configPath.toFile()), StandardCharsets.UTF_8))) {
                writer.println("{");
                writer.println("  \"Detailed Loot Table Log\": false,");
                writer.println("  \"Detailed Recipe Log\": false,");
                writer.println("  \"Detailed Trade Log\": false,");
                writer.println("  \"Detailed Tag Log\": false,");
                writer.println("  \"Blacklist\": [");
                for (String example : COMMENT_EXAMPLES) writer.println(example);
                writer.println("  ]");
                writer.println("}");
                BlacklistLogger.info(Group.INIT, "Created default item_blacklist config at " + configPath);
            } catch (Exception e) {
                BlacklistLogger.error(Group.INIT, "Failed to write default item_blacklist config!");
            }
        }

        BlacklistLogger.info(Group.INIT, "Loaded blacklist config with " +
                config.rawBlacklist.size() + " " +
                BlacklistLogger.pluralize(config.rawBlacklist.size(), "entry", "entries"));
        return config;
    }
}
