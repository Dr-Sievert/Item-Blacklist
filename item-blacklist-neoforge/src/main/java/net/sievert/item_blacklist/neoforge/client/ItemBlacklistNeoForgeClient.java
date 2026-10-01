package net.sievert.item_blacklist.neoforge.client;

import net.sievert.item_blacklist.ItemBlacklistMod;
import net.sievert.item_blacklist.client.ClientSync;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The NeoForge client entry class: FML constructs it on physical clients only
 * (dist = Dist.CLIENT), so it is the one NeoForge class that may name client code. It installs
 * the receiver behind the payload handler and drops the synced blacklist at every login and
 * logout.
 */
@Mod(value = ItemBlacklistMod.MOD_ID, dist = Dist.CLIENT)
public final class ItemBlacklistNeoForgeClient {
    /** FML passes the mod's event bus; the client's listeners are game events, on NeoForge's. */
    public ItemBlacklistNeoForgeClient(IEventBus modBus) {
        ClientSync.install();
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class,
                event -> ClientSync.loggedIn());
        // NeoForge posts LoggingOut at every world load and connect attempt too, without a
        // player; only a real logout has one.
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            if (event.getPlayer() != null) {
                ClientSync.loggedOut();
            }
        });
    }
}
