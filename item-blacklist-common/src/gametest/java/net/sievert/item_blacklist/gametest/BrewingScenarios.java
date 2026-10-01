package net.sievert.item_blacklist.gametest;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.ServerState;
import net.sievert.item_blacklist.brewing.BrewingFilter;
import net.sievert.item_blacklist.line.Ingredients;
import net.sievert.item_blacklist.line.Keys;
import net.sievert.item_blacklist.mixin.PotionBrewingAccessor;
import net.sievert.item_blacklist.mixin.PotionBrewingMixAccessor;
import net.sievert.item_blacklist.report.Recorder;
import net.sievert.item_blacklist.report.ReportSnapshot;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

/**
 * The brewing scenarios (prefix brew_): the vanilla potion and container mixes of the running
 * server, NeoForge's own brewing recipes through a loader fixture, the removal rule on built
 * snapshots, the refilter from kept originals on an object of its own, and the Ingredients
 * facade. Each is synchronous: set up, act, assert and succeed inside its call; each holds
 * before and after a reload. Ingredients are built through {@code ingredient}, so the call is
 * Ingredient.of(ItemLike...) on every release: the one-argument overload exists only from
 * 1.21.2. Potions are looked up by key, never through Potions.X, whose type changes on 26.2.
 */
public final class BrewingScenarios {
    private BrewingScenarios() {
    }

    /**
     * The brew_ scenarios, and the brewing clause of reload_keeps_filters; called by
     * ItemBlacklistGameTests.register, before ReloadScenarios.register.
     */
    public static void register(BiConsumer<String, Consumer<GameTestHelper>> sink) {
        sink.accept("brew_container_mix_removed", BrewingScenarios::containerMixRemoved);
        sink.accept("brew_facades_link", BrewingScenarios::facadesLink);
        sink.accept("brew_loader_recipe_removed", BrewingScenarios::loaderRecipeRemoved);
        sink.accept("brew_potion_mix_removed", BrewingScenarios::potionMixRemoved);
        sink.accept("brew_reagent_mix", BrewingScenarios::reagentMix);
        sink.accept("brew_refilter_from_originals", BrewingScenarios::refilterFromOriginals);
        sink.accept("brew_rule", BrewingScenarios::rule);
        ReloadScenarios.afterReload("brewing", helper -> Check.isTrue(helper,
                !helper.getLevel().potionBrewing().isBrewablePotion(potion(helper, "strength")),
                "Expected strength to stay unbrewable after a reload"));
    }

    /**
     * Potion mixes go by output and by input: awkward + blaze powder gives strength, which is
     * blacklisted, and long and strong strength come only from strength. The control mix
     * awkward + sugar (swiftness) stays, and the removal is in the last report.
     */
    static void potionMixRemoved(GameTestHelper helper) {
        PotionBrewing brewing = helper.getLevel().potionBrewing();
        ItemStack awkward = TestGame.potionStack(helper, Items.POTION, "awkward");
        Check.isTrue(helper, !brewing.hasMix(awkward, stack(Items.BLAZE_POWDER)),
                "Expected no mix for awkward + blaze powder: strength is blacklisted");
        for (String path : List.of("strength", "long_strength", "strong_strength")) {
            Check.isTrue(helper, !brewing.isBrewablePotion(potion(helper, path)),
                    "Expected " + path + " to be unbrewable");
        }
        Check.isTrue(helper, brewing.hasMix(awkward, stack(Items.SUGAR)),
                "Expected awkward + sugar to still mix");
        Check.isTrue(helper, brewing.isBrewablePotion(potion(helper, "swiftness")),
                "Expected swiftness to stay brewable");
        Check.isTrue(helper, mentions(lastFlush(helper), ReportSnapshot.Kind.BREWING,
                "input=minecraft:awkward, ingredient=[minecraft:blaze_powder],"
                        + " output=minecraft:strength"),
                "Expected the last report to hold the strength mix");
        helper.succeed();
    }

    /**
     * Container mixes go by output item: splash water + dragon breath gives a lingering potion,
     * which is blacklisted. The control potion + gunpowder (splash) stays, and the removal is in
     * the last report.
     */
    static void containerMixRemoved(GameTestHelper helper) {
        PotionBrewing brewing = helper.getLevel().potionBrewing();
        ItemStack splashWater = TestGame.potionStack(helper, Items.SPLASH_POTION, "water");
        Check.isTrue(helper, !brewing.hasMix(splashWater, stack(Items.DRAGON_BREATH)),
                "Expected no mix for splash water + dragon breath:"
                        + " lingering potions are blacklisted");
        Check.isTrue(helper, !brewing.isContainerIngredient(stack(Items.DRAGON_BREATH)),
                "Expected dragon breath to be no container ingredient");
        Check.isTrue(helper, brewing.hasMix(TestGame.potionStack(helper, Items.POTION, "water"),
                stack(Items.GUNPOWDER)), "Expected water + gunpowder to still mix");
        Check.isTrue(helper, brewing.isContainerIngredient(stack(Items.GUNPOWDER)),
                "Expected gunpowder to stay a container ingredient");
        Check.isTrue(helper, mentions(lastFlush(helper), ReportSnapshot.Kind.BREWING,
                "input=minecraft:splash_potion, ingredient=[minecraft:dragon_breath],"
                        + " output=minecraft:lingering_potion"),
                "Expected the last report to hold the lingering mix");
        helper.succeed();
    }

    /**
     * A mix whose every reagent alternative is blacklisted is removed, not only made inert:
     * leaping's only mix is awkward + rabbit foot, slow falling's awkward + phantom membrane.
     * The reagent rule also removes water + rabbit foot (mundane), so rabbit foot is no potion
     * ingredient at all. Holds without recipes' Ingredient.test guard.
     */
    static void reagentMix(GameTestHelper helper) {
        PotionBrewing brewing = helper.getLevel().potionBrewing();
        ItemStack awkward = TestGame.potionStack(helper, Items.POTION, "awkward");
        Check.isTrue(helper, !brewing.hasMix(awkward, stack(Items.RABBIT_FOOT)),
                "Expected no mix for awkward + rabbit foot");
        Check.isTrue(helper, !brewing.isPotionIngredient(stack(Items.RABBIT_FOOT)),
                "Expected rabbit foot to be no potion ingredient");
        Check.isTrue(helper, !brewing.isBrewablePotion(potion(helper, "leaping")),
                "Expected leaping to be unbrewable: its only mix uses a blacklisted reagent");
        Check.isTrue(helper, !brewing.isBrewablePotion(potion(helper, "slow_falling")),
                "Expected slow falling to be unbrewable: phantom membrane is blacklisted");
        Check.isTrue(helper, mentions(lastFlush(helper), ReportSnapshot.Kind.BREWING,
                "input=minecraft:awkward, ingredient=[minecraft:rabbit_foot],"
                        + " output=minecraft:leaping"),
                "Expected the last report to hold the leaping mix");
        helper.succeed();
    }

    /**
     * NeoForge's own brewing recipes (the loader fixture: stone + stick gives a lingering
     * potion, stone + rabbit foot iron, stone + sugar coal): the first two are removed, by
     * output and by reagent, the third stays, so the registry was written back and not
     * emptied. Where no fixture is installed (Fabric) stone is no brewing input and every
     * hasMix is false: the scenario passes on its control.
     */
    static void loaderRecipeRemoved(GameTestHelper helper) {
        PotionBrewing brewing = helper.getLevel().potionBrewing();
        ItemStack stone = stack(Items.STONE);
        boolean fixture = Fixtures.loaderBrewingRecipe;
        Check.isTrue(helper, !brewing.hasMix(stone, stack(Items.STICK)),
                "Expected no mix for stone + stick: its output, a lingering potion,"
                        + " is blacklisted");
        Check.isTrue(helper, !brewing.hasMix(stone, stack(Items.RABBIT_FOOT)),
                "Expected no mix for stone + rabbit foot");
        Check.equal(helper, brewing.hasMix(stone, stack(Items.SUGAR)), fixture,
                "whether stone + sugar mixes (the kept fixture recipe)");
        if (fixture) {
            ReportSnapshot report = lastFlush(helper);
            Check.isTrue(helper, mentions(report, ReportSnapshot.Kind.LOADER, "neoforge:brewing",
                    "output=minecraft:lingering_potion"),
                    "Expected the output-blacklisted recipe in the report");
            Check.isTrue(helper, mentions(report, ReportSnapshot.Kind.LOADER, "neoforge:brewing",
                    "ingredient=[minecraft:rabbit_foot]"),
                    "Expected the reagent-blacklisted recipe in the report");
            Check.isTrue(helper, !mentions(report, ReportSnapshot.Kind.LOADER, "neoforge:brewing",
                    "output=minecraft:coal"),
                    "Expected the clean recipe to stay out of the report");
        }
        helper.succeed();
    }

    /**
     * The rule on built snapshots the fixture cannot hold: nothing with an empty blacklist;
     * the first reason in the order output, input, reagent; a reagent with one allowed
     * alternative keeps its mix; container mixes by item; a cause's configured tag follows its
     * item.
     */
    static void rule(GameTestHelper helper) {
        Holder<Potion> awkward = potion(helper, "awkward");
        Holder<Potion> swiftness = potion(helper, "swiftness");
        Holder<Potion> longSwiftness = potion(helper, "long_swiftness");
        Holder<Item> potionItem = TestGame.holder(helper, Registries.ITEM, "potion");
        Holder<Item> splash = TestGame.holder(helper, Registries.ITEM, "splash_potion");
        Holder<Item> lingering = TestGame.holder(helper, Registries.ITEM, "lingering_potion");
        Ingredient sugar = ingredient(Items.SUGAR);

        Check.equal(helper, BrewingFilter.potionMixCauses(BlacklistSnapshot.EMPTY, awkward,
                sugar, swiftness), List.<String>of(), "causes with an empty blacklist");
        BlacklistSnapshot swift = TestGame.snapshot(
                b -> b.potion(TestGame.key(Registries.POTION, "swiftness")));
        Check.equal(helper, BrewingFilter.potionMixCauses(swift, awkward, sugar, swiftness),
                List.of("minecraft:swiftness"), "causes of a blacklisted output");
        Check.equal(helper, BrewingFilter.potionMixCauses(swift, swiftness,
                ingredient(Items.REDSTONE), longSwiftness),
                List.of("minecraft:swiftness"), "causes of a blacklisted input");
        BlacklistSnapshot sugarOut = TestGame.snapshot(
                b -> b.item(TestGame.key(Registries.ITEM, "sugar")));
        Check.equal(helper, BrewingFilter.potionMixCauses(sugarOut, awkward, sugar, swiftness),
                List.of("minecraft:sugar"), "causes of a blacklisted reagent");
        Check.equal(helper, BrewingFilter.potionMixCauses(sugarOut, awkward,
                ingredient(Items.SUGAR, Items.STONE), swiftness),
                List.<String>of(), "causes of a reagent with one allowed alternative");
        BlacklistSnapshot splashOut = TestGame.snapshot(
                b -> b.item(TestGame.key(Registries.ITEM, "splash_potion")));
        Check.equal(helper, BrewingFilter.containerMixCauses(splashOut, potionItem,
                ingredient(Items.GUNPOWDER), splash),
                List.of("minecraft:splash_potion"), "causes of a blacklisted container output");
        Check.equal(helper, BrewingFilter.containerMixCauses(splashOut, splash,
                ingredient(Items.DRAGON_BREATH), lingering),
                List.of("minecraft:splash_potion"), "causes of a blacklisted container input");
        // The item is named explicitly too, so the expectation does not depend on whether
        // Builder.itemTag also blacklists its members.
        BlacklistSnapshot planks = TestGame.snapshot(b -> b
                .item(TestGame.key(Registries.ITEM, "oak_planks"))
                .itemTag(Keys.tag(Registries.ITEM, "minecraft", "planks"),
                        List.of(TestGame.key(Registries.ITEM, "oak_planks"))));
        Check.equal(helper, BrewingFilter.reagentCauses(planks, ingredient(Items.OAK_PLANKS)),
                List.of("minecraft:oak_planks", "#minecraft:planks"),
                "causes with tag attribution");
        helper.succeed();
    }

    /**
     * Filtering works from the lists an object had when first seen, on a fresh PotionBrewing
     * the server never uses (the per-object path a client's object takes): a second call with
     * the same snapshot changes nothing, a smaller blacklist gives a mix back, an empty one
     * restores every list. NeoForge marks the one-argument bootstrap deprecated, not for
     * removal; it is vanilla's only form.
     */
    @SuppressWarnings("deprecation")
    static void refilterFromOriginals(GameTestHelper helper) {
        PotionBrewing fresh = PotionBrewing.bootstrap(helper.getLevel().enabledFeatures());
        PotionBrewingAccessor lists = (PotionBrewingAccessor) fresh;
        int potionMixes = lists.item_blacklist$potionMixes().size();
        int containerMixes = lists.item_blacklist$containerMixes().size();
        ItemStack awkward = TestGame.potionStack(helper, Items.POTION, "awkward");
        Holder<Potion> leaping = potion(helper, "leaping");
        Check.isTrue(helper, fresh.hasMix(awkward, stack(Items.BLAZE_POWDER)),
                "Expected an unfiltered object to mix awkward + blaze powder");

        BrewingFilter.apply(fresh, TestGame.live(), Recorder.NONE);
        Check.isTrue(helper, !fresh.hasMix(awkward, stack(Items.BLAZE_POWDER)),
                "Expected the live blacklist to remove awkward + blaze powder");
        List<?> once = List.copyOf(lists.item_blacklist$potionMixes());
        BrewingFilter.apply(fresh, TestGame.live(), Recorder.NONE);
        Check.isTrue(helper, sameElements(once, lists.item_blacklist$potionMixes()),
                "Expected a second call with the same snapshot to change nothing");

        BrewingFilter.apply(fresh, TestGame.snapshot(
                b -> b.potion(TestGame.key(Registries.POTION, "leaping"))), Recorder.NONE);
        Check.isTrue(helper, fresh.hasMix(awkward, stack(Items.BLAZE_POWDER)),
                "Expected a smaller blacklist to give awkward + blaze powder back");
        Check.isTrue(helper, !fresh.isBrewablePotion(leaping),
                "Expected leaping, now blacklisted, to be unbrewable");

        BrewingFilter.apply(fresh, BlacklistSnapshot.EMPTY, Recorder.NONE);
        Check.equal(helper, lists.item_blacklist$potionMixes().size(), potionMixes,
                "potion mixes after an empty blacklist");
        Check.equal(helper, lists.item_blacklist$containerMixes().size(), containerMixes,
                "container mixes after an empty blacklist");
        Check.isTrue(helper, fresh.isBrewablePotion(leaping), "Expected leaping brewable again");
        helper.succeed();
    }

    /**
     * Every member of the Ingredients facade runs on this release (so every Fabric 1.21.x boot
     * executes the backend it picked), both accessors read, and every mix the server kept is
     * clean under the live blacklist, so the setters took effect. NeoForgeIngredients is a
     * NeoForge-part class no common scenario may name: NeoForgeBrewing runs it at every server
     * starting and reload of a NeoForge GameTest run, and brew_loader_recipe_removed asserts
     * its results.
     */
    @SuppressWarnings("unchecked")
    static void facadesLink(GameTestHelper helper) {
        List<Item> two = Ingredients.alternatives(ingredient(Items.SUGAR, Items.STONE));
        Check.equal(helper, Set.copyOf(two), Set.of(Items.SUGAR, Items.STONE),
                "alternatives of sugar or stone");
        Check.equal(helper, two.size(), 2, "number of alternatives of sugar or stone");
        PotionBrewingAccessor lists = (PotionBrewingAccessor) helper.getLevel().potionBrewing();
        BlacklistSnapshot live = TestGame.live();
        Check.isTrue(helper, !lists.item_blacklist$potionMixes().isEmpty(),
                "Expected potion mixes");
        for (Object element : lists.item_blacklist$potionMixes()) {
            PotionBrewingMixAccessor mix = (PotionBrewingMixAccessor) element;
            Check.isTrue(helper, !Ingredients.alternatives(mix.item_blacklist$ingredient())
                    .isEmpty(), "Expected alternatives for " + BrewingFilter.describe(element));
            Check.equal(helper, BrewingFilter.potionMixCauses(live,
                    (Holder<Potion>) mix.item_blacklist$from(), mix.item_blacklist$ingredient(),
                    (Holder<Potion>) mix.item_blacklist$to()), List.<String>of(),
                    "causes of the kept mix " + BrewingFilter.describe(element));
        }
        for (Object element : lists.item_blacklist$containerMixes()) {
            PotionBrewingMixAccessor mix = (PotionBrewingMixAccessor) element;
            Check.equal(helper, BrewingFilter.containerMixCauses(live,
                    (Holder<Item>) mix.item_blacklist$from(), mix.item_blacklist$ingredient(),
                    (Holder<Item>) mix.item_blacklist$to()), List.<String>of(),
                    "causes of the kept mix " + BrewingFilter.describe(element));
        }
        helper.succeed();
    }

    /** Ingredient.of(ItemLike...) on every release; see the class comment. */
    private static Ingredient ingredient(ItemLike... items) {
        return Ingredient.of(items);
    }

    /** One item as a stack of one. */
    private static ItemStack stack(Item item) {
        return new ItemStack(item);
    }

    /** The potion "minecraft:<path>" from the level's registries. */
    private static Holder.Reference<Potion> potion(GameTestHelper helper, String path) {
        return TestGame.holder(helper, Registries.POTION, path);
    }

    /** The server state's last flushed report; fails the scenario without a state. */
    private static ReportSnapshot lastFlush(GameTestHelper helper) {
        ServerState state = Blacklist.serverState();
        if (state == null) {
            Check.fail(helper, "Expected a server state");
            throw new IllegalStateException("unreachable");
        }
        return state.report().lastFlush();
    }

    /**
     * Whether a recorded pair of the kind holds every needle in "subject | cause", so the check
     * does not depend on which of the Recorder call's arguments the report keeps as subject.
     */
    private static boolean mentions(ReportSnapshot report, ReportSnapshot.Kind kind,
            String... needles) {
        SortedMap<String, SortedSet<String>> pairs =
                report.entries().getOrDefault(kind, Collections.emptySortedMap());
        for (Map.Entry<String, SortedSet<String>> entry : pairs.entrySet()) {
            Set<String> causes = entry.getValue().isEmpty() ? Set.of("") : entry.getValue();
            for (String cause : causes) {
                String pair = entry.getKey() + " | " + cause;
                boolean all = true;
                for (String needle : needles) {
                    all &= pair.contains(needle);
                }
                if (all) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The same elements by identity, in order. */
    private static boolean sameElements(List<?> a, List<?> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i) != b.get(i)) {
                return false;
            }
        }
        return true;
    }
}
