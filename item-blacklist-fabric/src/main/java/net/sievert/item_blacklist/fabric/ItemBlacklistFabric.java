package net.sievert.item_blacklist.fabric;

import net.sievert.item_blacklist.ItemBlacklistMod;
import net.sievert.item_blacklist.fabric.line.FabricPayloads;
import net.sievert.item_blacklist.network.Sync;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * The Fabric entrypoint (fabric.mod.json, "main"): hands Fabric's hooks to ItemBlacklistMod.
 * Every listener sits in the default phase, so the order among other mods' listeners is
 * Fabric's.
 */
public final class ItemBlacklistFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ItemBlacklistMod.init();
        // Here, not in the client entrypoint: both sides need the type, and "main" runs first.
        FabricPayloads.register();
        ServerLifecycleEvents.SERVER_STARTING.register(ItemBlacklistMod::onServerStarting);
        ServerLifecycleEvents.SERVER_STARTED.register(ItemBlacklistMod::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(ItemBlacklistMod::onServerStopped);
        // At every join and, for every player, at every reload: the one sync timing.
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register(
                (player, joined) -> Sync.send(player));
    }
}
