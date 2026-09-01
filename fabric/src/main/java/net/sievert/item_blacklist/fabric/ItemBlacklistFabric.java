package net.sievert.item_blacklist.fabric;

import net.fabricmc.api.ModInitializer;
import net.sievert.item_blacklist.ItemBlacklist;
import net.sievert.item_blacklist.fabric.platform.FabricEnvironmentService;
import net.sievert.item_blacklist.platform.ItemBlacklistServices;

public final class ItemBlacklistFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ItemBlacklistServices.registerEnvironment(
                new FabricEnvironmentService()
        );

        ItemBlacklist.init();
    }
}