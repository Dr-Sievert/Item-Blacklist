package net.sievert.item_blacklist.gametest;

import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.line.Keys;
import net.sievert.item_blacklist.report.RemovalReport;
import net.sievert.item_blacklist.report.ReportSnapshot;
import net.sievert.item_blacklist.trades.TradeRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * The trades scenarios (prefix trades_): offers are judged where the game makes them, so the
 * live ones summon merchants and read the offers they make, and the rules are also tested on
 * built snapshots. Each is synchronous: set up, act, assert and succeed inside its call, and
 * each holds before and after a reload. Merchants are summoned through TestGame.summon and
 * discarded at once, so none stays in the test area.
 */
public final class TradeScenarios {
    /** The professions of trades_villager_offers_clean: the fixture's items, books, arrows. */
    private static final String[] PROFESSIONS = {"farmer", "fletcher", "librarian"};
    /** Merchants per case; offers are random, so each case is sampled. */
    private static final int SAMPLES = 10;

    private TradeScenarios() {
    }

    /**
     * The trades_ scenarios, and the cleric clause of reload_keeps_filters; called by
     * ItemBlacklistGameTests.register.
     */
    public static void register(BiConsumer<String, Consumer<GameTestHelper>> sink) {
        sink.accept("trades_cleric_refused", TradeScenarios::clericRefused);
        sink.accept("trades_guard_rules", TradeScenarios::guardRules);
        sink.accept("trades_rule", TradeScenarios::rule);
        sink.accept("trades_saved_offers_kept", TradeScenarios::savedOffersKept);
        sink.accept("trades_villager_offers_clean", TradeScenarios::villagerOffersClean);
        sink.accept("trades_wandering_clean", TradeScenarios::wanderingClean);
        ReloadScenarios.afterReload("trades", TradeScenarios::assertClericRefused);
    }

    /**
     * Ten level 3 clerics: the level has two listings and two picks on every release, one of
     * them rabbit_foot for an emerald, so without the offer hook every cleric offers rabbit
     * foot, and with it every cleric keeps exactly the glowstone offer.
     */
    static void clericRefused(GameTestHelper helper) {
        for (int i = 0; i < SAMPLES; i++) {
            assertClericRefused(helper);
        }
        helper.succeed();
    }

    /**
     * One fresh level 3 cleric: exactly one offer, none with rabbit_foot. Also run by
     * reload_keeps_filters after its reload.
     */
    static void assertClericRefused(GameTestHelper helper) {
        List<MerchantOffer> offers = offersOf(helper, "minecraft:villager", villager("cleric", 3));
        Check.equal(helper, offers.size(), 1, "the number of a level 3 cleric's offers");
        for (MerchantOffer offer : offers) {
            Check.isTrue(helper, !uses(offer, Items.RABBIT_FOOT),
                    "A level 3 cleric offers rabbit_foot");
        }
    }

    /**
     * Farmers, fletchers and librarians of every level, ten each: no offer holds anything the
     * live blacklist refuses, whichever layer kept it out (the tag strip, the brewing filter,
     * the offer hook). Cartographers are left out: their maps search for structures.
     */
    static void villagerOffersClean(GameTestHelper helper) {
        BlacklistSnapshot live = TestGame.live();
        Check.isTrue(helper, !live.isEmpty(), "The fixture's blacklist is not live");
        for (String profession : PROFESSIONS) {
            for (int level = 1; level <= 5; level++) {
                for (int i = 0; i < SAMPLES; i++) {
                    for (MerchantOffer offer : offersOf(helper, "minecraft:villager",
                            villager(profession, level))) {
                        String cause = TradeRules.refusal(live, offer);
                        Check.isTrue(helper, cause == null,
                                "A level " + level + " " + profession + " offers " + cause);
                    }
                }
            }
        }
        helper.succeed();
    }

    /**
     * Ten wandering traders: offers, none refused by the live blacklist, none with the
     * fixture's beetroot_seeds, a common wandering listing on every release.
     */
    static void wanderingClean(GameTestHelper helper) {
        BlacklistSnapshot live = TestGame.live();
        for (int i = 0; i < SAMPLES; i++) {
            List<MerchantOffer> offers =
                    offersOf(helper, "minecraft:wandering_trader", "{NoAI:1b}");
            Check.isTrue(helper, !offers.isEmpty(), "A wandering trader has no offers");
            for (MerchantOffer offer : offers) {
                String cause = TradeRules.refusal(live, offer);
                Check.isTrue(helper, cause == null, "A wandering trader offers " + cause);
                Check.isTrue(helper, !uses(offer, Items.BEETROOT_SEEDS),
                        "A wandering trader offers beetroot_seeds");
            }
        }
        helper.succeed();
    }

    /**
     * A villager whose saved offers sell oak planks keeps them: offers that exist are never
     * judged again. The offers come in through the entity data, as from a world (overrideOffers
     * does nothing on AbstractVillager); the villager data is loaded without resetting them.
     */
    static void savedOffersKept(GameTestHelper helper) {
        String nbt = "{NoAI:1b,VillagerData:{profession:\"minecraft:farmer\",level:1,"
                + "type:\"minecraft:plains\"},Offers:{Recipes:[{buy:{id:\"minecraft:emerald\","
                + "count:1},sell:{id:\"minecraft:oak_planks\",count:4},maxUses:12}]}}";
        Entity villager = TestGame.summon(helper, "minecraft:villager", nbt);
        try {
            List<MerchantOffer> offers = ((Merchant) villager).getOffers();
            Check.equal(helper, offers.size(), 1, "the number of the saved offers");
            Check.isTrue(helper, offers.get(0).getResult().getItem() == Items.OAK_PLANKS,
                    "The saved planks offer was changed");
            Check.isTrue(helper, !TradeRules.offerAllowed(TestGame.live(), offers.get(0)),
                    "oak_planks is not blacklisted in the live state");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    /**
     * Every clause of TradeRules.refusal and admit on built snapshots, independent of the
     * fixture and of the hooks: cost A, cost B, the result, a stored enchantment, an
     * enchantment, a potion and a tipped arrow without a potion each refuse; a clean offer, an
     * allowed enchantment or potion, a plain arrow while no potion is blacklisted and an empty
     * blacklist pass; admit records one refusal and hands the others back unchanged.
     */
    static void rule(GameTestHelper helper) {
        BlacklistSnapshot snapshot = TestGame.snapshot(b -> b
                .item(TestGame.key(Registries.ITEM, "rabbit_foot"))
                .potion(TestGame.key(Registries.POTION, "strength"))
                .enchantment(TestGame.key(Registries.ENCHANTMENT, "mending")));
        BlacklistSnapshot itemOnly =
                TestGame.snapshot(b -> b.item(TestGame.key(Registries.ITEM, "rabbit_foot")));
        ItemCost emerald = new ItemCost(Items.EMERALD);

        MerchantOffer costA = offer(new ItemCost(Items.RABBIT_FOOT, 2), Optional.empty(),
                new ItemStack(Items.EMERALD));
        refused(helper, snapshot, costA, "a blacklisted cost A");
        refused(helper, snapshot, offer(emerald, Optional.of(new ItemCost(Items.RABBIT_FOOT)),
                new ItemStack(Items.STONE)), "a blacklisted cost B");
        refused(helper, snapshot, offer(emerald, Optional.empty(),
                new ItemStack(Items.RABBIT_FOOT)), "a blacklisted result");
        refused(helper, snapshot, offer(emerald, Optional.empty(),
                TestGame.book(helper, "mending")), "a stored enchantment");
        refused(helper, snapshot, offer(emerald, Optional.empty(),
                TestGame.enchanted(helper, Items.IRON_SWORD, "mending")), "an enchantment");
        refused(helper, snapshot, offer(emerald, Optional.empty(),
                TestGame.potionStack(helper, Items.TIPPED_ARROW, "strength")),
                "a blacklisted potion");
        refused(helper, snapshot, offer(emerald, Optional.empty(),
                new ItemStack(Items.TIPPED_ARROW)), "a tipped arrow without a potion");

        allowed(helper, snapshot, offer(emerald, Optional.empty(), new ItemStack(Items.STONE)),
                "nothing blacklisted");
        allowed(helper, snapshot, offer(emerald, Optional.empty(),
                TestGame.book(helper, "unbreaking")), "an allowed stored enchantment");
        allowed(helper, snapshot, offer(emerald, Optional.empty(),
                TestGame.potionStack(helper, Items.TIPPED_ARROW, "leaping")),
                "an allowed potion");
        allowed(helper, itemOnly, offer(emerald, Optional.empty(),
                new ItemStack(Items.TIPPED_ARROW)),
                "a tipped arrow without a potion and no potion blacklisted");
        allowed(helper, BlacklistSnapshot.EMPTY, costA, "an empty blacklist");

        Check.equal(helper, TradeRules.refusal(snapshot, costA), "minecraft:rabbit_foot",
                "the cause of the cost A refusal");
        Check.equal(helper, TradeRules.refusal(snapshot, offer(emerald, Optional.empty(),
                TestGame.book(helper, "mending"))), "minecraft:mending",
                "the cause of the book refusal");
        Check.equal(helper, TradeRules.refusal(snapshot, offer(emerald, Optional.empty(),
                new ItemStack(Items.TIPPED_ARROW))), TradeRules.NO_POTION,
                "the cause of the plain arrow refusal");

        RemovalReport report = new RemovalReport();
        Check.isTrue(helper, TradeRules.admit(snapshot, costA, null, report) == null,
                "admit let a refused offer through");
        MerchantOffer clean = offer(emerald, Optional.empty(), new ItemStack(Items.STONE));
        Check.isTrue(helper, TradeRules.admit(snapshot, clean, null, report) == clean,
                "admit changed an allowed offer");
        Check.isTrue(helper, TradeRules.admit(snapshot, null, null, report) == null,
                "admit made an offer from null");
        ReportSnapshot flushed = report.flush(false);
        Check.equal(helper, flushed.count(ReportSnapshot.Kind.TRADE), 1,
                "the recorded trade refusals");
        Check.equal(helper, flushed.subjects(ReportSnapshot.Kind.TRADE),
                Set.of(TradeRules.UNKNOWN), "the refusing merchants");
        helper.succeed();
    }

    /**
     * The rules of the two 1.21.x guards, on every release, the only automatic proof they get:
     * their firing needs another config than the fixture. The configured #curse refuses a book
     * listing, #tradeable (neither named nor emptied) and an empty blacklist do not; the live
     * arrow pool is not empty (swiftness stays brewable from sugar), and it is with every
     * potion blacklisted.
     */
    static void guardRules(GameTestHelper helper) {
        BlacklistSnapshot live = TestGame.live();
        TagKey<Enchantment> tradeable = Keys.tag(Registries.ENCHANTMENT, "minecraft", "tradeable");
        TagKey<Enchantment> curse = Keys.tag(Registries.ENCHANTMENT, "minecraft", "curse");
        Check.isTrue(helper, TradeRules.bookListingAllowed(live, tradeable),
                "The #tradeable book listing is refused");
        Check.isTrue(helper, !TradeRules.bookListingAllowed(live, curse),
                "A book listing of the configured #curse is allowed");
        Check.isTrue(helper, TradeRules.bookListingAllowed(BlacklistSnapshot.EMPTY, curse),
                "An empty blacklist refuses a book listing");

        PotionBrewing brewing = TestGame.server(helper).potionBrewing();
        Check.isTrue(helper, !TradeRules.arrowPoolEmpty(live, brewing),
                "The live arrow pool is empty");
        BlacklistSnapshot everyPotion = TestGame.snapshot(b -> {
            for (Potion potion : BuiltInRegistries.POTION) {
                BuiltInRegistries.POTION.getResourceKey(potion).ifPresent(b::potion);
            }
        });
        Check.isTrue(helper, TradeRules.arrowPoolEmpty(everyPotion, brewing),
                "An arrow pool with every potion blacklisted is not empty");
        helper.succeed();
    }

    /**
     * The summon NBT of a villager of that profession and level, without AI; one text on every
     * release (the VillagerData keys type, profession and level). Summoned with NBT, the
     * villager skips finalizeSpawn and keeps both.
     */
    private static String villager(String profession, int level) {
        return "{NoAI:1b,VillagerData:{profession:\"minecraft:" + profession + "\",level:"
                + level + ",type:\"minecraft:plains\"}}";
    }

    /**
     * Summons the merchant, reads its offers, which makes them, and discards it. Read through
     * Merchant, which keeps its package on every release, while the villager classes move at
     * 1.21.11.
     */
    private static List<MerchantOffer> offersOf(GameTestHelper helper, String entity,
            String nbt) {
        Entity merchant = TestGame.summon(helper, entity, nbt);
        try {
            return new ArrayList<>(((Merchant) merchant).getOffers());
        } finally {
            merchant.discard();
        }
    }

    /** Whether the offer's base cost A, cost B or result is of the item. */
    private static boolean uses(MerchantOffer offer, Item item) {
        return offer.getBaseCostA().getItem() == item || offer.getCostB().getItem() == item
                || offer.getResult().getItem() == item;
    }

    /** An offer as the listings build one: 12 uses, 1 xp, a price multiplier of 0.05. */
    private static MerchantOffer offer(ItemCost costA, Optional<ItemCost> costB,
            ItemStack result) {
        return new MerchantOffer(costA, costB, result, 12, 1, 0.05F);
    }

    private static void refused(GameTestHelper helper, BlacklistSnapshot snapshot,
            MerchantOffer offer, String what) {
        Check.isTrue(helper, !TradeRules.offerAllowed(snapshot, offer),
                "An offer with " + what + " must be refused");
    }

    private static void allowed(GameTestHelper helper, BlacklistSnapshot snapshot,
            MerchantOffer offer, String what) {
        String cause = TradeRules.refusal(snapshot, offer);
        Check.isTrue(helper, cause == null, "An offer with " + what + " was refused for " + cause);
    }
}
