package net.sievert.item_blacklist.gametest;

import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The test level's fuel check and fuel list on the 26.x line: the level's FuelValues, the one
 * form every release of the line has. The 1.21.x twin is a facade, since the table changed at
 * 1.21.2.
 */
public final class FuelTables {
    private FuelTables() {
    }

    /**
     * Whether the test level's furnaces take the stack as fuel by the table, not by a furnace's
     * slot check: the level's FuelValues.
     */
    public static boolean isFuel(GameTestHelper helper, ItemStack stack) {
        return helper.getLevel().fuelValues().isFuel(stack);
    }

    /** The items of the test level's fuel table, the list the recipe book shows; never null. */
    public static Set<Item> fuelItems(GameTestHelper helper) {
        return Set.copyOf(helper.getLevel().fuelValues().fuelItems());
    }
}
