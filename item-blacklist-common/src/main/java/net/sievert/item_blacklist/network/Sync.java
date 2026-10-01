package net.sievert.item_blacklist.network;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.ServerState;
import net.sievert.item_blacklist.line.Keys;
import net.sievert.item_blacklist.log.Log;
import net.sievert.item_blacklist.log.LogTag;
import net.sievert.item_blacklist.platform.Services;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * The server half of the sync: builds the payload from the running server's snapshot, turns a
 * received payload back into a snapshot, and sends it to one player. Called by the loaders'
 * join and reload listeners and by scenarios.
 */
public final class Sync {
    /** The three VarInt list counts, at most five bytes each. */
    private static final int COUNT_BYTES = 15;

    /** Per entry beyond its name: the VarInt of the name's length, with room to spare. */
    private static final int ENTRY_BYTES = 5;

    private Sync() {
    }

    /**
     * The snapshot's tag-expanded items, potions and enchantments, in its PATH_FIRST order: a
     * client gets no tags, so what a tag brought in travels as plain keys.
     */
    public static BlacklistSyncPayload payloadOf(BlacklistSnapshot snapshot) {
        return new BlacklistSyncPayload(List.copyOf(snapshot.items()),
                List.copyOf(snapshot.potions()), List.copyOf(snapshot.enchantments()));
    }

    /**
     * A snapshot with the payload's lists as explicit entries and no tags: what a client holds
     * of a remote server's blacklist. Item keys this client lacks are dropped by build(); potion
     * and enchantment keys are kept and simply match nothing.
     */
    public static BlacklistSnapshot snapshotOf(BlacklistSyncPayload payload) {
        BlacklistSnapshot.Builder builder = BlacklistSnapshot.builder();
        for (ResourceKey<Item> item : payload.items()) {
            builder.item(item);
        }
        for (ResourceKey<Potion> potion : payload.potions()) {
            builder.potion(potion);
        }
        for (ResourceKey<Enchantment> enchantment : payload.enchantments()) {
            builder.enchantment(enchantment);
        }
        return builder.build();
    }

    /**
     * The encoded size, estimated from above: per entry the name's length plus five (its
     * length's VarInt), plus fifteen for the three counts. Ids are ASCII, so a character is a
     * byte; the estimate is never below the real size of a payload with an entry.
     */
    public static int estimatedBytes(BlacklistSyncPayload payload) {
        return COUNT_BYTES + listBytes(payload.items()) + listBytes(payload.potions())
                + listBytes(payload.enchantments());
    }

    /**
     * Whether the payload may be sent: every list within MAX_ENTRIES, which the codec would
     * refuse anyway, and the estimate within MAX_BYTES.
     */
    public static boolean fits(BlacklistSyncPayload payload) {
        return payload.items().size() <= BlacklistSyncPayload.MAX_ENTRIES
                && payload.potions().size() <= BlacklistSyncPayload.MAX_ENTRIES
                && payload.enchantments().size() <= BlacklistSyncPayload.MAX_ENTRIES
                && estimatedBytes(payload) <= BlacklistSyncPayload.MAX_BYTES;
    }

    /**
     * Sends the running server's blacklist to the player, built from Blacklist.server() and
     * never from the synced view; false when nothing was sent. A payload too large is not sent,
     * with one WARN per server: the server still enforces everything, only that client's
     * display stays unfiltered. A player without the channel (a vanilla client) gets nothing.
     */
    public static boolean send(ServerPlayer player) {
        BlacklistSyncPayload payload = payloadOf(Blacklist.server());
        if (!fits(payload)) {
            ServerState state = Blacklist.serverState();
            if (state == null || state.slot(Oversize.class, Oversize::new).first()) {
                Log.warn(LogTag.INIT, "The blacklist is too large to sync ({} items, {} potions, "
                        + "{} enchantments, about {} bytes); clients show it unfiltered",
                        payload.items().size(), payload.potions().size(),
                        payload.enchantments().size(), estimatedBytes(payload));
            }
            return false;
        }
        return Services.PLATFORM.sendToPlayer(player, payload);
    }

    private static int listBytes(List<? extends ResourceKey<?>> keys) {
        int bytes = 0;
        for (ResourceKey<?> key : keys) {
            bytes += Keys.name(key).length() + ENTRY_BYTES;
        }
        return bytes;
    }

    /** The "warned once" flag of one server, kept in its state's slot, not a static. */
    private static final class Oversize {
        private final AtomicBoolean warned = new AtomicBoolean();

        /** True for the first caller only. */
        boolean first() {
            return warned.compareAndSet(false, true);
        }
    }
}
