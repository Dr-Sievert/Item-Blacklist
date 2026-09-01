package net.sievert.item_blacklist.platform.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public interface NetworkService {

    void syncBlacklist(ServerPlayer player);

    default void syncBlacklist(
            MinecraftServer server
    ) {
        for (ServerPlayer player :
                server.getPlayerList().getPlayers()) {
            syncBlacklist(player);
        }
    }
}