package net.sievert.item_blacklist.fabric;

import net.fabricmc.api.ModInitializer;
import net.sievert.item_blacklist.ItemBlacklist;

public final class ItemBlacklistFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ItemBlacklist.init();
    }
}
