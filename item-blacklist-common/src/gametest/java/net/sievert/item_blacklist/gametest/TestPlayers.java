package net.sievert.item_blacklist.gametest;

import java.util.UUID;
import java.util.function.Consumer;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;

/**
 * A real survival ServerPlayer for scenarios. It repeats vanilla's mock server player without
 * its creative override: vanilla's is marked for removal on every release, so no version
 * module may call it. The player joins through PlayerList, the real join path, on a connection
 * over an embedded channel, and leaves in a finally block.
 */
public final class TestPlayers {
    private TestPlayers() {
    }

    /** A survival ServerPlayer placed in the test's level for the body, removed afterwards. */
    public static void withSurvival(GameTestHelper helper, Consumer<ServerPlayer> body) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "ib-test-player"), false);
        ServerPlayer player =
                new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        // The channel binds itself to the connection; nothing reads it.
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        try {
            player.setGameMode(GameType.SURVIVAL);
            body.accept(player);
        } finally {
            server.getPlayerList().remove(player);
        }
    }
}
