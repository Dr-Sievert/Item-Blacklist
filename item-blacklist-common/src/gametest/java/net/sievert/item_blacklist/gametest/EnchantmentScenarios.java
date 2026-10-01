package net.sievert.item_blacklist.gametest;

import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.Components;
import net.sievert.item_blacklist.blacklist.Lookups;
import net.sievert.item_blacklist.enchantments.EnchantmentRules;
import net.sievert.item_blacklist.line.Keys;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.providers.SingleEnchantment;
import net.minecraft.world.level.GameType;

/**
 * The enchantments scenarios (prefix ench_): the rules on built snapshots, then each of the
 * four hooks through the game's own entry point, with the fixture's mending and #curse
 * blacklisted and unbreaking as the control. Each is synchronous: set up, act, assert and
 * succeed inside its call; each holds before and after a reload, since it reads the live
 * snapshot or builds its own.
 */
public final class EnchantmentScenarios {
    /** Seeds 0 to 199 for the enchanting scenarios: enough for an unfiltered hook to show. */
    private static final int SEEDS = 200;
    /**
     * The enchanting cost of the table scenarios. A book (enchantability 1) then gets a cost of
     * 26 to 36, which holds mending (25 to 75), both curses (25 to 50) and unbreaking III (21
     * to 71), so every seed has blacklisted candidates and a clean one.
     */
    private static final int COST = 30;

    private EnchantmentScenarios() {
    }

    /** The ench_ scenarios; called by ItemBlacklistGameTests.register. */
    public static void register(BiConsumer<String, Consumer<GameTestHelper>> sink) {
        sink.accept("ench_rules", EnchantmentScenarios::rules);
        sink.accept("ench_table_filtered", EnchantmentScenarios::tableFiltered);
        sink.accept("ench_with_levels_filtered", EnchantmentScenarios::withLevelsFiltered);
        sink.accept("ench_empty_book_kept", EnchantmentScenarios::emptyBookKept);
        sink.accept("ench_randomly_filtered", EnchantmentScenarios::randomlyFiltered);
        sink.accept("ench_randomly_empty", EnchantmentScenarios::randomlyEmpty);
        sink.accept("ench_single_skipped", EnchantmentScenarios::singleSkipped);
        sink.accept("ench_anvil_merge_skips", EnchantmentScenarios::anvilMergeSkips);
        sink.accept("ench_anvil_cost_charged", EnchantmentScenarios::anvilCostCharged);
    }

    /**
     * The rules on a built snapshot: explicit and tag-derived keys refused, the control allowed,
     * EMPTY allowing everything, the same instance back when nothing goes (the fast path the
     * hooks rely on with an empty blacklist), and the plain-book rule.
     */
    static void rules(GameTestHelper helper) {
        Holder<Enchantment> mending = holder(helper, "mending");
        Holder<Enchantment> unbreaking = holder(helper, "unbreaking");
        Holder<Enchantment> binding = holder(helper, "binding_curse");
        BlacklistSnapshot built = TestGame.snapshot(b -> b
                .enchantment(key("mending"))
                .enchantmentTag(Keys.tag(Registries.ENCHANTMENT, "minecraft", "curse"),
                        List.of(key("binding_curse"))));

        Check.isTrue(helper, !EnchantmentRules.allowed(built, mending),
                "allowed: explicit mending was allowed");
        Check.isTrue(helper, !EnchantmentRules.allowed(built, binding),
                "allowed: the tag member binding_curse was allowed");
        Check.isTrue(helper, EnchantmentRules.allowed(built, unbreaking),
                "allowed: unbreaking was refused");
        Check.isTrue(helper, EnchantmentRules.allowed(BlacklistSnapshot.EMPTY, mending),
                "allowed: EMPTY refused mending");

        List<Holder<Enchantment>> mixed = List.of(mending, unbreaking, binding);
        List<Holder<Enchantment>> clean = List.of(unbreaking);
        Check.equal(helper, EnchantmentRules.allowedList(built, mixed), clean,
                "allowedList of mending, unbreaking, binding_curse");
        Check.isTrue(helper, EnchantmentRules.allowedList(built, clean) == clean,
                "allowedList: a clean list was copied");
        Check.isTrue(helper, EnchantmentRules.allowedList(BlacklistSnapshot.EMPTY, mixed) == mixed,
                "allowedList: a list was copied under EMPTY");
        Check.equal(helper, EnchantmentRules.allowedStream(built, mixed.stream()).toList(), clean,
                "allowedStream of mending, unbreaking, binding_curse");
        Stream<Holder<Enchantment>> stream = mixed.stream();
        Check.isTrue(helper, EnchantmentRules.allowedStream(BlacklistSnapshot.EMPTY, stream)
                == stream, "allowedStream: a stream was wrapped under EMPTY");

        ItemStack book = new ItemStack(Items.BOOK);
        ItemStack emptyEnchanted = new ItemStack(Items.ENCHANTED_BOOK);
        ItemStack stored = TestGame.book(helper, "unbreaking");
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        Check.isTrue(helper, EnchantmentRules.keepPlainBook(built, book, emptyEnchanted) == book,
                "keepPlainBook: an empty enchanted book was not replaced by the book");
        Check.isTrue(helper, EnchantmentRules.keepPlainBook(built, book, stored) == stored,
                "keepPlainBook: an enchanted book with unbreaking was replaced");
        Check.isTrue(helper, EnchantmentRules.keepPlainBook(BlacklistSnapshot.EMPTY, book,
                emptyEnchanted) == emptyEnchanted, "keepPlainBook: active under EMPTY");
        Check.isTrue(helper, EnchantmentRules.keepPlainBook(built, sword, sword) == sword,
                "keepPlainBook: a sword was replaced");
        helper.succeed();
    }

    /**
     * The enchanting table's path, selectEnchantment through enchantItem, over every holder of
     * the registry, seeds 0 to 199: nothing blacklisted comes out, and something does. The full
     * registry, since the table's own tag holds no treasure and no curse, and the tag strip
     * empties the rest of it anyway; only the full stream proves the hook.
     */
    static void tableFiltered(GameTestHelper helper) {
        BlacklistSnapshot live = TestGame.live();
        requireFixture(helper, live);
        MinecraftServer server = TestGame.server(helper);
        int enchanted = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            ItemStack result = EnchantmentHelper.enchantItem(RandomSource.create(seed),
                    new ItemStack(Items.BOOK), COST, allEnchantments(server));
            enchanted += checkClean(helper, live, result, "table seed " + seed);
        }
        Check.isTrue(helper, enchanted > 0,
                "table: no seed of " + SEEDS + " gave an enchanted book");
        helper.succeed();
    }

    /**
     * enchant_with_levels' own call without options, which draws from the whole registry (so do
     * 1.21.x villager trades and mob equipment), seeds 0 to 199.
     */
    static void withLevelsFiltered(GameTestHelper helper) {
        BlacklistSnapshot live = TestGame.live();
        requireFixture(helper, live);
        MinecraftServer server = TestGame.server(helper);
        int enchanted = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            ItemStack result = EnchantmentHelper.enchantItem(RandomSource.create(seed),
                    new ItemStack(Items.BOOK), COST, server.registryAccess(), Optional.empty());
            enchanted += checkClean(helper, live, result, "with_levels seed " + seed);
        }
        Check.isTrue(helper, enchanted > 0,
                "with_levels: no seed of " + SEEDS + " gave an enchanted book");
        helper.succeed();
    }

    /**
     * A book whose every candidate is blacklisted comes back as the same plain book, where
     * vanilla would give an enchanted book without an enchantment; a clean candidate still
     * enchants it.
     */
    static void emptyBookKept(GameTestHelper helper) {
        requireFixture(helper, TestGame.live());
        ItemStack book = new ItemStack(Items.BOOK);
        ItemStack result = EnchantmentHelper.enchantItem(RandomSource.create(0), book, COST,
                Stream.<Holder<Enchantment>>of(holder(helper, "mending"),
                        holder(helper, "binding_curse")));
        Check.isTrue(helper, result == book, "empty book: the input book was not returned");
        Check.isTrue(helper, result.is(Items.BOOK) && enchantmentKeys(result).isEmpty(),
                "empty book: the result is not a plain book");

        ItemStack control = EnchantmentHelper.enchantItem(RandomSource.create(0),
                new ItemStack(Items.BOOK), COST,
                Stream.<Holder<Enchantment>>of(holder(helper, "unbreaking")));
        Check.isTrue(helper, control.is(Items.ENCHANTED_BOOK),
                "control: unbreaking did not enchant the book");
        Check.equal(helper, enchantmentKeys(control), Set.of(key("unbreaking")),
                "the control's stored enchantments");
        helper.succeed();
    }

    /**
     * A test table rolls 50 books through enchant_randomly with the options mending and
     * unbreaking: 50 books with unbreaking. With the hook broken about half would carry mending
     * and fail the key check, or, with the loot roll filter in place, be dropped and fail the
     * count; so the scenario holds with or without the loot subsystem's hooks.
     */
    static void randomlyFiltered(GameTestHelper helper) {
        requireFixture(helper, TestGame.live());
        List<ItemStack> drops = TestGame.lootSpawn(helper,
                ItemBlacklistGameTests.NAMESPACE + ":ench_randomly_filtered");
        Check.equal(helper, drops.size(), 50, "the number of books rolled");
        for (ItemStack drop : drops) {
            Check.isTrue(helper, drop.is(Items.ENCHANTED_BOOK), "a rolled book is not enchanted");
            Check.equal(helper, enchantmentKeys(drop), Set.of(key("unbreaking")),
                    "a rolled book's stored enchantments");
        }
        helper.succeed();
    }

    /**
     * enchant_randomly with mending as its only option: the emptied list takes vanilla's own
     * path, a plain book (and vanilla's warning), without an exception.
     */
    static void randomlyEmpty(GameTestHelper helper) {
        requireFixture(helper, TestGame.live());
        List<ItemStack> drops = TestGame.lootSpawn(helper,
                ItemBlacklistGameTests.NAMESPACE + ":ench_randomly_empty");
        Check.equal(helper, drops.size(), 1, "the number of books rolled");
        ItemStack drop = drops.get(0);
        Check.isTrue(helper, drop.is(Items.BOOK) && enchantmentKeys(drop).isEmpty(),
                "the rolled book is not a plain book");
        helper.succeed();
    }

    /**
     * A single provider of mending, and of binding_curse through #curse, adds nothing; one of
     * unbreaking still does. The difficulty is built by hand: the provider does not read it,
     * and the level's own lookup moves at 1.21.11.
     */
    static void singleSkipped(GameTestHelper helper) {
        requireFixture(helper, TestGame.live());
        DifficultyInstance difficulty = new DifficultyInstance(Difficulty.NORMAL, 0L, 0L, 0.0F);
        for (String path : List.of("mending", "binding_curse")) {
            ItemEnchantments.Mutable enchantments =
                    new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            new SingleEnchantment(holder(helper, path), ConstantInt.of(1)).enchant(
                    new ItemStack(Items.DIAMOND_SWORD), enchantments, RandomSource.create(0),
                    difficulty);
            Check.isTrue(helper, enchantments.keySet().isEmpty(),
                    "the single provider applied minecraft:" + path);
        }
        ItemEnchantments.Mutable control = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        new SingleEnchantment(holder(helper, "unbreaking"), ConstantInt.of(1)).enchant(
                new ItemStack(Items.DIAMOND_SWORD), control, RandomSource.create(0), difficulty);
        Check.equal(helper, Lookups.keys(control.keySet()), Set.of(key("unbreaking")),
                "the control provider's enchantments");
        helper.succeed();
    }

    /**
     * An anvil with a sword and a book of mending and unbreaking gives a sword with unbreaking
     * only: on vanilla and Fabric through createResult, on NeoForge from 1.21.5 through
     * createResultInternal.
     */
    static void anvilMergeSkips(GameTestHelper helper) {
        requireFixture(helper, TestGame.live());
        AnvilMenu menu = anvil(helper, TestGame.book(helper, "mending", "unbreaking"));
        ItemStack result = menu.getSlot(menu.getResultSlot()).getItem();
        Check.isTrue(helper, result.is(Items.DIAMOND_SWORD), "anvil: no sword in the result slot");
        Check.equal(helper, enchantmentKeys(result), Set.of(key("unbreaking")),
                "the anvil result's enchantments");
        helper.succeed();
    }

    /**
     * A book of mending only: the anvil still charges and gives the sword without mending. The
     * wrap skips only the set; the cost is added as before, as in the old mod. This scenario
     * flips when that is ever changed.
     */
    static void anvilCostCharged(GameTestHelper helper) {
        requireFixture(helper, TestGame.live());
        AnvilMenu menu = anvil(helper, TestGame.book(helper, "mending"));
        ItemStack result = menu.getSlot(menu.getResultSlot()).getItem();
        Check.isTrue(helper, menu.getCost() > 0, "anvil: the cost is 0 for a mending-only book");
        Check.isTrue(helper, result.is(Items.DIAMOND_SWORD), "anvil: no sword in the result slot");
        Check.isTrue(helper, enchantmentKeys(result).isEmpty(),
                "anvil: the sword gained an enchantment");
        helper.succeed();
    }

    /**
     * An anvil no player opened, with a diamond sword left and the book right, its result made.
     * The level access is NULL because createResult never uses it; the mock player is survival,
     * so the merge's creative branch stays off, and it is no ServerPlayer, so no other hook of
     * the mod acts on it. Setting a slot already makes the result through slotsChanged; the
     * explicit call keeps the scenario independent of that listener.
     */
    private static AnvilMenu anvil(GameTestHelper helper, ItemStack book) {
        AnvilMenu menu = new AnvilMenu(0, helper.makeMockPlayer(GameType.SURVIVAL).getInventory(),
                ContainerLevelAccess.NULL);
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD));
        menu.getSlot(1).set(book);
        menu.createResult();
        return menu;
    }

    /** The key of a minecraft enchantment; built at use, never in a static initializer. */
    private static ResourceKey<Enchantment> key(String path) {
        return TestGame.key(Registries.ENCHANTMENT, path);
    }

    /** The registry holder of a minecraft enchantment. */
    private static Holder<Enchantment> holder(GameTestHelper helper, String path) {
        return TestGame.holder(helper, Registries.ENCHANTMENT, path);
    }

    /** The keys of every enchantment a stack carries, applied and stored. */
    private static Set<ResourceKey<Enchantment>> enchantmentKeys(ItemStack stack) {
        Set<ResourceKey<Enchantment>> keys = new HashSet<>();
        ItemEnchantments applied = Components.get(stack, DataComponents.ENCHANTMENTS);
        if (applied != null) {
            keys.addAll(Lookups.keys(applied.keySet()));
        }
        ItemEnchantments stored = Components.get(stack, DataComponents.STORED_ENCHANTMENTS);
        if (stored != null) {
            keys.addAll(Lookups.keys(stored.keySet()));
        }
        return keys;
    }

    /**
     * Fails unless the live snapshot holds what these scenarios rely on: mending and both curses
     * blacklisted, unbreaking not. A later change of the fixture then fails loudly here.
     */
    private static void requireFixture(GameTestHelper helper, BlacklistSnapshot live) {
        for (String path : List.of("mending", "binding_curse", "vanishing_curse")) {
            Check.isTrue(helper, live.enchantment(key(path)),
                    "fixture: minecraft:" + path + " is not blacklisted");
        }
        Check.isTrue(helper, !live.enchantment(key("unbreaking")),
                "fixture: minecraft:unbreaking is blacklisted");
    }

    /** Fails when the stack carries a blacklisted enchantment; 1 when it carries any, else 0. */
    private static int checkClean(GameTestHelper helper, BlacklistSnapshot live, ItemStack stack,
            String what) {
        Set<ResourceKey<Enchantment>> keys = enchantmentKeys(stack);
        for (ResourceKey<Enchantment> key : keys) {
            Check.isTrue(helper, !live.enchantment(key), what + " carries " + Keys.name(key));
        }
        return keys.isEmpty() ? 0 : 1;
    }

    /** Every holder of the enchantment registry, mending and the curses included. */
    private static Stream<Holder<Enchantment>> allEnchantments(MinecraftServer server) {
        return Lookups.of(server.registryAccess(), Registries.ENCHANTMENT).listElements()
                .map(holder -> (Holder<Enchantment>) holder);
    }
}
