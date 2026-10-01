package net.sievert.item_blacklist.gametest;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.Lookups;
import net.sievert.item_blacklist.blacklist.TagFilter;
import net.sievert.item_blacklist.line.Keys;
import net.sievert.item_blacklist.platform.Capability;
import net.sievert.item_blacklist.platform.Services;
import net.sievert.item_blacklist.report.RemovalReport;
import net.sievert.item_blacklist.report.ReportSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/**
 * The registry-tags scenarios (prefix tags_): what the tag loads bind on the running server,
 * and TagFilter's contract on built maps and snapshots. Each is synchronous and holds before
 * and after a reload, since every tag load re-derives what it reads.
 */
public final class TagScenarios {
    private static final String NS = ItemBlacklistGameTests.NAMESPACE;

    private TagScenarios() {
    }

    /** The tags_ scenarios; called by ItemBlacklistGameTests.register. */
    public static void register(BiConsumer<String, Consumer<GameTestHelper>> sink) {
        sink.accept("tags_item_tag_cleared", TagScenarios::itemTagCleared);
        sink.accept("tags_items_stripped", TagScenarios::itemsStripped);
        sink.accept("tags_blocks_stripped", TagScenarios::blocksStripped);
        sink.accept("tags_enchantments_filtered", TagScenarios::enchantmentsFiltered);
        sink.accept("tags_others_untouched", TagScenarios::othersUntouched);
        sink.accept("tags_filter_keeps_keys", TagScenarios::filterKeepsKeys);
        sink.accept("tags_filter_registries", TagScenarios::filterRegistries);
        sink.accept("tags_emptied_recorded", TagScenarios::emptiedRecorded);
        sink.accept("tags_potions_filtered", TagScenarios::potionsFiltered);
        sink.accept("tags_recorded", TagScenarios::recorded);
    }

    /** A configured tag keeps its key and is bound to nothing. */
    static void itemTagCleared(GameTestHelper helper) {
        HolderLookup.Provider provider = provider(helper);
        TagKey<Item> planks = vanillaTag(Registries.ITEM, "planks");
        Check.isTrue(helper, Lookups.tagKnown(provider, Registries.ITEM, planks),
                "#minecraft:planks is still defined");
        Check.equal(helper, Lookups.members(provider, Registries.ITEM, planks).size(), 0,
                "the size of #minecraft:planks");
        helper.succeed();
    }

    /** Explicit and tag-derived items leave every item tag; other items keep theirs. */
    static void itemsStripped(GameTestHelper helper) {
        Holder<Item> oakPlanks = TestGame.holder(helper, Registries.ITEM, "oak_planks");
        Check.equal(helper, oakPlanks.tags().map(tag -> Keys.name(tag)).toList(), List.of(),
                "the item tags of minecraft:oak_planks");
        Holder<Item> birchPlanks = TestGame.holder(helper, Registries.ITEM, "birch_planks");
        Check.equal(helper, birchPlanks.tags().map(tag -> Keys.name(tag)).toList(), List.of(),
                "the item tags of minecraft:birch_planks (a member of #planks only)");
        Holder<Item> oakLog = TestGame.holder(helper, Registries.ITEM, "oak_log");
        Check.isTrue(helper, oakLog.is(vanillaTag(Registries.ITEM, "logs")),
                "minecraft:oak_log is in #minecraft:logs");
        helper.succeed();
    }

    /**
     * A block goes from the block tags when its item is blacklisted, and a block tag emptied
     * by the strip keeps its key.
     */
    static void blocksStripped(GameTestHelper helper) {
        HolderLookup.Provider provider = provider(helper);
        TagKey<Block> axe = vanillaTag(Registries.BLOCK, "mineable/axe");
        Holder<Block> oakPlanks = TestGame.holder(helper, Registries.BLOCK, "oak_planks");
        Check.isTrue(helper, !oakPlanks.is(axe),
                "the oak planks block is not in #minecraft:mineable/axe");
        Holder<Block> oakLog = TestGame.holder(helper, Registries.BLOCK, "oak_log");
        Check.isTrue(helper, oakLog.is(axe), "the oak log block is in #minecraft:mineable/axe");
        TagKey<Block> planks = vanillaTag(Registries.BLOCK, "planks");
        Check.isTrue(helper, Lookups.tagKnown(provider, Registries.BLOCK, planks),
                "the block tag #minecraft:planks is still defined");
        Check.equal(helper, Lookups.members(provider, Registries.BLOCK, planks).size(), 0,
                "the size of the block tag #minecraft:planks");
        helper.succeed();
    }

    /**
     * The enchantment strip on the server's own registry: a configured tag bound empty, an
     * explicit and a tag-derived enchantment gone from #tradeable, a control kept.
     */
    static void enchantmentsFiltered(GameTestHelper helper) {
        HolderLookup.Provider provider = provider(helper);
        TagKey<Enchantment> curse = vanillaTag(Registries.ENCHANTMENT, "curse");
        Check.isTrue(helper, Lookups.tagKnown(provider, Registries.ENCHANTMENT, curse),
                "#minecraft:curse is still defined");
        Check.equal(helper, Lookups.members(provider, Registries.ENCHANTMENT, curse).size(), 0,
                "the size of #minecraft:curse");
        TagKey<Enchantment> tradeable = vanillaTag(Registries.ENCHANTMENT, "tradeable");
        Holder<Enchantment> mending = TestGame.holder(helper, Registries.ENCHANTMENT, "mending");
        Check.isTrue(helper, !mending.is(tradeable),
                "minecraft:mending is not in #minecraft:tradeable");
        Holder<Enchantment> bindingCurse =
                TestGame.holder(helper, Registries.ENCHANTMENT, "binding_curse");
        Check.isTrue(helper, !bindingCurse.is(tradeable),
                "minecraft:binding_curse (a member of #curse) is not in #minecraft:tradeable");
        Holder<Enchantment> unbreaking =
                TestGame.holder(helper, Registries.ENCHANTMENT, "unbreaking");
        Check.isTrue(helper, unbreaking.is(tradeable),
                "minecraft:unbreaking is in #minecraft:tradeable");
        helper.succeed();
    }

    /** Another registry's tags pass through (the weak control; the strong one is FLUID below). */
    static void othersUntouched(GameTestHelper helper) {
        Holder<Fluid> water = TestGame.holder(helper, Registries.FLUID, "water");
        Check.isTrue(helper, water.is(vanillaTag(Registries.FLUID, "water")),
                "minecraft:water is in the fluid tag #minecraft:water");
        helper.succeed();
    }

    /**
     * The ITEM half of TagFilter's contract on a built map: every key kept, members read from
     * the unfiltered input (sugar leaves "derived" because "named" holds it), an emptied tag
     * bound empty and recorded with its members, a tag already empty not "emptied", the input
     * map and snapshot unchanged, and the records.
     */
    static void filterKeepsKeys(GameTestHelper helper) {
        ResourceKey<Item> stone = TestGame.key(Registries.ITEM, "stone");
        ResourceKey<Item> sugar = TestGame.key(Registries.ITEM, "sugar");
        Holder<Item> stoneH = TestGame.holder(helper, Registries.ITEM, "stone");
        Holder<Item> coalH = TestGame.holder(helper, Registries.ITEM, "coal");
        Holder<Item> sugarH = TestGame.holder(helper, Registries.ITEM, "sugar");
        TagKey<Item> named = testTag(Registries.ITEM, "named");
        TagKey<Item> mixed = testTag(Registries.ITEM, "mixed");
        TagKey<Item> onlyStone = testTag(Registries.ITEM, "only_stone");
        TagKey<Item> derived = testTag(Registries.ITEM, "derived");
        TagKey<Item> empty = testTag(Registries.ITEM, "empty");
        Map<TagKey<Item>, List<Holder<Item>>> incoming = Map.of(
                named, List.of(sugarH, stoneH),
                mixed, List.of(stoneH, coalH),
                onlyStone, List.of(stoneH),
                derived, List.of(coalH, sugarH),
                empty, List.of());
        BlacklistSnapshot snapshot =
                TestGame.snapshot(b -> b.item(stone).itemTag(named, List.of()));
        RemovalReport recorder = new RemovalReport();

        TagFilter.Result<Item> result =
                TagFilter.apply(snapshot, Registries.ITEM, incoming, recorder);

        Check.equal(helper, result.tags().keySet(), incoming.keySet(),
                "the tag keys after the filter");
        Check.equal(helper, result.tags().get(named), List.of(), "the named tag");
        Check.equal(helper, result.tags().get(mixed), List.of(coalH),
                "the tag holding stone and coal");
        Check.equal(helper, result.tags().get(onlyStone), List.of(), "the tag holding only stone");
        Check.equal(helper, result.tags().get(derived), List.of(coalH),
                "the tag holding coal and sugar");
        Check.equal(helper, result.tags().get(empty), List.of(), "the tag that was empty");
        BlacklistSnapshot next = result.snapshot();
        Check.equal(helper, next.itemTagMembers().get(named), Set.of(sugar, stone),
                "the members of the named tag");
        Check.isTrue(helper, next.item(sugar), "sugar is blacklisted through the named tag");
        Check.equal(helper, next.emptiedItemTags().keySet(), Set.of(onlyStone),
                "the emptied tags");
        Check.equal(helper, next.emptiedItemTags().get(onlyStone), Set.of(stone),
                "the members of the emptied tag");
        Check.isTrue(helper, next.itemTagEmptied(onlyStone) && next.itemTagEmptied(named)
                && !next.itemTagEmptied(empty) && !next.itemTagEmptied(mixed), "itemTagEmptied");
        Check.isTrue(helper, !snapshot.item(sugar), "the input snapshot is unchanged");
        Check.equal(helper, incoming.get(mixed), List.of(stoneH, coalH),
                "the incoming list of the mixed tag");
        Check.equal(helper, recorder.flush(false).entries(), Map.of(
                ReportSnapshot.Kind.TAG, Map.of("#" + NS + ":named", Set.of("item")),
                ReportSnapshot.Kind.TAG_ENTRY, Map.of(
                        "minecraft:sugar", Set.of("item #" + NS + ":named",
                                "item #" + NS + ":derived"),
                        "minecraft:stone", Set.of("item #" + NS + ":named",
                                "item #" + NS + ":mixed", "item #" + NS + ":only_stone"))),
                "the records");
        helper.succeed();
    }

    /**
     * The BLOCK, ENCHANTMENT and POTION branches and the pass-through of any other registry and
     * of the empty snapshot, on built maps: the only run of the POTION branch on 1.21.x, which
     * has no vanilla potion tag.
     */
    static void filterRegistries(GameTestHelper helper) {
        BlacklistSnapshot snapshot = TestGame.snapshot(b -> b
                .item(TestGame.key(Registries.ITEM, "stone"))
                .enchantment(TestGame.key(Registries.ENCHANTMENT, "mending"))
                .enchantmentTag(testTag(Registries.ENCHANTMENT, "named"), List.of())
                .potion(TestGame.key(Registries.POTION, "strength")));
        RemovalReport recorder = new RemovalReport();

        // BLOCK: stone's item is blacklisted; fire's item is air
        Holder<Block> stoneB = TestGame.holder(helper, Registries.BLOCK, "stone");
        Holder<Block> dirtB = TestGame.holder(helper, Registries.BLOCK, "dirt");
        Holder<Block> fireB = TestGame.holder(helper, Registries.BLOCK, "fire");
        TagKey<Block> blocks = testTag(Registries.BLOCK, "blocks");
        TagKey<Block> allStone = testTag(Registries.BLOCK, "all_stone");
        Map<TagKey<Block>, List<Holder<Block>>> blockIn = Map.of(
                blocks, List.of(stoneB, dirtB, fireB), allStone, List.of(stoneB));
        TagFilter.Result<Block> blockOut =
                TagFilter.apply(snapshot, Registries.BLOCK, blockIn, recorder);
        Check.equal(helper, blockOut.tags().keySet(), blockIn.keySet(), "the block tag keys");
        Check.equal(helper, blockOut.tags().get(blocks), List.of(dirtB, fireB), "the block tag");
        Check.equal(helper, blockOut.tags().get(allStone), List.of(),
                "the block tag holding only stone");
        Check.isTrue(helper, blockOut.snapshot() == snapshot, "the BLOCK pass keeps the snapshot");

        // ENCHANTMENT
        ResourceKey<Enchantment> bindingKey = TestGame.key(Registries.ENCHANTMENT, "binding_curse");
        Holder<Enchantment> mending = TestGame.holder(helper, Registries.ENCHANTMENT, "mending");
        Holder<Enchantment> unbreaking =
                TestGame.holder(helper, Registries.ENCHANTMENT, "unbreaking");
        Holder<Enchantment> binding =
                TestGame.holder(helper, Registries.ENCHANTMENT, "binding_curse");
        TagKey<Enchantment> named = testTag(Registries.ENCHANTMENT, "named");
        TagKey<Enchantment> mixed = testTag(Registries.ENCHANTMENT, "mixed");
        TagKey<Enchantment> onlyMending = testTag(Registries.ENCHANTMENT, "only_mending");
        TagKey<Enchantment> derived = testTag(Registries.ENCHANTMENT, "derived");
        Map<TagKey<Enchantment>, List<Holder<Enchantment>>> enchIn = Map.of(
                named, List.of(binding), mixed, List.of(mending, unbreaking),
                onlyMending, List.of(mending), derived, List.of(binding, unbreaking));
        TagFilter.Result<Enchantment> enchOut =
                TagFilter.apply(snapshot, Registries.ENCHANTMENT, enchIn, recorder);
        Check.equal(helper, enchOut.tags().keySet(), enchIn.keySet(), "the enchantment tag keys");
        Check.equal(helper, enchOut.tags().get(named), List.of(), "the named enchantment tag");
        Check.equal(helper, enchOut.tags().get(mixed), List.of(unbreaking),
                "the enchantment tag holding mending and unbreaking");
        Check.equal(helper, enchOut.tags().get(onlyMending), List.of(),
                "the enchantment tag holding only mending");
        Check.equal(helper, enchOut.tags().get(derived), List.of(unbreaking),
                "the enchantment tag holding binding_curse and unbreaking");
        Check.equal(helper, enchOut.snapshot().enchantmentTagMembers().get(named),
                Set.of(bindingKey), "the members of the named enchantment tag");
        Check.isTrue(helper, enchOut.snapshot().enchantment(bindingKey),
                "binding_curse is blacklisted through the named tag");
        Check.equal(helper, enchOut.snapshot().emptiedEnchantmentTags(), Set.of(onlyMending),
                "the emptied enchantment tags");

        // POTION
        Holder<Potion> strength = TestGame.holder(helper, Registries.POTION, "strength");
        Holder<Potion> leaping = TestGame.holder(helper, Registries.POTION, "leaping");
        TagKey<Potion> potions = testTag(Registries.POTION, "potions");
        TagKey<Potion> onlyStrength = testTag(Registries.POTION, "only_strength");
        Map<TagKey<Potion>, List<Holder<Potion>>> potionIn = Map.of(
                potions, List.of(strength, leaping), onlyStrength, List.of(strength));
        TagFilter.Result<Potion> potionOut =
                TagFilter.apply(snapshot, Registries.POTION, potionIn, recorder);
        Check.equal(helper, potionOut.tags().get(potions), List.of(leaping), "the potion tag");
        Check.equal(helper, potionOut.tags().get(onlyStrength), List.of(),
                "the potion tag holding only strength");
        Check.isTrue(helper, potionOut.snapshot() == snapshot,
                "the POTION pass keeps the snapshot");

        // any other registry, and the empty snapshot: the input itself
        Map<TagKey<Fluid>, List<Holder<Fluid>>> fluidIn = Map.of(
                testTag(Registries.FLUID, "fluids"),
                List.of(TestGame.holder(helper, Registries.FLUID, "water")));
        Check.isTrue(helper,
                TagFilter.apply(snapshot, Registries.FLUID, fluidIn, recorder).tags() == fluidIn,
                "a fluid tag load is returned as it came");
        Map<TagKey<Block>, List<Holder<Block>>> emptyOut =
                TagFilter.apply(BlacklistSnapshot.EMPTY, Registries.BLOCK, blockIn, recorder)
                        .tags();
        Check.isTrue(helper, emptyOut == blockIn, "an empty snapshot returns the input");

        Check.equal(helper, recorder.flush(false).entries(), Map.of(
                ReportSnapshot.Kind.TAG, Map.of("#" + NS + ":named", Set.of("enchantment")),
                ReportSnapshot.Kind.TAG_ENTRY, Map.of(
                        "minecraft:stone", Set.of("block #" + NS + ":blocks",
                                "block #" + NS + ":all_stone"),
                        "minecraft:binding_curse", Set.of("enchantment #" + NS + ":named",
                                "enchantment #" + NS + ":derived"),
                        "minecraft:mending", Set.of("enchantment #" + NS + ":mixed",
                                "enchantment #" + NS + ":only_mending"),
                        "minecraft:strength", Set.of("potion #" + NS + ":potions",
                                "potion #" + NS + ":only_strength"))),
                "the records");
        helper.succeed();
    }

    /**
     * The live snapshot carries what the tag loads derived: configured tag members, an undefined
     * configured tag with no member, and a tag emptied without being named, which recipes'
     * scenarios build on.
     */
    static void emptiedRecorded(GameTestHelper helper) {
        HolderLookup.Provider provider = provider(helper);
        BlacklistSnapshot live = TestGame.live();
        TagKey<Item> planks = vanillaTag(Registries.ITEM, "planks");
        Check.isTrue(helper, live.itemTagMembers().get(planks).containsAll(Set.of(
                        TestGame.key(Registries.ITEM, "oak_planks"),
                        TestGame.key(Registries.ITEM, "birch_planks"))),
                "#minecraft:planks's members hold oak and birch planks");
        TagKey<Item> modTag = Keys.tag(Registries.ITEM, "mod_id", "mod_tag");
        Check.equal(helper, live.itemTagMembers().get(modTag), Set.of(),
                "the members of the undefined #mod_id:mod_tag");
        TagKey<Item> soul = vanillaTag(Registries.ITEM, "soul_fire_base_blocks");
        Check.isTrue(helper, live.itemTagEmptied(soul),
                "#minecraft:soul_fire_base_blocks is emptied");
        Check.isTrue(helper, live.emptiedItemTags().get(soul).containsAll(Set.of(
                        TestGame.key(Registries.ITEM, "soul_sand"),
                        TestGame.key(Registries.ITEM, "soul_soil"))),
                "the recorded members of #minecraft:soul_fire_base_blocks");
        Check.isTrue(helper, Lookups.tagKnown(provider, Registries.ITEM, soul),
                "#minecraft:soul_fire_base_blocks is still defined");
        Check.equal(helper, Lookups.members(provider, Registries.ITEM, soul).size(), 0,
                "the size of #minecraft:soul_fire_base_blocks");
        Check.isTrue(helper, !live.itemTagEmptied(vanillaTag(Registries.ITEM, "logs")),
                "#minecraft:logs is not emptied");
        TagKey<Enchantment> curse = vanillaTag(Registries.ENCHANTMENT, "curse");
        Check.isTrue(helper, live.enchantmentTagMembers().get(curse).containsAll(Set.of(
                        TestGame.key(Registries.ENCHANTMENT, "binding_curse"),
                        TestGame.key(Registries.ENCHANTMENT, "vanishing_curse"))),
                "#minecraft:curse's members hold both curses");
        Check.isTrue(helper, live.enchantmentTagEmptied(curse),
                "#minecraft:curse counts as emptied");
        helper.succeed();
    }

    /**
     * 26.x: blacklisted potions leave every potion tag; 1.21.x has no vanilla potion tag. The
     * capability is checked against the game's own data.
     */
    static void potionsFiltered(GameTestHelper helper) {
        HolderLookup.Provider provider = provider(helper);
        TagKey<Potion> tradeable = vanillaTag(Registries.POTION, "tradeable");
        boolean expected = Services.PLATFORM.supports(Capability.POTION_TAGS);
        Check.equal(helper, Lookups.tagKnown(provider, Registries.POTION, tradeable), expected,
                "the potion tag #minecraft:tradeable is defined");
        if (expected) {
            Holder<Potion> strength = TestGame.holder(helper, Registries.POTION, "strength");
            Check.isTrue(helper, !strength.is(tradeable),
                    "minecraft:strength is not in #minecraft:tradeable");
            Holder<Potion> leaping = TestGame.holder(helper, Registries.POTION, "leaping");
            Check.isTrue(helper, leaping.is(tradeable),
                    "minecraft:leaping is in #minecraft:tradeable");
        }
        helper.succeed();
    }

    /** The tag records reach the server's report through onTagLoad on every reload. */
    static void recorded(GameTestHelper helper) {
        ReportSnapshot flush = Blacklist.serverState().report().lastFlush();
        Check.isTrue(helper, flush.number() >= 1, "a report was flushed");
        Check.isTrue(helper, flush.count(ReportSnapshot.Kind.TAG) >= 2,
                "the last report holds the cleared #minecraft:planks and #minecraft:curse");
        Check.isTrue(helper, flush.count(ReportSnapshot.Kind.TAG_ENTRY) > 0,
                "the last report holds tag entries");
        helper.succeed();
    }

    /** The running server's registries as a lookup provider. */
    private static HolderLookup.Provider provider(GameTestHelper helper) {
        return TestGame.server(helper).registryAccess();
    }

    /** A tag of the test mod's namespace, for built maps; no data file defines it. */
    private static <T> TagKey<T> testTag(ResourceKey<? extends Registry<T>> registry,
            String path) {
        return Keys.tag(registry, ItemBlacklistGameTests.NAMESPACE, path);
    }

    /** "minecraft:<path>" as a tag key. */
    private static <T> TagKey<T> vanillaTag(ResourceKey<? extends Registry<T>> registry,
            String path) {
        return Keys.tag(registry, "minecraft", path);
    }
}
