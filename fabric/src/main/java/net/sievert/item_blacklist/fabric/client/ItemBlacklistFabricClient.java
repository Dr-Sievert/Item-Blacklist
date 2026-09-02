package net.sievert.item_blacklist.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.sievert.item_blacklist.blacklist.BlacklistBrewingManager;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import net.sievert.item_blacklist.integration.jei.BlacklistJeiManager;
import net.sievert.item_blacklist.network.BlacklistSyncPayload;

public final class ItemBlacklistFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) ->
                        BlacklistManager.setRemoteClient(
                                !client.hasSingleplayerServer()
                        )
        );

        ClientPlayNetworking.registerGlobalReceiver(
                BlacklistSyncPayload.TYPE,
                (payload, context) -> {
                    BlacklistManager.setSyncedBlacklist(
                            payload.items(),
                            payload.potions(),
                            payload.enchantments()
                    );

                    Minecraft minecraft =
                            Minecraft.getInstance();

                    if (minecraft.level != null) {
                        BlacklistBrewingManager.filter(
                                minecraft.level.potionBrewing()
                        );
                    }

                    BlacklistJeiManager.filterRecipes();
                }
        );

        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> {
                    BlacklistManager.setRemoteClient(false);
                    BlacklistJeiManager.clearRuntime();
                }
        );
    }
}