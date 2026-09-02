package net.sievert.item_blacklist.neoforge.blacklist;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;
import net.neoforged.neoforge.registries.datamaps.builtin.Compostable;
import net.neoforged.neoforge.registries.datamaps.builtin.FurnaceFuel;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;
import net.sievert.item_blacklist.blacklist.BlacklistManager;

import java.util.Map;

public final class NeoForgeDataMapFilter {

    private NeoForgeDataMapFilter() {}

    public static void onDataMapsUpdated(
            DataMapsUpdatedEvent event
    ) {
        if (event.getCause()
                != DataMapsUpdatedEvent.UpdateCause.SERVER_RELOAD) {
            return;
        }

        event.ifRegistry(
                Registries.ITEM,
                NeoForgeDataMapFilter::filter
        );
    }

    private static void filter(
            Registry<Item> itemRegistry
    ) {
        filterCompostables(
                itemRegistry
        );

        filterFurnaceFuels(
                itemRegistry
        );
    }

    private static void filterCompostables(
            Registry<Item> itemRegistry
    ) {
        Map<ResourceKey<Item>, Compostable> compostables =
                itemRegistry.getDataMap(
                        NeoForgeDataMaps.COMPOSTABLES
                );

        compostables.keySet().removeIf(
                itemKey ->
                        BlacklistManager.isBlacklisted(
                                itemKey.location()
                        )
        );
    }

    private static void filterFurnaceFuels(
            Registry<Item> itemRegistry
    ) {
        Map<ResourceKey<Item>, FurnaceFuel> furnaceFuels =
                itemRegistry.getDataMap(
                        NeoForgeDataMaps.FURNACE_FUELS
                );

        furnaceFuels.keySet().removeIf(
                itemKey ->
                        BlacklistManager.isBlacklisted(
                                itemKey.location()
                        )
        );
    }
}