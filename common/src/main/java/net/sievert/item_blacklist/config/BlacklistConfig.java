package net.sievert.item_blacklist.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import net.minecraft.resources.ResourceLocation;
import net.sievert.item_blacklist.platform.ItemBlacklistServices;
import net.sievert.item_blacklist.util.ItemBlacklistLogs;

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

import static net.sievert.item_blacklist.util.ItemBlacklistLogTags.CONFIG;

public class BlacklistConfig {

    private static final String CONFIG_FILE = "item_blacklist.jsonc";
    private static final String TAG_PREFIX = "#";
    private static final String VANILLA_NAMESPACE = "minecraft";

    private static final List<String> COMMENT_EXAMPLES = List.of(
            "    // \"minecraft:oak_planks\",",
            "    // \"#minecraft:planks\",",
            "    // \"mod_id:mod_item\",",
            "    // \"#mod_id:mod_tag\""
    );

    public final Set<ResourceLocation> blacklist = new HashSet<>();
    public final Set<ResourceLocation> blacklistTags = new HashSet<>();
    public boolean detailedLog;

    public static BlacklistConfig loadOrCreate() {
        Path configDir = ItemBlacklistServices.environment().getConfigDirectory();
        Path configPath = configDir.resolve(CONFIG_FILE);

        BlacklistConfig config = new BlacklistConfig();

        if (configPath.toFile().exists()) {
            try (Reader reader = new InputStreamReader(
                    new FileInputStream(configPath.toFile()),
                    StandardCharsets.UTF_8
            )) {
                JsonReader jsonReader = new JsonReader(reader);
                jsonReader.setLenient(true);

                JsonElement root = JsonParser.parseReader(jsonReader);

                if (!root.isJsonObject()) {
                    throw new JsonSyntaxException("Root element is not a JSON object");
                }

                JsonObject object = root.getAsJsonObject();

                JsonElement detailedLog = object.get("Detailed Log");
                if (detailedLog != null
                        && detailedLog.isJsonPrimitive()
                        && detailedLog.getAsJsonPrimitive().isBoolean()) {
                    config.detailedLog = detailedLog.getAsBoolean();
                }

                JsonArray blacklist = object.getAsJsonArray("Blacklist");

                if (blacklist != null) {
                    for (JsonElement element : blacklist) {
                        if (
                                !element.isJsonPrimitive()
                                        || !element.getAsJsonPrimitive().isString()
                        ) {
                            ItemBlacklistLogs.warn(
                                    CONFIG,
                                    "Ignoring non-string blacklist entry: {}",
                                    element
                            );
                            continue;
                        }

                        parseEntry(
                                config,
                                element.getAsString()
                        );
                    }
                }
            } catch (Exception exception) {
                ItemBlacklistLogs.warn(
                        CONFIG,
                        "Failed to load config: malformed JSONC. Using empty blacklist."
                );
            }
        } else {
            File configDirFile = configDir.toFile();

            if (!configDirFile.exists() && !configDirFile.mkdirs()) {
                ItemBlacklistLogs.warn(
                        CONFIG,
                        "Failed to create config directory: {}",
                        configDirFile
                );
            }

            try (PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(configPath.toFile()),
                            StandardCharsets.UTF_8
                    )
            )) {
                writer.println("{");
                writer.println("  \"Detailed Log\": false,");
                writer.println("  \"Blacklist\": [");

                for (String example : COMMENT_EXAMPLES) {
                    writer.println(example);
                }

                writer.println("  ]");
                writer.println("}");

                ItemBlacklistLogs.info(
                        CONFIG,
                        "Created default Item Blacklist config at {}",
                        configPath
                );
            } catch (Exception exception) {
                ItemBlacklistLogs.error(
                        CONFIG,
                        "Failed to write default Item Blacklist config!"
                );
            }
        }

        logLoadedEntries(config);

        return config;
    }

    private static void parseEntry(
            BlacklistConfig config,
            String rawEntry
    ) {
        String entry = rawEntry.trim();

        if (entry.isEmpty()) {
            ItemBlacklistLogs.warn(
                    CONFIG,
                    "Ignoring empty blacklist entry"
            );
            return;
        }

        if (entry.startsWith(TAG_PREFIX)) {
            parseTagEntry(config, entry);
        } else {
            parseItemEntry(config, entry);
        }
    }

    private static void parseItemEntry(
            BlacklistConfig config,
            String entry
    ) {
        ResourceLocation itemId = parseNamespacedId(entry);

        if (itemId == null) {
            ItemBlacklistLogs.warn(
                    CONFIG,
                    "Ignoring invalid item entry: {}",
                    entry
            );
            return;
        }

        config.blacklist.add(itemId);
    }

    private static void parseTagEntry(
            BlacklistConfig config,
            String entry
    ) {
        String rawTagId = entry.substring(TAG_PREFIX.length());
        ResourceLocation tagId = parseNamespacedId(rawTagId);

        if (tagId == null) {
            ItemBlacklistLogs.warn(
                    CONFIG,
                    "Ignoring invalid tag entry: {}",
                    entry
            );
            return;
        }

        config.blacklistTags.add(tagId);
    }

    private static ResourceLocation parseNamespacedId(String entry) {
        if (!entry.contains(":")) {
            return null;
        }

        return ResourceLocation.tryParse(entry);
    }

    private static void logLoadedEntries(BlacklistConfig config) {
        long vanillaSingles = config.blacklist.stream()
                .filter(id -> id.getNamespace().equals(VANILLA_NAMESPACE))
                .count();

        long vanillaTags = config.blacklistTags.stream()
                .filter(id -> id.getNamespace().equals(VANILLA_NAMESPACE))
                .count();

        long moddedSingles = config.blacklist.size() - vanillaSingles;
        long moddedTags = config.blacklistTags.size() - vanillaTags;

        ItemBlacklistLogs.info(
                CONFIG,
                "Loaded blacklist config with {} vanilla {}, {} vanilla {}, {} modded {}, and {} modded {}",
                vanillaSingles,
                vanillaSingles == 1 ? "item" : "items",
                vanillaTags,
                vanillaTags == 1 ? "tag" : "tags",
                moddedSingles,
                moddedSingles == 1 ? "item" : "items",
                moddedTags,
                moddedTags == 1 ? "tag" : "tags"
        );
    }
}