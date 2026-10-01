package net.sievert.item_blacklist.config;

import net.sievert.item_blacklist.id.IdText;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

/**
 * The config file's text to a {@link BlacklistConfig}. It never throws and logs nothing: every
 * input it refuses becomes one warning naming the input, and the parse goes on with the rest,
 * so one bad entry never costs the others. The caller logs the warnings.
 */
public final class ConfigParser {
    /** The key of the flag that adds the report's detail lines. */
    public static final String DETAILED_LOG = "Detailed Log";
    /** The key of the item list; an entry with a leading '#' is an item tag. */
    public static final String ITEMS = "Items";
    /** The key of the potion list; potions have no tags. */
    public static final String POTIONS = "Potions";
    /** The key of the enchantment list; an entry with a leading '#' is an enchantment tag. */
    public static final String ENCHANTMENTS = "Enchantments";

    private static final String TAG_PREFIX = "#";

    /**
     * What a parse gives: the config, and one warning per refused input, in file order.
     *
     * @param config the config; EMPTY when the text is no JSON object
     * @param warnings one line per refused input, without the log prefix
     */
    public record Result(BlacklistConfig config, List<String> warnings) {
        /** Copies the warnings, so the result cannot change after the parse. */
        public Result {
            warnings = List.copyOf(warnings);
        }
    }

    private ConfigParser() {
    }

    /**
     * Parses a config text. The JsonReader is lenient by itself when JsonParser reads it, in
     * every Gson the releases ship, so comments and a trailing comma in a list are accepted
     * without setLenient; a leading byte order mark is skipped by Gson.
     */
    public static Result parse(Reader reader) {
        List<String> warnings = new ArrayList<>();
        JsonElement root;
        try {
            root = JsonParser.parseReader(new JsonReader(reader));
        } catch (RuntimeException e) {
            warnings.add("The config is not valid JSONC (" + e.getMessage()
                    + "); using an empty blacklist");
            return new Result(BlacklistConfig.EMPTY, warnings);
        }
        // An empty file parses to JsonNull, which lands here too.
        if (!root.isJsonObject()) {
            warnings.add("The config's root is not an object; using an empty blacklist");
            return new Result(BlacklistConfig.EMPTY, warnings);
        }
        JsonObject object = root.getAsJsonObject();

        boolean detailed = detailedLog(object, warnings);

        Set<IdText> items = new LinkedHashSet<>();
        Set<IdText> itemTags = new LinkedHashSet<>();
        for (String entry : strings(object, ITEMS, "item", warnings)) {
            if (entry.startsWith(TAG_PREFIX)) {
                add(entry, entry.substring(1), "tag", itemTags, warnings);
            } else {
                add(entry, entry, "item", items, warnings);
            }
        }

        Set<IdText> potions = new LinkedHashSet<>();
        for (String entry : strings(object, POTIONS, "potion", warnings)) {
            if (entry.startsWith(TAG_PREFIX)) {
                warnings.add("Ignoring potion entry " + entry + ": potions have no tags");
            } else {
                add(entry, entry, "potion", potions, warnings);
            }
        }

        Set<IdText> enchantments = new LinkedHashSet<>();
        Set<IdText> enchantmentTags = new LinkedHashSet<>();
        for (String entry : strings(object, ENCHANTMENTS, "enchantment", warnings)) {
            if (entry.startsWith(TAG_PREFIX)) {
                add(entry, entry.substring(1), "enchantment tag", enchantmentTags, warnings);
            } else {
                add(entry, entry, "enchantment", enchantments, warnings);
            }
        }

        BlacklistConfig config = new BlacklistConfig(detailed, List.copyOf(items),
                List.copyOf(itemTags), List.copyOf(potions), List.copyOf(enchantments),
                List.copyOf(enchantmentTags));
        return new Result(config, warnings);
    }

    /** "Detailed Log": absent or null is false; anything but a boolean warns and is false. */
    private static boolean detailedLog(JsonObject object, List<String> warnings) {
        JsonElement value = object.get(DETAILED_LOG);
        if (value == null || value.isJsonNull()) {
            return false;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            return value.getAsBoolean();
        }
        warnings.add("\"" + DETAILED_LOG + "\" is not true or false (" + value + "); using false");
        return false;
    }

    /**
     * The trimmed, non-empty strings of one list. A list that is no array warns and counts as
     * empty, and the other lists are kept. A null element is what lenient Gson reads for a
     * trailing comma, so it is skipped without a warning.
     */
    private static List<String> strings(JsonObject object, String key, String kind,
            List<String> warnings) {
        List<String> out = new ArrayList<>();
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) {
            return out;
        }
        if (!value.isJsonArray()) {
            warnings.add("\"" + key + "\" is not a list (" + value + "); ignored");
            return out;
        }
        JsonArray array = value.getAsJsonArray();
        for (JsonElement element : array) {
            if (element.isJsonNull()) {
                continue;
            }
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                warnings.add("Ignoring non-string " + kind + " entry: " + element);
                continue;
            }
            String text = element.getAsString().trim();
            if (text.isEmpty()) {
                warnings.add("Ignoring empty " + kind + " entry");
                continue;
            }
            out.add(text);
        }
        return out;
    }

    /**
     * Adds the id of {@code id} to {@code into}, or warns naming {@code entry} as written (a tag
     * keeps its '#'). A duplicate is dropped without a warning: the set keeps the first.
     */
    private static void add(String entry, String id, String kind, Set<IdText> into,
            List<String> warnings) {
        Optional<IdText> parsed = IdText.parse(id);
        if (parsed.isEmpty()) {
            warnings.add("Ignoring invalid " + kind + " entry: " + entry);
            return;
        }
        into.add(parsed.get());
    }
}
