package net.sievert.item_blacklist.fabric.gametest;

import net.sievert.item_blacklist.gametest.Fixtures;
import net.sievert.item_blacklist.gametest.ItemBlacklistGameTests;
import net.sievert.item_blacklist.line.Keys;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The loot fixture on 1.21.6 to 1.21.11: a MODIFY_DROPS listener that adds oak planks and
 * stone to the test table loot_additions_filtered only, after the table rolled, the way another
 * mod would; loot_additions_filtered proves the roll filter still refuses the planks. The
 * table is matched by its registry holder's key, so no id class is named.
 */
public final class LootFixtureSince1_21_6 implements LootFixture.Backend {
    @Override
    public void register() {
        ResourceKey<LootTable> table = Keys.of(Registries.LOOT_TABLE,
                ItemBlacklistGameTests.NAMESPACE, "loot_additions_filtered");
        LootTableEvents.MODIFY_DROPS.register((holder, context, drops) -> {
            if (holder.is(table)) {
                drops.add(new ItemStack(Items.OAK_PLANKS));
                drops.add(new ItemStack(Items.STONE));
            }
        });
        Fixtures.lootAdditions = true;
    }
}
