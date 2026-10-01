package net.sievert.item_blacklist.fabric.line;

import net.sievert.item_blacklist.network.BlacklistSyncPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * The payload type's registration on the 1.21.x line, where Fabric API calls the server to
 * client play registry playS2C; the 26.x twin calls it clientboundPlay. Called by
 * ItemBlacklistFabric in "main", before the client entrypoint registers the receiver, which
 * Fabric API refuses for an unregistered type.
 */
public final class FabricPayloads {
    private FabricPayloads() {
    }

    /** Registers the sync payload's type and codec for the play phase, server to client. */
    public static void register() {
        PayloadTypeRegistry.playS2C()
                .register(BlacklistSyncPayload.TYPE, BlacklistSyncPayload.STREAM_CODEC);
    }
}
