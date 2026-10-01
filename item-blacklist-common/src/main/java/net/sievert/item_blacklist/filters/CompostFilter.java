package net.sievert.item_blacklist.filters;

import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.line.Keys;
import net.sievert.item_blacklist.report.Recorder;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.ComposterBlock;

/**
 * The vanilla compost table (ComposterBlock.COMPOSTABLES, a static on every release), which no
 * hook at the point of use covers: blacklisted items leave it after every server starting and
 * reload, and come back at the server's stop. Every call works from the entries this class
 * removed before, so a second call with the same snapshot changes nothing and a call with a
 * smaller blacklist gives entries back. Called by Lifecycle (its static filters and its stop)
 * and by the filters_ scenarios, on the server thread.
 *
 * <p>On NeoForge the composter reads the neoforge:compostables data map instead
 * (NeoForgeLoaderFilters filters it); the table is edited there too, so both loaders run and
 * report the same code, and only NeoForge's own datagen reads it.
 */
public final class CompostFilter {
    /** The table's name in the report (Recorder.loaderRemoval). */
    public static final String TABLE = "compost";

    /**
     * The entries this class took out of COMPOSTABLES, with their chances, in removal order.
     * Static, beside the JVM-global table it restores, and not in a ServerState slot: a state
     * dropped without restore() (a missed stop, a starting that replaces another server's
     * state) would lose them for the JVM's life, while here the next apply puts them back
     * first. It holds game data, no blacklist state. Guarded by the class lock.
     */
    private static final Map<ItemLike, Float> REMOVED = new LinkedHashMap<>();

    private CompostFilter() {
    }

    /**
     * Restores every entry removed before, then removes every entry whose item the snapshot
     * blacklists, explicit and tag-derived alike, recording each one under {@link #TABLE}.
     */
    // NeoForge marks COMPOSTABLES deprecated, not for removal, in favour of its data map.
    @SuppressWarnings("deprecation")
    public static synchronized void apply(BlacklistSnapshot snapshot, Recorder recorder) {
        restoreLocked();
        if (snapshot.isEmpty()) {
            return;
        }
        Iterator<Object2FloatMap.Entry<ItemLike>> entries =
                ComposterBlock.COMPOSTABLES.object2FloatEntrySet().iterator();
        while (entries.hasNext()) {
            Object2FloatMap.Entry<ItemLike> entry = entries.next();
            // Key and chance are read before the removal, which moves the map's slots.
            ItemLike key = entry.getKey();
            Item item = key.asItem();
            if (!snapshot.item(item)) {
                continue;
            }
            REMOVED.put(key, entry.getFloatValue());
            entries.remove();
            if (recorder != Recorder.NONE) {
                BuiltInRegistries.ITEM.getResourceKey(item)
                        .ifPresent(id -> recorder.loaderRemoval(TABLE, Keys.name(id)));
            }
        }
    }

    /** Puts every removed entry back; idempotent. Lifecycle.stopped calls it first. */
    public static synchronized void restore() {
        restoreLocked();
    }

    // NeoForge marks COMPOSTABLES deprecated, not for removal, in favour of its data map.
    @SuppressWarnings("deprecation")
    private static void restoreLocked() {
        ComposterBlock.COMPOSTABLES.putAll(REMOVED);
        REMOVED.clear();
    }
}
