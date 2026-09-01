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

    private static final List<String> COMMENT_EXAMPLES = List.of(
            "    // \"minecraft:oak_planks\",",
            "    // \"#minecraft:planks\",",
            "    // \"mod_id:mod_item\",",
            "    // \"#mod_id:mod_tag\""
    );

    public final Set<String> rawBlacklist = new HashSet<>();

    public final Set<ResourceLocation> blacklist = new HashSet<>();
    public final Set<ResourceLocation> blacklistTags = new HashSet<>();

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
                JsonArray blacklist = object.getAsJsonArray("Blacklist");

                if (blacklist != null) {
                    for (JsonElement element : blacklist) {
                        if (
                                element.isJsonPrimitive()
                                        && element.getAsJsonPrimitive().isString()
                        ) {
                            config.rawBlacklist.add(element.getAsString());
                        }
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

        long vanillaSingles = config.rawBlacklist.stream()
                .filter(entry -> !entry.startsWith("#"))
                .filter(entry -> entry.startsWith("minecraft:"))
                .count();

        long vanillaTags = config.rawBlacklist.stream()
                .filter(entry -> entry.startsWith("#minecraft:"))
                .count();

        long moddedSingles = config.rawBlacklist.stream()
                .filter(entry -> !entry.startsWith("#"))
                .filter(entry -> !entry.startsWith("minecraft:"))
                .count();

        long moddedTags = config.rawBlacklist.stream()
                .filter(entry -> entry.startsWith("#"))
                .filter(entry -> !entry.startsWith("#minecraft:"))
                .count();

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

        return config;
    }
}