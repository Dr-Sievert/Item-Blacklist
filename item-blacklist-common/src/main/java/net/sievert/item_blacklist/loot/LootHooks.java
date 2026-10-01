package net.sievert.item_blacklist.loot;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.ServerState;
import net.sievert.item_blacklist.log.Log;
import net.sievert.item_blacklist.log.LogTag;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;

/**
 * The JSON hooks' one body, shared by the three windowed mixins, which only find the table id
 * and the parse input: fetch the running server's snapshot, walk the tree into a buffer,
 * commit the buffer when the table loaded. Called by those mixins only, on the loot workers
 * of a reload, several tables at once; each call writes only its own tree and buffer, and at
 * commit the thread-safe report.
 */
public final class LootHooks {
    private LootHooks() {
    }

    /**
     * Before a loot table's JSON is decoded: filters the input in place and returns the
     * buffered records, or null when nothing was walked (no running server, an empty
     * blacklist, an input that is no JSON, a fault of the walk). Never throws. The running
     * server's snapshot, never the synced one: a loot walk shapes the server's tables only.
     */
    public static LootRecordBuffer beforeDecode(String tableId, Object input) {
        BlacklistSnapshot snapshot = Blacklist.server();
        if (snapshot.isEmpty()) {
            // Also the world load: no server state exists yet, and the forced reload walks.
            return null;
        }
        if (!(input instanceof JsonElement json)) {
            warnNotJson(tableId, input);
            return null;
        }
        LootRecordBuffer pending = new LootRecordBuffer(Blacklist.recorder());
        try {
            LootJsonFilter.filter(json, tableId, snapshot, pending);
        } catch (RuntimeException e) {
            // The tree may already be partly filtered; the table still loads, so a fault of
            // the walk never drops loot. Its buffered records go with the buffer.
            Log.warn(LogTag.LOOT, "Loot table {} could not be filtered completely, it loads"
                    + " with what was removed before the fault: {}", tableId, e.toString());
            return null;
        }
        return pending;
    }

    /** After the decode: the buffered records reach the report only when the table loaded. */
    public static void afterDecode(LootRecordBuffer pending, boolean loaded) {
        if (pending != null && loaded) {
            pending.commit();
        }
    }

    /**
     * Whether a Codec.parse result is a loaded table: a success whose value is not an empty
     * Optional. NeoForge wraps the codec in its conditional codec, whose value is an Optional,
     * empty when the table's neoforge:conditions fail; vanilla and Fabric hand back the table.
     * A failed parse is not loaded either.
     */
    public static boolean loaded(DataResult<?> result) {
        return result.result()
                .map(value -> !(value instanceof Optional<?> optional) || optional.isPresent())
                .orElse(false);
    }

    /** One WARN per server when the parse input is no JSON: the walk cannot run there. */
    private static void warnNotJson(String tableId, Object input) {
        ServerState state = Blacklist.serverState();
        if (state == null) {
            return;
        }
        if (state.slot(NotJsonWarning.class, NotJsonWarning::new).first()) {
            Log.warn(LogTag.LOOT, "Loot table {} reached the JSON filter as {}, not as JSON;"
                    + " loot JSON is not filtered on this server, loot rolls still are",
                    tableId, input == null ? "null" : input.getClass().getName());
        }
    }

    /** The per-server "warned once" flag of the non-JSON case: a ServerState slot, no static. */
    static final class NotJsonWarning {
        private final AtomicBoolean done = new AtomicBoolean();

        /** True for the first caller only, on any thread. */
        boolean first() {
            return done.compareAndSet(false, true);
        }
    }
}
