package net.sievert.item_blacklist.recipes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * The 1.21.1 walk for the item tags a recipe document names, with no game running: tags in
 * keys, lists and nested custom ingredients are found in document order, with the default
 * namespace and without '#'; load conditions and NeoForge's block tag ingredient are skipped;
 * anything that is no id, no string or no object gives nothing; the results are unmodifiable.
 */
class RecipeJsonTagsTest {
    private static final String STICK = "{\"type\":\"minecraft:crafting_shaped\","
            + "\"key\":{\"#\":{\"tag\":\"minecraft:planks\"}},"
            + "\"result\":{\"id\":\"minecraft:stick\"}}";

    @Test
    void shapedKeyTag() {
        assertNames(List.of("minecraft:planks"), STICK);
    }

    @Test
    void defaultNamespace() {
        assertNames(List.of("minecraft:planks"), "{\"ingredients\":[{\"tag\":\"planks\"}]}");
    }

    @Test
    void hashStripped() {
        assertNames(List.of("minecraft:logs"), "{\"ingredient\":{\"tag\":\"#minecraft:logs\"}}");
    }

    @Test
    void alternativesInArrays() {
        assertNames(List.of("minecraft:coals", "c:rods/wooden"),
                "{\"ingredients\":[[{\"item\":\"minecraft:coal\"},{\"tag\":\"minecraft:coals\"}],"
                        + "{\"tag\":\"c:rods/wooden\"}]}");
    }

    @Test
    void neoforgeConditionsSkipped() {
        assertNames(List.of(), "{\"neoforge:conditions\":[{\"type\":\"neoforge:tag_empty\","
                + "\"tag\":\"minecraft:planks\"}],"
                + "\"ingredients\":[{\"item\":\"minecraft:stone\"}]}");
    }

    @Test
    void fabricConditionsSkipped() {
        assertNames(List.of(), "{\"fabric:load_conditions\":[{\"condition\":"
                + "\"fabric:tags_populated\",\"tag\":\"minecraft:planks\"}]}");
    }

    @Test
    void blockTagIngredientSkipped() {
        assertNames(List.of(), "{\"ingredients\":[{\"type\":\"neoforge:block_tag\","
                + "\"tag\":\"minecraft:planks\"}]}");
    }

    @Test
    void nestedCustomIngredient() {
        assertNames(List.of("minecraft:logs"),
                "{\"ingredients\":[{\"type\":\"neoforge:difference\","
                + "\"base\":{\"tag\":\"minecraft:logs\"},"
                + "\"subtracted\":{\"item\":\"minecraft:oak_log\"}}]}");
    }

    @Test
    void nonStringTagIgnored() {
        assertNames(List.of(),
                "{\"a\":{\"tag\":5},\"b\":{\"tag\":{\"x\":1}},\"c\":{\"tag\":\"Bad Id\"}}");
    }

    @Test
    void nonObjectRoot() {
        assertEquals(Set.of(), RecipeJsonTags.tagNames(JsonParser.parseString("[]")), "[]");
        assertEquals(Set.of(), RecipeJsonTags.tagNames(JsonParser.parseString("\"text\"")),
                "a string");
        assertEquals(Set.of(), RecipeJsonTags.tagNames(JsonNull.INSTANCE), "JSON null");
        assertEquals(Set.of(), RecipeJsonTags.tagNames(null), "no element");
    }

    @Test
    void collectKeepsOnlyRecipesWithTags() {
        Map<String, JsonElement> recipes = new LinkedHashMap<>();
        recipes.put("minecraft:stick", JsonParser.parseString(STICK));
        recipes.put("minecraft:stone_bricks",
                JsonParser.parseString("{\"key\":{\"#\":{\"item\":\"minecraft:stone\"}}}"));
        assertEquals(Map.of("minecraft:stick", Set.of("minecraft:planks")),
                RecipeJsonTags.collect(recipes));
    }

    @Test
    void collectUsesKeyText() {
        Object key = new Object() {
            @Override
            public String toString() {
                return "ns:recipe";
            }
        };
        Map<Object, JsonElement> recipes = new LinkedHashMap<>();
        recipes.put(key, JsonParser.parseString(STICK));
        assertEquals(Set.of("ns:recipe"), RecipeJsonTags.collect(recipes).keySet());
    }

    @Test
    void resultIsUnmodifiable() {
        Set<String> names = RecipeJsonTags.tagNames(JsonParser.parseString(STICK));
        assertThrows(UnsupportedOperationException.class, () -> names.add("x"));
        Map<String, Set<String>> collected =
                RecipeJsonTags.collect(Map.of("minecraft:stick", JsonParser.parseString(STICK)));
        assertThrows(UnsupportedOperationException.class, () -> collected.put("x", Set.of()));
    }

    /** The document's tag names, in the order the walk found them. */
    private static void assertNames(List<String> expected, String document) {
        Set<String> names = RecipeJsonTags.tagNames(JsonParser.parseString(document));
        assertEquals(expected, List.copyOf(names), document);
    }
}
