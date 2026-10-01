package net.sievert.item_blacklist.gametest;

import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.integration.jer.JerReflect;
import net.sievert.item_blacklist.integration.jer.JerRules;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * The jer scenarios (prefix jer_): every rule the JER mixins call, on the live and on built
 * snapshots, and JerReflect across two mods' jars. No server shows JER's pages, so the mixins
 * themselves are checked by hand, on a dev client; these prove what they run. Each is
 * synchronous, holds before and after a reload, reads the live snapshot and changes no state.
 */
public final class JerScenarios {
    private JerScenarios() {
    }

    /** The jer_ scenarios; called by ItemBlacklistGameTests.register. */
    public static void register(BiConsumer<String, Consumer<GameTestHelper>> sink) {
        sink.accept("jer_rules", JerScenarios::rules);
        sink.accept("jer_reflect_cross_jar", JerScenarios::reflectCrossJar);
    }

    /**
     * Stands in for JER's TradeList: a LinkedList subclass with a field of its own, which a
     * clone must keep.
     */
    public static final class StubTradeList extends LinkedList<Object> {
        final String owner;

        StubTradeList(String owner, List<Object> trades) {
            super(trades);
            this.owner = owner;
        }
    }

    /**
     * Stands in for JER's EnchantmentWrapper and LootDrop: private and private final fields
     * of JER's names, and a public getDrops(). It ships in the test mod's jar, so JerReflect,
     * in the mod's jar, reaches it across jars as it reaches JER.
     */
    public static final class Stub {
        private final List<ItemStack> enchantments;
        private final int lastSet;
        private int set;

        Stub(List<ItemStack> enchantments, int lastSet, int set) {
            this.enchantments = enchantments;
            this.lastSet = lastSet;
            this.set = set;
        }

        /** Read by name through JerReflect, as JER's LootDrop.getDrops() is. */
        public List<ItemStack> getDrops() {
            return enchantments;
        }
    }

    /**
     * JerRules on the fixture's live snapshot (oak planks, mending, #curse and strength in;
     * stone, coal and unbreaking out) and on built ones: removal, order, identity when nothing
     * goes, the fail-open cases, a count-0 stack, the clone of a list subclass, JER's paging.
     */
    static void rules(GameTestHelper helper) {
        BlacklistSnapshot live = TestGame.live();
        ItemStack planks = new ItemStack(Items.OAK_PLANKS);
        ItemStack stone = new ItemStack(Items.STONE);
        ItemStack coal = new ItemStack(Items.COAL);

        // visibleStacks: removal, order, null kept, identity when nothing goes, the snapshot
        // handed in
        List<ItemStack> mixed = new ArrayList<>(Arrays.asList(null, planks, stone));
        List<ItemStack> shown = JerRules.visibleStacks(live, mixed);
        Check.equal(helper, shown.size(), 2,
                "the number of visible stacks of [null, planks, stone]");
        Check.isTrue(helper, shown.get(0) == null && shown.get(1) == stone,
                "null and stone stay, in order");
        List<ItemStack> clean = List.of(stone, coal);
        Check.isTrue(helper, JerRules.visibleStacks(live, clean) == clean,
                "nothing blacklisted: the same list");
        List<ItemStack> onlyPlanks = List.of(planks);
        Check.isTrue(helper,
                JerRules.visibleStacks(BlacklistSnapshot.EMPTY, onlyPlanks) == onlyPlanks,
                "empty snapshot: the same list");
        BlacklistSnapshot builtStone =
                TestGame.snapshot(b -> b.item(TestGame.key(Registries.ITEM, "stone")));
        List<ItemStack> builtShown = JerRules.visibleStacks(builtStone, List.of(planks, stone));
        Check.isTrue(helper, builtShown.size() == 1 && builtShown.get(0) == planks,
                "a snapshot of stone hides stone and keeps planks");

        // tradeVisible: each position, a count of 0, a stored enchantment, a potion, unreadable
        Check.isTrue(helper, JerRules.tradeVisible(live, coal, ItemStack.EMPTY, stone),
                "a clean trade is shown");
        Check.isTrue(helper, JerRules.tradeVisible(live, null, null, null),
                "an unreadable trade is shown");
        Check.isTrue(helper, !JerRules.tradeVisible(live, planks, ItemStack.EMPTY, stone),
                "cost A planks is refused");
        Check.isTrue(helper, !JerRules.tradeVisible(live, coal, planks, stone),
                "cost B planks is refused");
        Check.isTrue(helper, !JerRules.tradeVisible(live, coal, ItemStack.EMPTY, planks),
                "result planks is refused");
        ItemStack noPlanks = new ItemStack(Items.OAK_PLANKS, 0);
        Check.isTrue(helper, !JerRules.tradeVisible(live, coal, ItemStack.EMPTY, noPlanks),
                "result planks with count 0 is refused");
        Check.equal(helper, noPlanks.getCount(), 0,
                "the count of the count-0 stack after the check");
        Check.isTrue(helper, !JerRules.tradeVisible(live, coal, ItemStack.EMPTY,
                TestGame.book(helper, "mending")), "a book with stored mending is refused");
        Check.isTrue(helper, !JerRules.tradeVisible(live, coal, ItemStack.EMPTY,
                TestGame.potionStack(helper, Items.POTION, "strength")),
                "a strength potion is refused");

        // visibleTrades: the clone keeps class and fields, identity when nothing goes
        StubTradeList trades = new StubTradeList("villager", List.of("clean", "planks"));
        StubTradeList tradesShown = JerRules.visibleTrades(live, trades,
                trade -> coal, trade -> ItemStack.EMPTY,
                trade -> "planks".equals(trade) ? planks : stone);
        Check.isTrue(helper, tradesShown != trades && tradesShown.size() == 1
                && "clean".equals(tradesShown.get(0)), "the planks trade leaves a copy");
        Check.isTrue(helper, "villager".equals(tradesShown.owner),
                "the copy keeps the list's own field");
        Check.equal(helper, trades.size(), 2, "the size of the original trade list");
        Check.isTrue(helper,
                JerRules.visibleTrades(live, trades, t -> coal, t -> null, t -> stone) == trades,
                "no trade refused: the same list");

        // dropVisible and visibleDrops
        Check.isTrue(helper, !JerRules.dropVisible(live, List.of(planks)),
                "a drop of planks only is hidden");
        Check.isTrue(helper, JerRules.dropVisible(live, List.of(planks, stone)),
                "a drop with stone stays");
        Check.isTrue(helper, !JerRules.dropVisible(live, List.of()),
                "a drop with no stack is hidden");
        Check.isTrue(helper, JerRules.dropVisible(live, "no list"),
                "a drop read as no collection stays");
        Check.isTrue(helper, JerRules.dropVisible(live, JerReflect.FAILED), "a failed read stays");
        List<Object> drops = List.of("planks", "stone");
        List<Object> dropsShown = JerRules.visibleDrops(live, drops,
                drop -> "planks".equals(drop) ? List.of(planks) : List.of(stone));
        Check.isTrue(helper, dropsShown.size() == 1 && "stone".equals(dropsShown.get(0)),
                "the planks drop leaves");
        List<Object> withNull = Arrays.asList(null, "stone");
        List<Object> withNullShown = JerRules.visibleDrops(live, withNull, drop -> List.of(stone));
        Check.isTrue(helper, withNullShown.size() == 1 && "stone".equals(withNullShown.get(0)),
                "a null drop leaves, as in the old mod");

        // visibleEnchantments on holders of the server's registry (never a hand-made Holder)
        Holder<Enchantment> mending = TestGame.holder(helper, Registries.ENCHANTMENT, "mending");
        Holder<Enchantment> unbreaking =
                TestGame.holder(helper, Registries.ENCHANTMENT, "unbreaking");
        Holder<Enchantment> curse =
                TestGame.holder(helper, Registries.ENCHANTMENT, "binding_curse");
        List<Holder<Enchantment>> entries = List.of(mending, unbreaking, curse);
        List<Holder<Enchantment>> entriesShown =
                JerRules.visibleEnchantments(live, entries, holder -> holder);
        Check.isTrue(helper, entriesShown.size() == 1 && entriesShown.get(0) == unbreaking,
                "mending and binding_curse leave, unbreaking stays");
        List<Holder<Enchantment>> onlyUnbreaking = List.of(unbreaking);
        Check.isTrue(helper,
                JerRules.visibleEnchantments(live, onlyUnbreaking, holder -> holder)
                        == onlyUnbreaking,
                "nothing blacklisted: the same list");
        List<String> named = List.of("mending", "unreadable");
        List<String> namedShown = JerRules.visibleEnchantments(live, named,
                name -> "mending".equals(name) ? mending : null);
        Check.isTrue(helper, namedShown.size() == 1 && "unreadable".equals(namedShown.get(0)),
                "an entry without a holder stays");
        BlacklistSnapshot builtUnbreaking = TestGame.snapshot(
                b -> b.enchantment(TestGame.key(Registries.ENCHANTMENT, "unbreaking")));
        List<Holder<Enchantment>> builtEntries = JerRules.visibleEnchantments(builtUnbreaking,
                List.of(mending, unbreaking), holder -> holder);
        Check.isTrue(helper, builtEntries.size() == 1 && builtEntries.get(0) == mending,
                "a snapshot of unbreaking keeps mending");
        Check.isTrue(helper, JerRules.asEnchantmentHolder(mending) == mending
                && JerRules.asEnchantmentHolder("x") == null, "asEnchantmentHolder");
        Check.isTrue(helper, JerRules.asStack(stone) == stone
                && JerRules.asStack(JerReflect.FAILED) == null, "asStack");

        // lastPage: pages of 11
        Check.equal(helper, JerRules.lastPage(0), 0, "lastPage(0)");
        Check.equal(helper, JerRules.lastPage(11), 0, "lastPage(11)");
        Check.equal(helper, JerRules.lastPage(12), 1, "lastPage(12)");
        Check.equal(helper, JerRules.lastPage(22), 1, "lastPage(22)");
        Check.equal(helper, JerRules.lastPage(23), 2, "lastPage(23)");
        helper.succeed();
    }

    /**
     * JerReflect, in the mod's jar, calls a public method and reads and writes private and
     * private final fields of a class in the test mod's jar, which is what the enchantment,
     * mob and trade mixins do to JER; then the mob mixin's handler body on the live snapshot.
     * Only successful paths run, so no JerReflect WARN appears; the failing ones are
     * unit-tested.
     */
    static void reflectCrossJar(GameTestHelper helper) {
        ItemStack stone = new ItemStack(Items.STONE);
        Stub stub = new Stub(new LinkedList<>(List.of(stone)), 0, 3);
        Object drops = JerReflect.call(stub, "getDrops");
        Check.isTrue(helper,
                drops instanceof List<?> list && list.size() == 1 && list.get(0) == stone,
                "a public method of a class of another jar, called by name");
        List<ItemStack> replaced = new ArrayList<>();
        Check.isTrue(helper, JerReflect.set(stub, "enchantments", replaced)
                && JerReflect.get(stub, "enchantments") == replaced,
                "a private final List field written and read");
        Check.isTrue(helper, JerReflect.set(stub, "lastSet", 2)
                && Integer.valueOf(2).equals(JerReflect.get(stub, "lastSet")),
                "a private final int field written");
        Check.isTrue(helper, JerReflect.set(stub, "set", 0)
                && Integer.valueOf(0).equals(JerReflect.get(stub, "set")),
                "a private int field written");

        // The mob mixin's handler body, verbatim, on the live snapshot: drops read by name
        // across the jars
        ItemStack planks = new ItemStack(Items.OAK_PLANKS);
        Stub planksDrop = new Stub(new LinkedList<>(List.of(planks)), 0, 0);
        Stub stoneDrop = new Stub(new LinkedList<>(List.of(stone)), 0, 0);
        List<Stub> mobDrops = List.of(planksDrop, stoneDrop);
        List<Stub> mobShown = JerRules.visibleDrops(TestGame.live(), mobDrops,
                drop -> JerReflect.call(drop, "getDrops"));
        Check.isTrue(helper, mobShown.size() == 1 && mobShown.get(0) == stoneDrop,
                "a drop whose getDrops() holds only planks leaves, one with stone stays");
        helper.succeed();
    }
}
