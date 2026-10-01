package net.sievert.item_blacklist.gametest;

import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The fuel table from 1.21.2 to 1.21.11: the test level's FuelValues, the server's table,
 * which the fuel builder hook filtered as it was built.
 */
public final class FuelTablesSince1_21_2 implements FuelTables.Backend {
    @Override
    public boolean isFuel(GameTestHelper helper, ItemStack stack) {
        return helper.getLevel().fuelValues().isFuel(stack);
    }

    @Override
    public Set<Item> fuelItems(GameTestHelper helper) {
        return Set.copyOf(helper.getLevel().fuelValues().fuelItems());
    }
}
