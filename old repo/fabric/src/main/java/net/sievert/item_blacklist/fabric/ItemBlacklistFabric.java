package net.sievert.item_blacklist.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.sievert.item_blacklist.ItemBlacklist;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import net.sievert.item_blacklist.fabric.blacklist.FabricCompostingFilter;
import net.sievert.item_blacklist.fabric.blacklist.FabricFuelFilter;
import net.sievert.item_blacklist.network.BlacklistSyncPayload;

import java.util.List;

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

        ServerLifecycleEvents.SERVER_STARTED.register(
                server -> filterRegistries()
        );

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register(
                (server, resourceManager, success) -> {
                    if (!success) {
                        return;
                    }

                    filterRegistries();
                    syncBlacklistToAll(server);
                }
        );

        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) ->
                        syncBlacklist(handler.player)
        );
    }

    private static void filterRegistries() {
        FabricCompostingFilter.filter();
        FabricFuelFilter.filter();
    }

    private static void syncBlacklistToAll(
            MinecraftServer server
    ) {
        for (ServerPlayer player :
                server.getPlayerList().getPlayers()) {
            syncBlacklist(player);
        }
    }

    private static void syncBlacklist(
            ServerPlayer player
    ) {
        if (!ServerPlayNetworking.canSend(
                player,
                BlacklistSyncPayload.TYPE
        )) {
            return;
        }

        ServerPlayNetworking.send(
                player,
                new BlacklistSyncPayload(
                        List.copyOf(
                                BlacklistManager.getResolvedBlacklist()
                        ),
                        List.copyOf(
                                BlacklistManager.getBlacklistedPotions()
                        ),
                        List.copyOf(
                                BlacklistManager.getBlacklistedEnchantments()
                        )
                )
        );
    }
}
