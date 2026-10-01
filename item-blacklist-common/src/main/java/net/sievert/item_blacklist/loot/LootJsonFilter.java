package net.sievert.item_blacklist.loot;

import net.sievert.item_blacklist.blacklist.IdQuery;
import net.sievert.item_blacklist.id.IdText;
import net.sievert.item_blacklist.report.Recorder;
import java.util.ArrayList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * The loot JSON walk: removes what the blacklist names from one loot table's raw tree, in
 * place, before the game decodes it. It walks the loot schema (pools, entries, children,
 * functions, sequence, set_contents, inline tables), not key names, so an array of another
 * shape that happens to be called "entries" is never touched, and unknown entry and function
 * types are left alone. It reads the blacklist as text (IdQuery) and names no game type, so
 * its unit tests run without a game. What the tree cannot show (set_item, item modifiers, a
 * loader's additions) is the roll filter's (LootRolls).
 */
public final class LootJsonFilter {
    /** The directory of loot tables inside a data pack namespace, with its slash. */
    private static final String DIRECTORY = "loot_table/";

    private static final String EXTENSION = ".json";
    private static final String EMPTY_ENTRY = "minecraft:empty";
    private static final String ITEM = "minecraft:item";
    private static final String TAG = "minecraft:tag";
    private static final String ALTERNATIVES = "minecraft:alternatives";
    private static final String GROUP = "minecraft:group";
    private static final String SEQUENCE_ENTRY = "minecraft:sequence";
    private static final String LOOT_TABLE = "minecraft:loot_table";
    private static final String DYNAMIC = "minecraft:dynamic";
    private static final String SLOTS = "minecraft:slots";
    private static final String SET_POTION = "minecraft:set_potion";
    private static final String SET_ENCHANTMENTS = "minecraft:set_enchantments";
    private static final String SEQUENCE_FUNCTION = "minecraft:sequence";
    private static final String SET_CONTENTS = "minecraft:set_contents";

    private LootJsonFilter() {
    }

    /**
     * Filters one loot table's JSON tree in place; true when it changed something. A tree that
     * is no object is left alone: the codec refuses it itself. Every removal of an entry, a
     * potion function or an enchantment is recorded as lootRemoval(tableId, cause), the cause
     * being the id that caused it ("#" before a tag).
     */
    public static boolean filter(JsonElement table, String tableId, IdQuery query,
            Recorder recorder) {
        if (!(table instanceof JsonObject root)) {
            return false;
        }
        Walk walk = new Walk(tableId, query, recorder);
        walk.table(root);
        return walk.changed;
    }

    /**
     * The table id of a loot table's file id, "ns:loot_table/a/b.json" to "ns:a/b"; null for a
     * file outside loot_table/ or without .json. The text form of FileToIdConverter.fileToId,
     * so the hooks that see a file id name no id class; it is also their scope gate: recipes,
     * predicates, item modifiers and loot modifiers pass untouched.
     */
    public static String tableIdOfFile(String fileId) {
        if (fileId == null) {
            return null;
        }
        int colon = fileId.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        String namespace = fileId.substring(0, colon);
        String path = fileId.substring(colon + 1);
        if (!path.startsWith(DIRECTORY) || !path.endsWith(EXTENSION)) {
            return null;
        }
        String table = path.substring(DIRECTORY.length(), path.length() - EXTENSION.length());
        if (table.isEmpty()) {
            return null;
        }
        return namespace + ":" + table;
    }

    /**
     * The normalised id at key ("item" becomes "minecraft:item"), or null when the key is
     * absent, holds no string, or holds no valid id. One normaliser for every id the walk
     * reads, so a bare id and a full one are judged alike.
     */
    private static String id(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (!(value instanceof JsonPrimitive primitive) || !primitive.isString()) {
            return null;
        }
        return normalised(primitive.getAsString());
    }

    /** The text in "namespace:path" form, or null when it is no valid id. */
    private static String normalised(String text) {
        return IdText.parseLenient(text).map(IdText::toString).orElse(null);
    }

    /** The array at key, or null when the key is absent or holds no array. */
    private static JsonArray array(JsonObject object, String key) {
        return object.get(key) instanceof JsonArray array ? array : null;
    }

    /** A new {"type":"minecraft:empty"}, which decodes with weight 1. */
    private static JsonObject emptyEntry() {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", EMPTY_ENTRY);
        return entry;
    }

    /** One walk over one table: the query, the recorder and whether anything went. */
    private static final class Walk {
        private final String tableId;
        private final IdQuery query;
        private final Recorder recorder;
        private boolean changed;

        Walk(String tableId, IdQuery query, Recorder recorder) {
            this.tableId = tableId;
            this.query = query;
            this.recorder = recorder;
        }

        /** A table object: its pools and its table-level functions; other keys are not read. */
        void table(JsonObject table) {
            JsonArray pools = array(table, "pools");
            if (pools != null) {
                for (JsonElement pool : pools) {
                    if (pool instanceof JsonObject object) {
                        pool(object);
                    }
                }
            }
            functions(array(table, "functions"));
        }

        /** A pool object: its entries and its pool-level functions. */
        void pool(JsonObject pool) {
            entries(array(pool, "entries"));
            functions(array(pool, "functions"));
        }

        /**
         * An entries or children array: removes the entries that must go, walks the rest, and
         * puts one empty entry in when a removal of this walk emptied it. An array that was
         * empty in the source stays empty; a non-object element is kept untouched.
         */
        void entries(JsonArray entries) {
            if (entries == null) {
                return;
            }
            boolean removedHere = false;
            for (int i = entries.size() - 1; i >= 0; i--) {
                if (entries.get(i) instanceof JsonObject object && entry(object)) {
                    entries.remove(i);
                    removedHere = true;
                }
            }
            if (removedHere && entries.size() == 0) {
                entries.add(emptyEntry());
            }
        }

        /** One entry object; true when it must go. Walks into what it keeps. */
        boolean entry(JsonObject entry) {
            String type = id(entry, "type");
            if (type == null) {
                return false;
            }
            switch (type) {
                case ITEM -> {
                    String name = id(entry, "name");
                    if (name != null && query.itemId(name)) {
                        removed(name);
                        return true;
                    }
                    return singleton(entry);
                }
                case TAG -> {
                    // Tag names carry no '#'. Only a tag the config names goes; members of
                    // other tags are the roll filter's.
                    String name = id(entry, "name");
                    if (name != null && query.itemTagId(name)) {
                        removed("#" + name);
                        return true;
                    }
                    return singleton(entry);
                }
                case ALTERNATIVES, GROUP, SEQUENCE_ENTRY -> {
                    entries(array(entry, "children"));
                    return false;
                }
                case LOOT_TABLE -> {
                    // "value" is a table id or an inline table; only the inline one is data.
                    if (entry.get("value") instanceof JsonObject inline) {
                        table(inline);
                    }
                    return singleton(entry);
                }
                case EMPTY_ENTRY, DYNAMIC, SLOTS -> {
                    return singleton(entry);
                }
                default -> {
                    return false;
                }
            }
        }

        /**
         * A singleton entry: true when its own functions hold a set_potion of a blacklisted
         * potion (the entry goes as a whole, as in the old mod); else its functions are walked.
         */
        boolean singleton(JsonObject entry) {
            JsonArray functions = array(entry, "functions");
            if (functions == null) {
                return false;
            }
            for (JsonElement element : functions) {
                if (element instanceof JsonObject function
                        && SET_POTION.equals(id(function, "function"))) {
                    String potion = id(function, "id");
                    if (potion != null && query.potionId(potion)) {
                        removed(potion);
                        return true;
                    }
                }
            }
            functions(functions);
            return false;
        }

        /**
         * A functions array: removes a set_potion of a blacklisted potion, strips blacklisted
         * keys from set_enchantments (and the function once its map is empty), walks sequence
         * and set_contents. Inline function arrays, filtered, reference and modded types stay.
         */
        void functions(JsonArray functions) {
            if (functions == null) {
                return;
            }
            for (int i = functions.size() - 1; i >= 0; i--) {
                if (!(functions.get(i) instanceof JsonObject function)) {
                    continue;
                }
                String name = id(function, "function");
                if (name == null) {
                    continue;
                }
                switch (name) {
                    case SET_POTION -> {
                        // Only the key "id": the old fallback key "potion" never decoded.
                        String potion = id(function, "id");
                        if (potion != null && query.potionId(potion)) {
                            functions.remove(i);
                            removed(potion);
                        }
                    }
                    case SET_ENCHANTMENTS -> {
                        if (function.get("enchantments") instanceof JsonObject levels) {
                            enchantments(levels);
                            // Also a map empty in the source: the old mod removed it too.
                            if (levels.size() == 0) {
                                functions.remove(i);
                                removed(null);
                            }
                        }
                    }
                    case SEQUENCE_FUNCTION -> functions(array(function, "functions"));
                    case SET_CONTENTS -> entries(array(function, "entries"));
                    default -> {
                    }
                }
            }
        }

        /** Removes the blacklisted enchantments of one set_enchantments map. */
        private void enchantments(JsonObject levels) {
            for (String key : new ArrayList<>(levels.keySet())) {
                String enchantment = normalised(key);
                if (enchantment != null && query.enchantmentId(enchantment)) {
                    levels.remove(key);
                    removed(enchantment);
                }
            }
        }

        /** A removal: the walk changed the tree, and with a cause it is recorded. */
        private void removed(String cause) {
            changed = true;
            if (cause != null) {
                recorder.lootRemoval(tableId, cause);
            }
        }
    }
}
