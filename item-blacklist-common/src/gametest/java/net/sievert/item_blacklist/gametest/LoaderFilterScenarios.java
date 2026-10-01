package net.sievert.item_blacklist.gametest;

import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.filters.CompostFilter;
import net.sievert.item_blacklist.filters.LoaderFilterRules;
import net.sievert.item_blacklist.platform.Capability;
import net.sievert.item_blacklist.platform.Services;
import net.sievert.item_blacklist.report.Recorder;
import net.sievert.item_blacklist.report.RemovalReport;
import net.sievert.item_blacklist.report.ReportSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The loader-filters scenarios (prefix filters_): compost and furnace fuel, on both loaders.
 * Fabric's composter reads vanilla's compost table and NeoForge's its data map; fuel goes
 * through a per-query hook at 1.21.1, through every built FuelValues from 1.21.2, and on
 * NeoForge also through the burn-time event. The scenarios ask the game the way a player's
 * action does, so each one proves whichever of these the running loader and release use. Each
 * is synchronous: set up, act, assert and succeed inside its call, and holds before and after
 * a reload.
 */
public final class LoaderFilterScenarios {
    /** The one block each scenario places, inside the shared 3x3x3 structure, as place_stone. */
    private static final BlockPos POS = new BlockPos(1, 1, 1);

    /** The furnace's fuel slot. */
    private static final int FUEL_SLOT = 1;

    private LoaderFilterScenarios() {
    }

    /** The filters_ scenarios; called by ItemBlacklistGameTests.register. */
    public static void register(BiConsumer<String, Consumer<GameTestHelper>> sink) {
        sink.accept("filters_compost_refused", LoaderFilterScenarios::compostRefused);
        sink.accept("filters_compost_restored", LoaderFilterScenarios::compostRestored);
        sink.accept("filters_fuel_refused", LoaderFilterScenarios::fuelRefused);
        sink.accept("filters_fuel_stack_rule", LoaderFilterScenarios::fuelStackRule);
        sink.accept("filters_fuel_table", LoaderFilterScenarios::fuelTable);
        sink.accept("filters_rules", LoaderFilterScenarios::rules);
    }

    /**
     * A composter refuses the fixture's beetroot seeds and takes wheat seeds. On Fabric the
     * composter reads COMPOSTABLES, which CompostFilter filtered; on NeoForge it reads only the
     * neoforge:compostables data map, so this isolates the data-map listener there.
     */
    static void compostRefused(GameTestHelper helper) {
        helper.setBlock(POS, Blocks.COMPOSTER);
        BlockState empty = helper.getBlockState(POS);
        BlockPos at = helper.absolutePos(POS);
        ItemStack beetroot = new ItemStack(Items.BEETROOT_SEEDS);
        BlockState after = ComposterBlock.insertItem(null, empty, helper.getLevel(), beetroot, at);
        Check.equal(helper, beetroot.getCount(), 1, "the beetroot seeds left after insertItem");
        Check.isTrue(helper, after == empty,
                "A blacklisted item must leave the composter unchanged");
        // The control: a compostable is taken whatever the chance roll gives.
        ItemStack wheat = new ItemStack(Items.WHEAT_SEEDS);
        ComposterBlock.insertItem(null, empty, helper.getLevel(), wheat, at);
        Check.equal(helper, wheat.getCount(), 0, "the wheat seeds left after insertItem");
        helper.succeed();
    }

    /**
     * CompostFilter works from what it removed: restore() gives entries back, a smaller built
     * blacklist gives back what it no longer names, an empty one gives back everything, and the
     * live blacklist removes the same again. The live filtering is put back in a finally block
     * inside this call, so no other scenario sees the built state.
     */
    // NeoForge marks COMPOSTABLES deprecated, not for removal, in favour of its data map.
    @SuppressWarnings("deprecation")
    static void compostRestored(GameTestHelper helper) {
        BlacklistSnapshot live = TestGame.live();
        try {
            CompostFilter.restore();
            Check.isTrue(helper, ComposterBlock.COMPOSTABLES.containsKey(Items.BEETROOT_SEEDS),
                    "restore() must give the beetroot seeds back");
            CompostFilter.apply(
                    TestGame.snapshot(b -> b.item(TestGame.key(Registries.ITEM, "wheat_seeds"))),
                    Recorder.NONE);
            Check.isTrue(helper, !ComposterBlock.COMPOSTABLES.containsKey(Items.WHEAT_SEEDS),
                    "A built blacklist of wheat seeds must remove them");
            Check.isTrue(helper, ComposterBlock.COMPOSTABLES.containsKey(Items.BEETROOT_SEEDS),
                    "A smaller blacklist must give the beetroot seeds back");
            CompostFilter.apply(BlacklistSnapshot.EMPTY, Recorder.NONE);
            Check.isTrue(helper, ComposterBlock.COMPOSTABLES.containsKey(Items.WHEAT_SEEDS)
                    && ComposterBlock.COMPOSTABLES.containsKey(Items.BEETROOT_SEEDS),
                    "An empty blacklist must restore every entry");
        } finally {
            // The live filtering again, also after a failed check.
            CompostFilter.apply(live, Recorder.NONE);
        }
        Check.isTrue(helper, !ComposterBlock.COMPOSTABLES.containsKey(Items.BEETROOT_SEEDS),
                "The live blacklist must remove the beetroot seeds again");
        Check.isTrue(helper, ComposterBlock.COMPOSTABLES.containsKey(Items.WHEAT_SEEDS),
                "The wheat seeds must stay after the live filtering");
        helper.succeed();
    }

    /**
     * A furnace's fuel slot refuses charcoal (named) and oak planks (named, and through
     * #planks) and takes coal: the 1.21.1 query hook on Fabric, the fuel builder hook from
     * 1.21.2 on Fabric, the burn-time listener on NeoForge. The same expectation on all 26.
     */
    static void fuelRefused(GameTestHelper helper) {
        Container furnace = furnace(helper);
        Check.isTrue(helper, !furnace.canPlaceItem(FUEL_SLOT, new ItemStack(Items.CHARCOAL)),
                "Charcoal accepted as fuel");
        Check.isTrue(helper, !furnace.canPlaceItem(FUEL_SLOT, new ItemStack(Items.OAK_PLANKS)),
                "Oak planks accepted as fuel");
        Check.isTrue(helper, furnace.canPlaceItem(FUEL_SLOT, new ItemStack(Items.COAL)),
                "Coal refused as fuel");
        helper.succeed();
    }

    /**
     * The hooks that judge the stack, not only its item: a bow is fuel, a bow with mending is
     * none where the stack is judged, which is NeoForge's burn-time listener on every release
     * and the query hook on Fabric 1.21.1. From 1.21.2 Fabric judges the item only (the fuel
     * builder), so the enchanted bow is fuel there; asserted, so that a change of it shows.
     */
    static void fuelStackRule(GameTestHelper helper) {
        Container furnace = furnace(helper);
        Check.isTrue(helper, furnace.canPlaceItem(FUEL_SLOT, new ItemStack(Items.BOW)),
                "A plain bow refused as fuel");
        boolean stackLevel = Fixtures.loaderFuelStackHook
                || !Services.PLATFORM.supports(Capability.FUEL_VALUES);
        ItemStack mended = TestGame.enchanted(helper, Items.BOW, "mending");
        Check.equal(helper, furnace.canPlaceItem(FUEL_SLOT, mended), !stackLevel,
                "whether a bow with mending is accepted as fuel");
        helper.succeed();
    }

    /**
     * The fuel table itself, apart from any furnace: from 1.21.2 the level's FuelValues and its
     * fuel list (the recipe book's), which on NeoForge is built from the furnace_fuels data map,
     * so this isolates the data-map listener and the builder hook from the burn-time event; at
     * 1.21.1 the static isFuel. The list exists exactly where Capability.FUEL_VALUES says, so a
     * window that disagrees with FuelTables' pick fails.
     */
    static void fuelTable(GameTestHelper helper) {
        Check.isTrue(helper, !FuelTables.isFuel(helper, new ItemStack(Items.CHARCOAL)),
                "Charcoal is fuel by the table");
        Check.isTrue(helper, !FuelTables.isFuel(helper, new ItemStack(Items.OAK_PLANKS)),
                "Oak planks are fuel by the table");
        Check.isTrue(helper, FuelTables.isFuel(helper, new ItemStack(Items.COAL)),
                "Coal is no fuel by the table");
        Set<Item> listed = FuelTables.fuelItems(helper);
        Check.equal(helper, listed != null, Services.PLATFORM.supports(Capability.FUEL_VALUES),
                "whether a per-level fuel table exists");
        if (listed != null) {
            Check.isTrue(helper,
                    !listed.contains(Items.CHARCOAL) && !listed.contains(Items.OAK_PLANKS),
                    "The fuel list holds a blacklisted item");
            Check.isTrue(helper, listed.contains(Items.COAL), "The fuel list lacks coal");
        }
        helper.succeed();
    }

    /**
     * Both table rules on built input, on both loaders, so Fabric boots run the NeoForge rule
     * too: what goes, what stays, the records named through Keys, the empty snapshot's
     * shortcut and the Recorder.NONE path.
     */
    static void rules(GameTestHelper helper) {
        ResourceKey<Item> coalKey = TestGame.key(Registries.ITEM, "coal");
        ResourceKey<Item> stoneKey = TestGame.key(Registries.ITEM, "stone");
        BlacklistSnapshot coalOnly = TestGame.snapshot(b -> b.item(coalKey));
        RemovalReport recorder = new RemovalReport();

        Map<Item, Integer> byItem = new LinkedHashMap<>();
        byItem.put(Items.COAL, 1600);
        byItem.put(Items.STONE, 1);
        Check.equal(helper, LoaderFilterRules.stripItems(byItem, coalOnly, "test_items", recorder),
                1, "the items stripped");
        Check.equal(helper, byItem.keySet(), Set.of(Items.STONE), "the items left");

        Map<ResourceKey<Item>, String> byKey = new LinkedHashMap<>();
        byKey.put(coalKey, "coal");
        byKey.put(stoneKey, "stone");
        Check.equal(helper, LoaderFilterRules.stripKeys(byKey, coalOnly, "test_keys", recorder),
                1, "the keys stripped");
        Check.equal(helper, byKey.keySet(), Set.of(stoneKey), "the keys left");
        Check.equal(helper, recorder.flush(false).entries(), Map.of(ReportSnapshot.Kind.LOADER,
                Map.of("test_items", Set.of("minecraft:coal"),
                        "test_keys", Set.of("minecraft:coal"))), "the records");

        Map<Item, Integer> untouched = new LinkedHashMap<>();
        untouched.put(Items.COAL, 1600);
        Check.equal(helper,
                LoaderFilterRules.stripItems(untouched, BlacklistSnapshot.EMPTY, "x", recorder),
                0, "the items stripped by an empty snapshot");
        Check.equal(helper, LoaderFilterRules.stripItems(untouched, coalOnly, "y", Recorder.NONE),
                1, "the items stripped without a recorder");
        Check.equal(helper, untouched.size(), 0, "the entries left");
        Check.equal(helper, recorder.flush(false).entries(), Map.of(),
                "the records of the empty snapshot and Recorder.NONE");
        helper.succeed();
    }

    /**
     * A furnace at POS, as a Container (the furnace's block entity is one on every release);
     * fails the scenario when the block entity is missing.
     */
    private static Container furnace(GameTestHelper helper) {
        helper.setBlock(POS, Blocks.FURNACE);
        BlockEntity entity = TestGame.blockEntity(helper, POS);
        if (!(entity instanceof Container container)) {
            Check.fail(helper, "Expected a furnace block entity at " + POS + ", found " + entity);
            throw new IllegalStateException("unreachable");
        }
        return container;
    }
}
