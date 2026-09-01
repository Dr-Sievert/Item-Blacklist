package net.sievert.item_blacklist.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.sievert.item_blacklist.blacklist.BlacklistBrewingManager;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import net.sievert.item_blacklist.integration.BlacklistJeiManager;
import net.sievert.item_blacklist.network.BlacklistSyncPayload;

public final class ItemBlacklistFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                BlacklistSyncPayload.TYPE,
                (payload, context) -> {
                    BlacklistManager.setSyncedBlacklist(
                            payload.items(),
                            payload.potions()
                    );

                    Minecraft minecraft =
                            Minecraft.getInstance();

                    if (minecraft.level != null) {
                        BlacklistBrewingManager.filter(
                                minecraft.level.potionBrewing()
                        );
                    }

                    BlacklistJeiManager.filterBrewingRecipes();
                }
        );

        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> {
                    BlacklistManager.clearSyncedBlacklist();
                    BlacklistJeiManager.clearRuntime();
                }
        );
    }
}