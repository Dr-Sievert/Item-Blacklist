package net.sievert.item_blacklist.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.sievert.item_blacklist.blacklist.IdQuery;
import net.sievert.item_blacklist.report.Recorder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * The loot JSON walk on trees, with a fake IdQuery and no game: each rule of the walk (item and
 * tag entries, potion functions, enchantment maps, placeholders, nested tables and composites),
 * what it leaves alone, and the file-id gate of the hooks. Trees are written with ' for ".
 */
class LootJsonFilterTest {
    private static final String TABLE = "t:x";
    private static final Query QUERY = new Query(
            Set.of("minecraft:oak_planks", "minecraft:birch_planks"),
            Set.of("minecraft:planks"),
            Set.of("minecraft:strength"),
            Set.of("minecraft:mending", "minecraft:binding_curse"));
    private static final Query NOTHING = new Query(Set.of(), Set.of(), Set.of(), Set.of());

    private static final String OAK = "{'type':'minecraft:item','name':'minecraft:oak_planks'}";
    private static final String STONE = "{'type':'minecraft:item','name':'minecraft:stone'}";
    private static final String EMPTY = "{'type':'minecraft:empty'}";

    @Test
    void removesItemEntry() {
        Walked walked = walk(pool(OAK, STONE), QUERY);
        assertTrue(walked.changed());
        assertEquals(List.of("t:x <- minecraft:oak_planks"), walked.lines());
        assertEquals(parse(pool(STONE)), walked.tree());
    }

    @Test
    void normalisesBareIds() {
        Walked walked = walk(pool("{'type':'item','name':'birch_planks'}", STONE), QUERY);
        assertTrue(walked.changed());
        assertEquals(List.of("t:x <- minecraft:birch_planks"), walked.lines());
        assertEquals(parse(pool(STONE)), walked.tree());
    }

    @Test
    void refusesInvalidIds() {
        String source = pool("{'type':'minecraft:item','name':'Minecraft:Oak_Planks'}",
                "{'type':'minecraft:item','name':42}",
                "{'name':'minecraft:oak_planks'}");
        Walked walked = walk(source, QUERY);
        assertFalse(walked.changed());
        assertEquals(List.of(), walked.lines());
        assertEquals(parse(source), walked.tree());
    }

    @Test
    void removesNamedTagEntryOnly() {
        String logs = "{'type':'minecraft:tag','name':'minecraft:logs','expand':true}";
        Walked walked = walk(
                pool("{'type':'minecraft:tag','name':'minecraft:planks','expand':true}", logs),
                QUERY);
        assertTrue(walked.changed());
        assertEquals(List.of("t:x <- #minecraft:planks"), walked.lines());
        assertEquals(parse(pool(logs)), walked.tree());
    }

    @Test
    void removesEntryWithOwnBlacklistedPotion() {
        Walked walked = walk(pool(potionEntry("minecraft:strength"), STONE), QUERY);
        assertTrue(walked.changed());
        assertEquals(List.of("t:x <- minecraft:strength"), walked.lines());
        assertEquals(parse(pool(STONE)), walked.tree());
    }

    @Test
    void removesPoolLevelPotionFunctionOnly() {
        String splash = "{'type':'minecraft:item','name':'minecraft:splash_potion'}";
        Walked walked = walk("{'pools':[{'rolls':1,'entries':[" + splash + "],"
                + "'functions':[{'function':'minecraft:set_potion','id':'minecraft:strength'}]}]}",
                QUERY);
        assertTrue(walked.changed());
        assertEquals(List.of("t:x <- minecraft:strength"), walked.lines());
        assertEquals(parse("{'pools':[{'rolls':1,'entries':[" + splash + "],'functions':[]}]}"),
                walked.tree());
    }

    @Test
    void removesPotionInsideSequence() {
        String setCount = "{'function':'minecraft:set_count','count':2}";
        Walked walked = walk(pool("{'type':'minecraft:item','name':'minecraft:potion',"
                + "'functions':[{'function':'minecraft:sequence','functions':["
                + "{'function':'minecraft:set_potion','id':'minecraft:strength'}," + setCount
                + "]}]}"), QUERY);
        assertTrue(walked.changed());
        assertEquals(List.of("t:x <- minecraft:strength"), walked.lines());
        // A sequence is not an own direct set_potion: the entry stays.
        assertEquals(parse(pool("{'type':'minecraft:item','name':'minecraft:potion',"
                + "'functions':[{'function':'minecraft:sequence','functions':[" + setCount
                + "]}]}")), walked.tree());
    }

    @Test
    void ignoresPotionFallbackKey() {
        String source = pool("{'type':'minecraft:item','name':'minecraft:potion','functions':"
                + "[{'function':'minecraft:set_potion','potion':'minecraft:strength'}]}");
        Walked walked = walk(source, QUERY);
        assertFalse(walked.changed());
        assertEquals(parse(source), walked.tree());
    }

    @Test
    void stripsEnchantmentKeys() {
        Walked walked = walk(pool(book("{'minecraft:mending':1,'minecraft:unbreaking':1}")),
                QUERY);
        assertTrue(walked.changed());
        assertEquals(List.of("t:x <- minecraft:mending"), walked.lines());
        assertEquals(parse(pool(book("{'minecraft:unbreaking':1}"))), walked.tree());
    }

    @Test
    void removesEmptiedEnchantmentFunction() {
        Walked walked = walk(pool(book("{'mending':1}")), QUERY);
        assertTrue(walked.changed());
        assertEquals(List.of("t:x <- minecraft:mending"), walked.lines());
        assertEquals(parse(pool("{'type':'minecraft:item','name':'minecraft:book',"
                + "'functions':[]}")), walked.tree());
    }

    @Test
    void removesEmptyInSourceEnchantmentFunction() {
        Walked walked = walk(pool(book("{}")), QUERY);
        assertTrue(walked.changed());
        assertEquals(List.of(), walked.lines());
        assertEquals(parse(pool("{'type':'minecraft:item','name':'minecraft:book',"
                + "'functions':[]}")), walked.tree());
    }

    @Test
    void keepsEnchantmentFunctionWithoutMap() {
        String source = pool("{'type':'minecraft:item','name':'minecraft:book','functions':"
                + "[{'function':'minecraft:set_enchantments','add':true}]}");
        Walked walked = walk(source, QUERY);
        assertFalse(walked.changed());
        assertEquals(parse(source), walked.tree());
    }

    @Test
    void placeholderAfterRemoval() {
        Walked walked = walk(pool(OAK), QUERY);
        assertTrue(walked.changed());
        assertEquals(parse(pool(EMPTY)), walked.tree());
    }

    @Test
    void placeholderInChildren() {
        Walked walked = walk(pool("{'type':'minecraft:alternatives','children':[" + OAK + "]}"),
                QUERY);
        assertTrue(walked.changed());
        assertEquals(List.of("t:x <- minecraft:oak_planks"), walked.lines());
        assertEquals(parse(pool("{'type':'minecraft:alternatives','children':[" + EMPTY + "]}")),
                walked.tree());
    }

    @Test
    void noPlaceholderForEmptySource() {
        String source = "{'pools':[{'rolls':1,'entries':[]},"
                + "{'rolls':1,'entries':[{'type':'minecraft:alternatives','children':[]}]}]}";
        Walked walked = walk(source, QUERY);
        assertFalse(walked.changed());
        assertEquals(parse(source), walked.tree());
    }

    @Test
    void walksNestedTableAndContents() {
        Walked walked = walk(pool("{'type':'minecraft:loot_table','value':" + pool(OAK) + "}",
                chest(OAK)), QUERY);
        assertTrue(walked.changed());
        assertEquals(List.of("t:x <- minecraft:oak_planks", "t:x <- minecraft:oak_planks"),
                walked.lines());
        assertEquals(parse(pool("{'type':'minecraft:loot_table','value':" + pool(EMPTY) + "}",
                chest(EMPTY))), walked.tree());
    }

    @Test
    void walksNestedComposites() {
        Walked walked = walk(pool("{'type':'minecraft:group','children':["
                + "{'type':'minecraft:sequence','children':["
                + "{'type':'minecraft:alternatives','children':[" + OAK + "," + STONE + "]}]}]}"),
                QUERY);
        assertTrue(walked.changed());
        assertEquals(List.of("t:x <- minecraft:oak_planks"), walked.lines());
        assertEquals(parse(pool("{'type':'minecraft:group','children':["
                + "{'type':'minecraft:sequence','children':["
                + "{'type':'minecraft:alternatives','children':[" + STONE + "]}]}]}")),
                walked.tree());
    }

    @Test
    void leavesUnknownSchema() {
        String strength = "{'function':'minecraft:set_potion','id':'minecraft:strength'}";
        String source = pool("{'type':'minecraft:item','name':'minecraft:stone','functions':["
                        + "{'function':'minecraft:set_components','components':"
                        + "{'minecraft:container':[],'entries':[" + OAK + "]}},"
                        + "{'function':'minecraft:filtered','item_filter':{},"
                        + "'modifier':" + strength + "},"
                        + "[" + strength + "]]}",
                "{'type':'mod:thing','children':[" + OAK + "]}");
        Walked walked = walk(source, QUERY);
        assertFalse(walked.changed());
        assertEquals(List.of(), walked.lines());
        assertEquals(parse(source), walked.tree());
    }

    @Test
    void ignoresMalformedFields() {
        for (String source : List.of(
                "{'pools':{'entries':[" + OAK + "]}}",
                "{'pools':[1,'a',[" + OAK + "]]}",
                "{'pools':[{'entries':'minecraft:oak_planks'}]}",
                "{'pools':[{'entries':[{'type':'minecraft:item','name':'minecraft:stone',"
                        + "'functions':3}]}],'functions':{}}",
                "[" + OAK + "]",
                "'minecraft:oak_planks'")) {
            JsonElement tree = parse(source);
            assertFalse(LootJsonFilter.filter(tree, TABLE, QUERY, Recorder.NONE), source);
            assertEquals(parse(source), tree, source);
        }
        assertFalse(LootJsonFilter.filter(JsonNull.INSTANCE, TABLE, QUERY, Recorder.NONE));
    }

    @Test
    void idempotent() {
        JsonElement tree = parse(pool(OAK, STONE));
        assertTrue(LootJsonFilter.filter(tree, TABLE, QUERY, Recorder.NONE));
        JsonElement once = tree.deepCopy();
        assertFalse(LootJsonFilter.filter(tree, TABLE, QUERY, Recorder.NONE));
        assertEquals(once, tree);
    }

    @Test
    void emptyQueryChangesNothing() {
        // Every construct of the rules, except a set_enchantments map empty in the source,
        // which goes with any query (removesEmptyInSourceEnchantmentFunction).
        String source = "{'pools':[{'rolls':1,'entries':["
                + OAK + ","
                + "{'type':'minecraft:tag','name':'minecraft:planks','expand':true},"
                + potionEntry("minecraft:strength") + ","
                + book("{'minecraft:mending':1}") + ","
                + "{'type':'minecraft:alternatives','children':[" + OAK + "]},"
                + "{'type':'minecraft:loot_table','value':" + pool(OAK) + "},"
                + chest(OAK)
                + "],'functions':[{'function':'minecraft:sequence','functions':["
                + "{'function':'minecraft:set_potion','id':'minecraft:strength'}]}]}],"
                + "'functions':[{'function':'minecraft:set_potion','id':'minecraft:strength'}]}";
        Walked walked = walk(source, NOTHING);
        assertFalse(walked.changed());
        assertEquals(List.of(), walked.lines());
        assertEquals(parse(source), walked.tree());
    }

    @Test
    void alternativesNextChildWins() {
        Walked walked = walk(
                pool("{'type':'minecraft:alternatives','children':[" + OAK + "," + STONE + "]}"),
                QUERY);
        assertTrue(walked.changed());
        assertEquals(List.of("t:x <- minecraft:oak_planks"), walked.lines());
        assertEquals(parse(pool("{'type':'minecraft:alternatives','children':[" + STONE + "]}")),
                walked.tree());
    }

    @Test
    void recordsOnlyLootRemovals() {
        Walked walked = walk(pool(OAK, potionEntry("minecraft:strength"),
                book("{'minecraft:binding_curse':1,'minecraft:unbreaking':1}")), QUERY);
        assertEquals(List.of("t:x <- minecraft:binding_curse", "t:x <- minecraft:strength",
                "t:x <- minecraft:oak_planks"), walked.lines());
    }

    @Test
    void tableIdOfFile() {
        assertEquals("minecraft:blocks/stone",
                LootJsonFilter.tableIdOfFile("minecraft:loot_table/blocks/stone.json"));
        assertEquals("item_blacklist_gametest:x",
                LootJsonFilter.tableIdOfFile("item_blacklist_gametest:loot_table/x.json"));
        for (String other : List.of("minecraft:recipe/x.json", "minecraft:predicate/x.json",
                "minecraft:item_modifier/x.json",
                "item_blacklist_gametest:loot_modifiers/add_items.json",
                "neoforge:loot_modifiers/global_loot_modifiers.json",
                "minecraft:loot_table/x.txt", "minecraft:loot_tablex/y.json",
                "minecraft:loot_table/.json", "loot_table/x.json", ":loot_table/x.json")) {
            assertNull(LootJsonFilter.tableIdOfFile(other), other);
        }
        assertNull(LootJsonFilter.tableIdOfFile(null));
    }

    /** One pool with these entries. */
    private static String pool(String... entries) {
        return "{'pools':[{'rolls':1,'entries':[" + String.join(",", entries) + "]}]}";
    }

    /** A potion entry whose own functions set this potion. */
    private static String potionEntry(String potion) {
        return "{'type':'minecraft:item','name':'minecraft:potion','functions':"
                + "[{'function':'minecraft:set_potion','id':'" + potion + "'}]}";
    }

    /** A book entry with set_enchantments of this map. */
    private static String book(String levels) {
        return "{'type':'minecraft:item','name':'minecraft:book','functions':"
                + "[{'function':'minecraft:set_enchantments','enchantments':" + levels + "}]}";
    }

    /** A chest entry whose set_contents holds these entries. */
    private static String chest(String... entries) {
        return "{'type':'minecraft:item','name':'minecraft:chest','functions':"
                + "[{'function':'minecraft:set_contents','component':'minecraft:container',"
                + "'entries':[" + String.join(",", entries) + "]}]}";
    }

    private static JsonElement parse(String source) {
        return JsonParser.parseString(source.replace('\'', '"'));
    }

    private static Walked walk(String source, IdQuery query) {
        JsonElement tree = parse(source);
        Lines recorder = new Lines();
        boolean changed = LootJsonFilter.filter(tree, TABLE, query, recorder);
        return new Walked(tree, changed, recorder.lines);
    }

    /** A walked tree, the walk's result and its records. */
    private record Walked(JsonElement tree, boolean changed, List<String> lines) {
    }

    /** An IdQuery over four sets of "namespace:path" (tags without '#'). */
    private record Query(Set<String> items, Set<String> tags, Set<String> potions,
            Set<String> enchantments) implements IdQuery {
        @Override
        public boolean itemId(String id) {
            return items.contains(id);
        }

        @Override
        public boolean itemTagId(String id) {
            return tags.contains(id);
        }

        @Override
        public boolean potionId(String id) {
            return potions.contains(id);
        }

        @Override
        public boolean enchantmentId(String id) {
            return enchantments.contains(id);
        }
    }

    /** Keeps "table <- cause" of lootRemoval and "other <kind>" for every other call. */
    private static final class Lines implements Recorder {
        private final List<String> lines = new ArrayList<>();

        @Override
        public void lootRemoval(String table, String cause) {
            lines.add(table + " <- " + cause);
        }

        @Override
        public void blacklistedItem(String item) {
            lines.add("other item");
        }

        @Override
        public void blacklistedTag(String registry, String tag) {
            lines.add("other tag");
        }

        @Override
        public void blacklistedPotion(String potion) {
            lines.add("other potion");
        }

        @Override
        public void blacklistedEnchantment(String enchantment) {
            lines.add("other enchantment");
        }

        @Override
        public void tagRemoval(String registry, String tag, String entry) {
            lines.add("other tag entry");
        }

        @Override
        public void recipeRemoval(String recipe, String cause) {
            lines.add("other recipe");
        }

        @Override
        public void brewingRemoval(String mix, String cause) {
            lines.add("other brewing");
        }

        @Override
        public void loaderRemoval(String table, String entry) {
            lines.add("other loader");
        }

        @Override
        public void tradeRefusal(String merchant, String cause) {
            lines.add("other trade");
        }
    }
}
