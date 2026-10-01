package net.sievert.item_blacklist.fabric.client;

import net.sievert.item_blacklist.client.ClientSync;
import net.sievert.item_blacklist.network.BlacklistSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * The Fabric client entrypoint (fabric.mod.json, "client"): the only Fabric class that names
 * client code, so a dedicated server never loads it. Receives the server's blacklist and
 * drops it at every join and disconnect; the payload type is registered by "main" before this
 * runs.
 */
public final class ItemBlacklistFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientSync.install();
        ClientPlayNetworking.registerGlobalReceiver(BlacklistSyncPayload.TYPE,
                (payload, context) -> ClientSync.accept(payload));
        ClientPlayConnectionEvents.JOIN.register(
                (listener, sender, client) -> ClientSync.loggedIn());
        ClientPlayConnectionEvents.DISCONNECT.register(
                (listener, client) -> ClientSync.loggedOut());
    }
}
