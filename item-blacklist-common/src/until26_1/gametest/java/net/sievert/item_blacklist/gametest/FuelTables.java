package net.sievert.item_blacklist.gametest;

import net.sievert.item_blacklist.line.Backends;
import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The test level's fuel check and fuel list on the 1.21.x line. 1.21.1 has one static fuel
 * table behind AbstractFurnaceBlockEntity.isFuel; from 1.21.2 each server and connection builds
 * a FuelValues and the static call is gone, so a scenario compiled at the line's floor reaches
 * the table through this facade, which hands the calls to {@code FuelTablesUntil1_21_2} or
 * {@code FuelTablesSince1_21_2}. CLAUDE.md, "A name renamed or removed inside a line".
 */
public final class FuelTables {
    /** The calls that differ; each backend implements them for its window. */
    public interface Backend {
        /** Whether the test level's furnaces take the stack as fuel by the table. */
        boolean isFuel(GameTestHelper helper, ItemStack stack);

        /** The items of the test level's fuel table; null where no per-level table exists. */
        Set<Item> fuelItems(GameTestHelper helper);
    }

    private static final Backend BACKEND = Backends.pick("FuelTables", Backend.class,
            Backends.until("1.21.2", "net.sievert.item_blacklist.gametest.FuelTablesUntil1_21_2"),
            Backends.since("1.21.2", "net.sievert.item_blacklist.gametest.FuelTablesSince1_21_2"));

    private FuelTables() {
    }

    /**
     * Whether the test level's furnaces take the stack as fuel by the table, not by a furnace's
     * slot check: the static isFuel at 1.21.1, the level's FuelValues from 1.21.2.
     */
    public static boolean isFuel(GameTestHelper helper, ItemStack stack) {
        return BACKEND.isFuel(helper, stack);
    }

    /**
     * The items of the test level's fuel table, the list the recipe book shows; null below
     * 1.21.2, where no per-level table exists.
     */
    public static Set<Item> fuelItems(GameTestHelper helper) {
        return BACKEND.fuelItems(helper);
    }
}
