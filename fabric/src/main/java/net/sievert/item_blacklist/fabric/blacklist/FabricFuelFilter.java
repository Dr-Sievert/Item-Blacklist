package net.sievert.item_blacklist.fabric.blacklist;

import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.sievert.item_blacklist.blacklist.BlacklistManager;

import java.util.LinkedHashMap;
import java.util.Map;

public final class FabricFuelFilter {

    private static final Map<Item, Integer> REMOVED_FUELS =
            new LinkedHashMap<>();

    private FabricFuelFilter() {}

    public static void filter() {
        restoreRemoved();

        for (ResourceLocation itemId :
                BlacklistManager.getResolvedBlacklist()) {
            Item item =
                    BuiltInRegistries.ITEM.get(
                            itemId
                    );

            Integer burnTime =
                    FuelRegistry.INSTANCE.get(
                            item
                    );

            if (burnTime == null
                    || burnTime <= 0) {
                continue;
            }

            REMOVED_FUELS.put(
                    item,
                    burnTime
            );

            FuelRegistry.INSTANCE.remove(
                    item
            );
        }
    }

    private static void restoreRemoved() {
        REMOVED_FUELS.forEach(
                FuelRegistry.INSTANCE::add
        );

        REMOVED_FUELS.clear();
    }
}