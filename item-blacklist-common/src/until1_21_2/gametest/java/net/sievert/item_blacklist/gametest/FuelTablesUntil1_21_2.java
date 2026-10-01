package net.sievert.item_blacklist.gametest;

import java.util.Set;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/**
 * The fuel table below 1.21.2: one static table, asked through AbstractFurnaceBlockEntity's
 * static isFuel (on NeoForge through the burn-time event); no per-level list exists.
 */
public final class FuelTablesUntil1_21_2 implements FuelTables.Backend {
    @Override
    public boolean isFuel(GameTestHelper helper, ItemStack stack) {
        return AbstractFurnaceBlockEntity.isFuel(stack);
    }

    @Override
    public Set<Item> fuelItems(GameTestHelper helper) {
        return null;
    }
}
