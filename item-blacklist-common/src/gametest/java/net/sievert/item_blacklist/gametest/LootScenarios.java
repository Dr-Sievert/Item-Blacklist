package net.sievert.item_blacklist.gametest;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.Lookups;
import net.sievert.item_blacklist.blacklist.ServerState;
import net.sievert.item_blacklist.blacklist.StackRules;
import net.sievert.item_blacklist.loot.LootJsonFilter;
import net.sievert.item_blacklist.report.Recorder;
import net.sievert.item_blacklist.report.ReportSnapshot;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;

/**
 * The loot scenarios (prefix loot_): the JSON walk as each window's hook applies it to the
 * test tables in data/item_blacklist_gametest/loot_table, with this release's Gson on a built
 * snapshot, the report's records, the roll filter, and the loaders' additions. Each is
 * synchronous: set up, act, assert and succeed inside its call. Each holds before and after a
 * reload: every reload walks the tables again and flushes a new report.
 */
public final class LootScenarios {
    private static final String NS = ItemBlacklistGameTests.NAMESPACE;
    /** A table for the built walk: J1 with bare ids, J3, J5. */
    private static final String RULE_TABLE = """
            {"pools": [{"rolls": 1, "entries": [
              {"type": "minecraft:item", "name": "minecraft:stone"},
              {"type": "item", "name": "dirt"},
              {"type": "minecraft:item", "name": "minecraft:potion",
               "functions": [{"function": "minecraft:set_potion", "id": "minecraft:leaping"}]},
              {"type": "minecraft:item", "name": "minecraft:book",
               "functions": [{"function": "minecraft:set_enchantments",
                              "enchantments": {"minecraft:unbreaking": 1,
                                               "minecraft:mending": 1}}]}
            ]}]}
            """;

    private LootScenarios() {
    }

    /** The loot_ scenarios; called by ItemBlacklistGameTests.register. */
    public static void register(BiConsumer<String, Consumer<GameTestHelper>> sink) {
        sink.accept("loot_additions_filtered", LootScenarios::additionsFiltered);
        sink.accept("loot_json_conditions_first", LootScenarios::jsonConditionsFirst);
        sink.accept("loot_json_enchantments", LootScenarios::jsonEnchantments);
        sink.accept("loot_json_item_entry", LootScenarios::jsonItemEntry);
        sink.accept("loot_json_placeholder", LootScenarios::jsonPlaceholder);
        sink.accept("loot_json_potion", LootScenarios::jsonPotion);
        sink.accept("loot_json_rule_built", LootScenarios::jsonRuleBuilt);
        sink.accept("loot_json_tag_entry", LootScenarios::jsonTagEntry);
        sink.accept("loot_report_records", LootScenarios::reportRecords);
        sink.accept("loot_roll_filtered", LootScenarios::rollFiltered);
    }

    /**
     * An item entry named directly goes, and one written with a bare type and a bare id whose
     * item is blacklisted only through #planks; the control stays, so the table loaded.
     */
    static void jsonItemEntry(GameTestHelper helper) {
        JsonElement json = table(helper, "loot_json_item_entry");
        occurs(helper, json, "loot_json_item_entry", "minecraft:oak_planks", 0);
        occurs(helper, json, "loot_json_item_entry", "minecraft:birch_planks", 0);
        occurs(helper, json, "loot_json_item_entry", "minecraft:stone", 1);
        helper.succeed();
    }

    /** A tag entry of a tag the config names goes; one of another tag stays. */
    static void jsonTagEntry(GameTestHelper helper) {
        JsonElement json = table(helper, "loot_json_tag_entry");
        occurs(helper, json, "loot_json_tag_entry", "minecraft:planks", 0);
        occurs(helper, json, "loot_json_tag_entry", "minecraft:logs", 1);
        occurs(helper, json, "loot_json_tag_entry", "minecraft:stone", 1);
        helper.succeed();
    }

    /**
     * An entry whose own set_potion names a blacklisted potion goes as a whole; a pool-level
     * set_potion of a blacklisted potion goes alone, and its entry stays.
     */
    static void jsonPotion(GameTestHelper helper) {
        JsonElement json = table(helper, "loot_json_potion");
        occurs(helper, json, "loot_json_potion", "minecraft:strength", 0);
        occurs(helper, json, "loot_json_potion", "minecraft:strong_strength", 0);
        occurs(helper, json, "loot_json_potion", "minecraft:leaping", 1);
        occurs(helper, json, "loot_json_potion", "minecraft:potion", 1);
        occurs(helper, json, "loot_json_potion", "minecraft:splash_potion", 1);
        helper.succeed();
    }

    /**
     * Blacklisted keys leave a set_enchantments map (mending explicitly, binding_curse through
     * #curse); a map left empty takes its function with it, and both books stay.
     */
    static void jsonEnchantments(GameTestHelper helper) {
        JsonElement json = table(helper, "loot_json_enchantments");
        occurs(helper, json, "loot_json_enchantments", "minecraft:mending", 0);
        occurs(helper, json, "loot_json_enchantments", "minecraft:binding_curse", 0);
        occurs(helper, json, "loot_json_enchantments", "minecraft:unbreaking", 1);
        occurs(helper, json, "loot_json_enchantments", "minecraft:set_enchantments", 1);
        occurs(helper, json, "loot_json_enchantments", "minecraft:book", 2);
        helper.succeed();
    }

    /**
     * A pool and a composite that a removal emptied get one empty entry each; a pool empty in
     * the source stays empty. The table's own type is minecraft:chest, so every
     * "minecraft:empty" is an entry type.
     */
    static void jsonPlaceholder(GameTestHelper helper) {
        JsonElement json = table(helper, "loot_json_placeholder");
        occurs(helper, json, "loot_json_placeholder", "minecraft:empty", 2);
        occurs(helper, json, "loot_json_placeholder", "minecraft:oak_planks", 0);
        occurs(helper, json, "loot_json_placeholder", "minecraft:alternatives", 1);
        occurs(helper, json, "loot_json_placeholder", "minecraft:stone", 1);
        helper.succeed();
    }

    /**
     * A table that its load conditions drop on both loaders leaves no loot record: on Fabric
     * its conditions run before the walk, on NeoForge the walk's records wait for the load.
     */
    static void jsonConditionsFirst(GameTestHelper helper) {
        String table = NS + ":loot_json_conditions_first";
        occurs(helper, table(helper, "loot_json_conditions_first"), "loot_json_conditions_first",
                "minecraft:charcoal", 0);
        Check.isTrue(helper, Blacklist.serverState() != null, "Expected a server state");
        Check.isTrue(helper, !lootRecords().containsKey(table),
                "Expected no loot record for " + table + ", which its load conditions drop");
        helper.succeed();
    }

    /**
     * The walk and BlacklistSnapshot as IdQuery with this release's Gson, which the unit tests
     * (one line only) cannot show: bare ids, a potion entry, an enchantment key, the return
     * value, a second walk changing nothing, the empty snapshot changing nothing.
     */
    static void jsonRuleBuilt(GameTestHelper helper) {
        BlacklistSnapshot built = TestGame.snapshot(builder -> builder
                .item(TestGame.key(Registries.ITEM, "stone"))
                .potion(TestGame.key(Registries.POTION, "leaping"))
                .enchantment(TestGame.key(Registries.ENCHANTMENT, "unbreaking")));
        String id = NS + ":loot_json_rule_built";
        JsonElement tree = JsonParser.parseString(RULE_TABLE);
        Check.isTrue(helper, LootJsonFilter.filter(tree, id, built, Recorder.NONE),
                "Expected the walk to report a change");
        occurs(helper, tree, "the built tree", "minecraft:stone", 0);
        occurs(helper, tree, "the built tree", "dirt", 1);
        occurs(helper, tree, "the built tree", "minecraft:potion", 0);
        occurs(helper, tree, "the built tree", "minecraft:leaping", 0);
        occurs(helper, tree, "the built tree", "minecraft:unbreaking", 0);
        occurs(helper, tree, "the built tree", "minecraft:mending", 1);
        Check.isTrue(helper, !LootJsonFilter.filter(tree, id, built, Recorder.NONE),
                "Expected a second walk to change nothing");
        JsonElement untouched = JsonParser.parseString(RULE_TABLE);
        Check.isTrue(helper,
                !LootJsonFilter.filter(untouched, id, BlacklistSnapshot.EMPTY, Recorder.NONE),
                "Expected the empty snapshot to change nothing");
        Check.equal(helper, untouched, JsonParser.parseString(RULE_TABLE),
                "the tree after the empty snapshot's walk");
        helper.succeed();
    }

    /**
     * The table ids the hooks derive (the file id's text from 1.21.2, the table id at 1.21.1)
     * reach the latest report, for test tables and a vanilla one; a built walk records nothing.
     */
    static void reportRecords(GameTestHelper helper) {
        Check.isTrue(helper, Blacklist.serverState() != null, "Expected a server state");
        SortedMap<String, SortedSet<String>> loot = lootRecords();
        cause(helper, loot, NS + ":loot_json_item_entry", "minecraft:oak_planks");
        cause(helper, loot, NS + ":loot_json_tag_entry", "#minecraft:planks");
        cause(helper, loot, NS + ":loot_json_potion", "minecraft:strength");
        cause(helper, loot, NS + ":loot_json_enchantments", "minecraft:mending");
        cause(helper, loot, "minecraft:blocks/oak_planks", "minecraft:oak_planks");
        Check.isTrue(helper, !loot.containsKey(NS + ":loot_json_rule_built"),
                "Expected no loot record of a built walk");
        helper.succeed();
    }

    /**
     * The roll filter refuses a stack the walk cannot see: set_item turns cobblestone into oak
     * planks during the roll, and only the control comes out; the table itself is unchanged.
     */
    static void rollFiltered(GameTestHelper helper) {
        BlacklistSnapshot live = TestGame.live();
        List<ItemStack> drops = TestGame.lootSpawn(helper, NS + ":loot_roll_filtered");
        for (ItemStack stack : drops) {
            Check.isTrue(helper, !StackRules.blacklisted(live, stack),
                    "Expected no blacklisted stack rolled: "
                            + Lookups.itemName(stack.getItem(), "?"));
        }
        Check.equal(helper, drops.size(), 1, "stacks rolled from loot_roll_filtered");
        Check.equal(helper, Lookups.itemName(drops.get(0).getItem(), "?"), "minecraft:stone",
                "the one stack rolled");
        JsonElement json = table(helper, "loot_roll_filtered");
        occurs(helper, json, "loot_roll_filtered", "minecraft:oak_planks", 1);
        occurs(helper, json, "loot_roll_filtered", "minecraft:cobblestone", 1);
        helper.succeed();
    }

    /**
     * The loaders' bypasses are closed: NeoForge's global loot modifier (all releases) and
     * Fabric API's MODIFY_DROPS listener (from its 1.21.6 builds) add oak planks and stone to
     * the test table; no planks come out, stone does where the test mod installed the fixture
     * (Fixtures.lootAdditions). Which fixture the Fabric test mod picks shows in its backend
     * line, LootFixture.
     */
    static void additionsFiltered(GameTestHelper helper) {
        BlacklistSnapshot live = TestGame.live();
        List<ItemStack> drops = TestGame.lootSpawn(helper, NS + ":loot_additions_filtered");
        int dirt = 0;
        int stone = 0;
        for (ItemStack stack : drops) {
            String id = Lookups.itemName(stack.getItem(), "?");
            Check.isTrue(helper, !StackRules.blacklisted(live, stack),
                    "Expected no blacklisted stack after the loader's additions: " + id);
            if (id.equals("minecraft:dirt")) {
                dirt++;
            }
            if (id.equals("minecraft:stone")) {
                stone++;
            }
        }
        Check.equal(helper, dirt, 1, "dirt from the table itself");
        Check.equal(helper, stone, Fixtures.lootAdditions ? 1 : 0,
                "stone from the loader's additions");
        helper.succeed();
    }

    /** The loaded test table NS:name, encoded; the empty table's encoding for an absent one. */
    private static JsonElement table(GameTestHelper helper, String name) {
        return TestGame.lootJson(helper, NS + ":" + name);
    }

    /** How often text occurs in tree as a string value or an object key (enchantment ids). */
    private static int count(JsonElement tree, String text) {
        if (tree == null || tree.isJsonNull()) {
            return 0;
        }
        if (tree.isJsonPrimitive()) {
            return tree.getAsJsonPrimitive().isString() && text.equals(tree.getAsString())
                    ? 1 : 0;
        }
        int found = 0;
        if (tree.isJsonArray()) {
            for (JsonElement element : tree.getAsJsonArray()) {
                found += count(element, text);
            }
        } else {
            for (Map.Entry<String, JsonElement> entry : tree.getAsJsonObject().entrySet()) {
                if (text.equals(entry.getKey())) {
                    found++;
                }
                found += count(entry.getValue(), text);
            }
        }
        return found;
    }

    /** Fails unless text occurs exactly expected times in the named table's encoding. */
    private static void occurs(GameTestHelper helper, JsonElement tree, String table,
            String text, int expected) {
        Check.equal(helper, count(tree, text), expected,
                "occurrences of \"" + text + "\" in " + table);
    }

    /** The LOOT records of the latest flush, table to causes; empty without a server state. */
    private static SortedMap<String, SortedSet<String>> lootRecords() {
        ServerState state = Blacklist.serverState();
        if (state == null) {
            return Collections.emptySortedMap();
        }
        SortedMap<String, SortedSet<String>> loot =
                state.report().lastFlush().entries().get(ReportSnapshot.Kind.LOOT);
        return loot == null ? Collections.emptySortedMap() : loot;
    }

    /** Fails unless the records hold cause for table. */
    private static void cause(GameTestHelper helper, SortedMap<String, SortedSet<String>> loot,
            String table, String cause) {
        SortedSet<String> causes = loot.get(table);
        Check.isTrue(helper, causes != null && causes.contains(cause),
                "Expected the loot record " + table + " <- " + cause + " in " + loot.keySet());
    }
}
