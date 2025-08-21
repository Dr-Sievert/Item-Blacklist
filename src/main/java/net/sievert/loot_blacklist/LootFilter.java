package net.sievert.loot_blacklist;

import net.fabricmc.fabric.api.loot.v3.FabricLootPoolBuilder;
import net.fabricmc.fabric.api.loot.v3.FabricLootTableBuilder;
import net.minecraft.item.Item;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.sievert.loot_blacklist.mixin.ItemEntryAccessor;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Filters loot tables and pools to remove blacklisted items
 * using Fabric’s official v3 Loot API.
 */
public class LootFilter {

    /**
     * Removes blacklisted item entries from all pools in a Fabric loot table builder.
     * Used inside Fabric's LootTableEvents.MODIFY callback.
     */
    public static int filterLootTableBuilder(FabricLootTableBuilder tableBuilder, Set<Identifier> blacklist, Identifier tableId) {
        AtomicInteger removedCount = new AtomicInteger();

        tableBuilder.modifyPools(poolBuilder -> {
            LootPool original = poolBuilder.build();
            FabricLootPoolBuilder newPool = FabricLootPoolBuilder.copyOf(original);

            newPool.with(original.entries.stream()
                    .filter(entry -> {
                        if (entry instanceof ItemEntry itemEntry) {
                            RegistryEntry<Item> regEntry = ((ItemEntryAccessor) itemEntry).getItemEntry();
                            Optional<RegistryKey<Item>> key = regEntry.getKey();
                            if (key.isPresent() && blacklist.contains(key.get().getValue())) {
                                removedCount.incrementAndGet();
                                return false;
                            }
                        }
                        return true;
                    })
                    .toList()
            );
        });

        return removedCount.get();
    }

}
