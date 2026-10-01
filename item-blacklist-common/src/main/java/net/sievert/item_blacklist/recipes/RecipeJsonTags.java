package net.sievert.item_blacklist.recipes;

import net.sievert.item_blacklist.id.IdText;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The item tag ids a 1.21.1 recipe document names in "tag" members ({"tag": "ns:path"}). On
 * 1.21.1 a parsed ingredient keeps no tag key, so the 1.21.1 pass learns which tags a recipe
 * names from its JSON, read before the parse and never edited. JDK and Gson only, so the walk
 * is unit-tested without a game.
 */
public final class RecipeJsonTags {
    /** Members whose subtree is never searched: load conditions, whose "tag" is no ingredient. */
    static final Set<String> SKIPPED_MEMBERS =
            Set.of("neoforge:conditions", "fabric:load_conditions");
    /** "type" values of objects whose "tag" is no item tag: NeoForge's block tag ingredient. */
    static final Set<String> FOREIGN_TAG_TYPES = Set.of("neoforge:block_tag");

    private RecipeJsonTags() {
    }

    /**
     * Per recipe id text (String.valueOf of the map key), the tag ids its document names;
     * recipes naming none are left out. Unmodifiable.
     */
    public static Map<String, Set<String>> collect(Map<?, JsonElement> recipes) {
        Map<String, Set<String>> out = new HashMap<>();
        for (Map.Entry<?, JsonElement> entry : recipes.entrySet()) {
            Set<String> tags = tagNames(entry.getValue());
            if (!tags.isEmpty()) {
                out.put(String.valueOf(entry.getKey()), tags);
            }
        }
        return Map.copyOf(out);
    }

    /**
     * The tag ids of one document as "namespace:path", first seen first; a leading '#' is
     * dropped and a missing namespace is "minecraft". Empty for anything that is no object or
     * array. Unmodifiable.
     */
    public static Set<String> tagNames(JsonElement recipe) {
        Set<String> out = new LinkedHashSet<>();
        walk(recipe, out);
        return Collections.unmodifiableSet(out);
    }

    private static void walk(JsonElement element, Set<String> out) {
        if (element == null) {
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                walk(child, out);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        String type = string(object.get("type"));
        if (type != null && FOREIGN_TAG_TYPES.contains(type)) {
            return;
        }
        String tag = string(object.get("tag"));
        if (tag != null) {
            IdText.parseLenient(tag.startsWith("#") ? tag.substring(1) : tag)
                    .ifPresent(id -> out.add(id.toString()));
        }
        for (Map.Entry<String, JsonElement> member : object.entrySet()) {
            if (!SKIPPED_MEMBERS.contains(member.getKey())) {
                walk(member.getValue(), out);
            }
        }
    }

    /** The text of a JSON string, else null (a number, an object, null or nothing). */
    private static String string(JsonElement element) {
        return element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString() ? element.getAsString() : null;
    }
}
