package net.sievert.item_blacklist.blacklist;

import net.sievert.item_blacklist.config.BlacklistConfig;
import net.sievert.item_blacklist.line.Keys;
import net.sievert.item_blacklist.log.Log;
import net.sievert.item_blacklist.log.LogTag;
import net.sievert.item_blacklist.report.Recorder;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;

/**
 * The one holder of blacklist state in the JVM: the running server's state, and apart from it
 * the snapshot a remote server sent. Each reader reads a volatile field once, so a hook on any
 * thread sees one whole snapshot; the writers are Lifecycle's, on the server thread, and have
 * no side effect. Without a server and without a synced snapshot every reader sees EMPTY.
 */
public final class Blacklist {
    private static volatile ServerState SERVER;
    private static volatile BlacklistSnapshot REMOTE = BlacklistSnapshot.EMPTY;

    private Blacklist() {
    }

    /**
     * The running server's snapshot, else the synced one, else EMPTY: for hooks that also shape
     * what a client shows or predicts. A server in this JVM always wins over a synced snapshot.
     */
    public static BlacklistSnapshot effective() {
        ServerState state = SERVER;
        return state != null ? state.snapshot() : REMOTE;
    }

    /** The running server's snapshot, else EMPTY; never the synced one. */
    public static BlacklistSnapshot server() {
        ServerState state = SERVER;
        return state != null ? state.snapshot() : BlacklistSnapshot.EMPTY;
    }

    /** The running server's state, or null. */
    public static ServerState serverState() {
        return SERVER;
    }

    /** Whether a server runs in this JVM, as far as the mod saw it start. */
    public static boolean hasServer() {
        return SERVER != null;
    }

    /**
     * The running server's report on any thread, else Recorder.NONE: a record made while no
     * server runs has nowhere to go.
     */
    public static Recorder recorder() {
        ServerState state = SERVER;
        return state != null ? state.report() : Recorder.NONE;
    }

    /**
     * The running server's report when called on that server's own thread, else Recorder.NONE:
     * for hooks a client may run too, such as the offer hooks, which JER calls on clients. So a
     * client thread in a singleplayer JVM never writes into the integrated server's report.
     */
    public static Recorder serverRecorder() {
        ServerState state = SERVER;
        return state != null && state.onServerThread() ? state.report() : Recorder.NONE;
    }

    /**
     * The tag hooks' entry, called by the two tag mixins only: filters one registry's incoming
     * tags and publishes the snapshot the load derived. It acts only for a running server's
     * state on that server's thread; the world load (before any server exists), clients and
     * worker threads get their map back untouched. A fault of the filter is logged and the tags
     * stay unfiltered, so the reload goes on.
     */
    public static <T> Map<TagKey<T>, List<Holder<T>>> onTagLoad(
            ResourceKey<? extends Registry<T>> registry,
            Map<TagKey<T>, List<Holder<T>>> incoming) {
        ServerState state = SERVER;
        if (state == null || !state.onServerThread()) {
            return incoming;
        }
        try {
            TagFilter.Result<T> result =
                    TagFilter.apply(state.snapshot(), registry, incoming, state.report());
            if (result.snapshot() != state.snapshot()) {
                publish(state, result.snapshot());
            }
            return result.tags();
        } catch (RuntimeException e) {
            Log.error(LogTag.TAG, "Tag filter failed for " + Keys.name(registry)
                    + ", the tags stay unfiltered", e);
            return incoming;
        }
    }

    /**
     * Keeps the snapshot a remote server sent; ignored while a server runs in this JVM, whose
     * own snapshot every hook reads then. Called by ClientSync and scenarios.
     */
    public static void acceptRemote(BlacklistSnapshot synced) {
        if (SERVER == null) {
            REMOTE = synced;
        }
    }

    /**
     * Drops the synced snapshot. Called at client login, at client disconnect and at every
     * server starting, each idempotent, so a missed disconnect heals at the next of the three;
     * scenarios may call it too.
     */
    public static void clearRemote() {
        REMOTE = BlacklistSnapshot.EMPTY;
    }

    /** Lifecycle only, on the server thread: opens the state of a starting server. */
    static ServerState start(MinecraftServer server, BlacklistConfig config,
            BlacklistSnapshot first) {
        ServerState state = new ServerState(server, config, first);
        SERVER = state;
        return state;
    }

    /** The tag hook and Lifecycle only: the state's next snapshot; a plain write. */
    static void publish(ServerState state, BlacklistSnapshot next) {
        state.snapshot(next);
    }

    /** Lifecycle only: drops the state of this server with its slots; another's stays. */
    static void stop(MinecraftServer server) {
        ServerState state = SERVER;
        if (state != null && state.server() == server) {
            SERVER = null;
        }
    }
}
