package net.sievert.item_blacklist.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.sievert.item_blacklist.ItemBlacklist;
import net.sievert.item_blacklist.network.BlacklistSyncPayload;

public final class ItemBlacklistFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(
                BlacklistSyncPayload.TYPE,
                BlacklistSyncPayload.STREAM_CODEC
        );

        ItemBlacklist.init(
                FabricLoader.getInstance().getConfigDir()
        );
    }
}