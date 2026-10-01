package net.sievert.item_blacklist.client;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.brewing.BrewingFilter;
import net.sievert.item_blacklist.integration.jei.JeiHook;
import net.sievert.item_blacklist.log.Log;
import net.sievert.item_blacklist.log.LogTag;
import net.sievert.item_blacklist.network.BlacklistSyncPayload;
import net.sievert.item_blacklist.network.ClientSyncSlot;
import net.sievert.item_blacklist.network.Sync;
import net.sievert.item_blacklist.platform.Services;
import net.sievert.item_blacklist.report.Recorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * The client half of the sync: keeps a remote server's blacklist, refilters what the client
 * shows from it, and drops it at login and disconnect. Called by the two loader client entry
 * classes only. Every method runs on the client thread and hops there when called elsewhere:
 * Fabric's disconnect can fire on a netty thread. Client paths record nothing (Recorder.NONE),
 * so a client never writes into a server's report, in singleplayer either.
 */
public final class ClientSync {
    private ClientSync() {
    }

    /** Makes the NeoForge payload handler reach accept, and opens JeiHook's client gate. */
    public static void install() {
        ClientSyncSlot.install(ClientSync::accept);
    }

    /**
     * A payload from the server, in this order: keep it (ignored while a server runs in this
     * JVM, whose own snapshot wins), filter the connection's brewing from it, then JEI.
     */
    public static void accept(BlacklistSyncPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> accept(payload));
            return;
        }
        Blacklist.acceptRemote(Sync.snapshotOf(payload));
        Log.info(LogTag.INIT, "Received the server's blacklist: {} items, {} potions, "
                        + "{} enchantments{}",
                payload.items().size(), payload.potions().size(), payload.enchantments().size(),
                Blacklist.hasServer() ? " (not used: a server runs in this JVM)" : "");
        // The level is null before the first join finishes; the next payload filters it then.
        ClientLevel level = minecraft.level;
        if (level != null) {
            BrewingFilter.apply(level.potionBrewing(), Blacklist.effective(), Recorder.NONE);
            Services.PLATFORM.filterLoaderBrewing(level.potionBrewing(), Blacklist.effective(),
                    Recorder.NONE);
        }
        JeiHook.refilter();
    }

    /**
     * The client joined a server: drops a synced blacklist a missed disconnect left behind,
     * before the new server's payload arrives.
     */
    public static void loggedIn() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(ClientSync::loggedIn);
            return;
        }
        Blacklist.clearRemote();
    }

    /** The client left a server: drops its synced blacklist and what JEI was told of it. */
    public static void loggedOut() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(ClientSync::loggedOut);
            return;
        }
        Blacklist.clearRemote();
        Log.info(LogTag.INIT, "Cleared the server's blacklist");
        JeiHook.clearRuntime();
    }
}
