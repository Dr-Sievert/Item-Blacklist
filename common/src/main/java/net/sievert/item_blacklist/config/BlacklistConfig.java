package net.sievert.item_blacklist.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import net.minecraft.resources.ResourceLocation;
import net.sievert.item_blacklist.util.ItemBlacklistLogs;

import java.io.BufferedWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

    public static BlacklistConfig loadOrCreate(
            Path configDirectory
    ) {
        Path configPath =
                configDirectory.resolve(CONFIG_FILE);

        BlacklistConfig config =
                new BlacklistConfig();

        if (Files.exists(configPath)) {
            load(
                    config,
                    configPath
            );
        } else {
            createDefault(
                    configDirectory,
                    configPath
            );
        }

        logLoadedEntries(config);

        return config;
    }

    private static void load(
            BlacklistConfig config,
            Path configPath
    ) {
        try (Reader reader = Files.newBufferedReader(
                configPath,
                StandardCharsets.UTF_8
        )) {
            JsonReader jsonReader =
                    new JsonReader(reader);

            jsonReader.setLenient(true);

            JsonElement root =
                    JsonParser.parseReader(jsonReader);

            if (!root.isJsonObject()) {
                throw new JsonSyntaxException(
                        "Root element is not a JSON object"
                );
            }

            JsonObject object =
                    root.getAsJsonObject();

            JsonElement detailedLog =
                    object.get("Detailed Log");

            if (detailedLog != null
                    && detailedLog.isJsonPrimitive()
                    && detailedLog.getAsJsonPrimitive().isBoolean()) {
                config.detailedLog =
                        detailedLog.getAsBoolean();
            }

            JsonArray blacklist =
                    object.getAsJsonArray("Blacklist");

            if (blacklist == null) {
                return;
            }

            for (JsonElement element : blacklist) {
                if (!element.isJsonPrimitive()
                        || !element.getAsJsonPrimitive().isString()) {
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
        } catch (Exception exception) {
            ItemBlacklistLogs.warn(
                    CONFIG,
                    "Failed to load config: malformed JSONC. Using empty blacklist."
            );
        }
    }

    private static void createDefault(
            Path configDirectory,
            Path configPath
    ) {
        try {
            Files.createDirectories(
                    configDirectory
            );

            try (BufferedWriter writer =
                         Files.newBufferedWriter(
                                 configPath,
                                 StandardCharsets.UTF_8
                         )) {
                writer.write("{");
                writer.newLine();

                writer.write(
                        "  \"Detailed Log\": false,"
                );
                writer.newLine();

                writer.write(
                        "  \"Blacklist\": ["
                );
                writer.newLine();

                for (String example : COMMENT_EXAMPLES) {
                    writer.write(example);
                    writer.newLine();
                }

                writer.write("  ]");
                writer.newLine();

                writer.write("}");
                writer.newLine();
            }

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

    private static void parseEntry(
            BlacklistConfig config,
            String rawEntry
    ) {
        String entry =
                rawEntry.trim();

        if (entry.isEmpty()) {
            ItemBlacklistLogs.warn(
                    CONFIG,
                    "Ignoring empty blacklist entry"
            );

            return;
        }

        boolean tag =
                entry.startsWith(TAG_PREFIX);

        String rawId = tag
                ? entry.substring(TAG_PREFIX.length())
                : entry;

        ResourceLocation id =
                parseNamespacedId(rawId);

        if (id == null) {
            ItemBlacklistLogs.warn(
                    CONFIG,
                    "Ignoring invalid {} entry: {}",
                    tag ? "tag" : "item",
                    entry
            );

            return;
        }

        if (tag) {
            config.blacklistTags.add(id);
        } else {
            config.blacklist.add(id);
        }
    }

    private static ResourceLocation parseNamespacedId(
            String entry
    ) {
        if (!entry.contains(":")) {
            return null;
        }

        return ResourceLocation.tryParse(entry);
    }

    private static void logLoadedEntries(
            BlacklistConfig config
    ) {
        long vanillaItems = config.blacklist.stream()
                .filter(id ->
                        id.getNamespace().equals(
                                VANILLA_NAMESPACE
                        )
                )
                .count();

        long vanillaTags = config.blacklistTags.stream()
                .filter(id ->
                        id.getNamespace().equals(
                                VANILLA_NAMESPACE
                        )
                )
                .count();

        long moddedItems =
                config.blacklist.size() - vanillaItems;

        long moddedTags =
                config.blacklistTags.size() - vanillaTags;

        ItemBlacklistLogs.info(
                CONFIG,
                "Loaded blacklist config with {} vanilla {}, {} vanilla {}, {} modded {}, and {} modded {}",
                vanillaItems,
                vanillaItems == 1 ? "item" : "items",
                vanillaTags,
                vanillaTags == 1 ? "tag" : "tags",
                moddedItems,
                moddedItems == 1 ? "item" : "items",
                moddedTags,
                moddedTags == 1 ? "tag" : "tags"
        );
    }
}