package net.sievert.item_blacklist.neoforge;

import net.sievert.item_blacklist.ItemBlacklistMod;
import net.sievert.item_blacklist.network.BlacklistSyncPayload;
import net.sievert.item_blacklist.network.ClientSyncSlot;
import net.sievert.item_blacklist.network.Sync;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The NeoForge mod class: hands NeoForge's events to ItemBlacklistMod. Every listener is added
 * with addListener at default priority (no EventBusSubscriber, whose bus argument is gone on
 * later releases). A dedicated server loads this class too, so it names no client class: the
 * payload handler reaches the client through ClientSyncSlot.
 */
@Mod(ItemBlacklistMod.MOD_ID)
public final class ItemBlacklistNeoForge {
    /** FML passes the mod's event bus, for registration events; game events go to NeoForge's. */
    public ItemBlacklistNeoForge(IEventBus modBus) {
        ItemBlacklistMod.init();
        modBus.addListener(RegisterPayloadHandlersEvent.class,
                ItemBlacklistNeoForge::registerPayloads);
        NeoForge.EVENT_BUS.addListener(ServerAboutToStartEvent.class,
                event -> ItemBlacklistMod.onServerStarting(event.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class,
                event -> ItemBlacklistMod.onServerStarted(event.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class,
                event -> ItemBlacklistMod.onServerStopped(event.getServer()));
        // At every join and, for every player, at every reload: the one sync timing.
        NeoForge.EVENT_BUS.addListener(OnDatapackSyncEvent.class,
                event -> event.getRelevantPlayers().forEach(Sync::send));
        NeoForgeLoaderFilters.register();
    }

    /**
     * The sync payload, server to client, version "1" as the old mod had it. Optional, so a
     * client without the mod may join; Platform.sendToPlayer then sends it nothing.
     */
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(BlacklistSyncPayload.TYPE,
                BlacklistSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientSyncSlot.deliver(payload));
    }
}
