package net.sievert.item_blacklist.filters;

import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.line.Keys;
import net.sievert.item_blacklist.report.Recorder;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * The table rules of the loader filters, pure functions of a snapshot handed in: the fuel
 * builder hook and NeoForge's data-map listener fetch the snapshot and call these, so the
 * rules never ask Blacklist and the filters_ scenarios test them on built input on both
 * loaders.
 */
public final class LoaderFilterRules {
    /**
     * The fuel tables' name, for stripItems' table argument. No record carries it: the fuel
     * builder also runs on clients, which must not write into a server's report, so it passes
     * Recorder.NONE.
     */
    public static final String FUEL = "fuel";

    /** NeoForge's compost data map, as the report names it. */
    public static final String NEOFORGE_COMPOSTABLES = "neoforge:compostables";

    /** NeoForge's furnace fuel data map, as the report names it. */
    public static final String NEOFORGE_FURNACE_FUELS = "neoforge:furnace_fuels";

    private LoaderFilterRules() {
    }

    /**
     * Removes from a table keyed by item every item the snapshot blacklists, explicit and
     * tag-derived alike, and records each under {@code table}; how many went. The table is
     * edited in place: a fuel builder's map, which the game hands on without copying it.
     */
    public static int stripItems(Map<Item, ?> entries, BlacklistSnapshot snapshot, String table,
            Recorder recorder) {
        // Also keeps removeIf away from an immutable empty map.
        if (snapshot.isEmpty() || entries.isEmpty()) {
            return 0;
        }
        int[] removed = {0};
        entries.keySet().removeIf(item -> {
            if (!snapshot.item(item)) {
                return false;
            }
            removed[0]++;
            if (recorder != Recorder.NONE) {
                BuiltInRegistries.ITEM.getResourceKey(item)
                        .ifPresent(key -> recorder.loaderRemoval(table, Keys.name(key)));
            }
            return true;
        });
        return removed[0];
    }

    /**
     * The same for a table keyed by item key: a NeoForge data map, whose getDataMap returns
     * the registry's live map, so the removal is what every later reader sees.
     */
    public static int stripKeys(Map<ResourceKey<Item>, ?> entries, BlacklistSnapshot snapshot,
            String table, Recorder recorder) {
        // Also keeps removeIf away from Map.of(), which getDataMap returns for an absent map.
        if (snapshot.isEmpty() || entries.isEmpty()) {
            return 0;
        }
        int[] removed = {0};
        entries.keySet().removeIf(key -> {
            if (!snapshot.item(key)) {
                return false;
            }
            removed[0]++;
            if (recorder != Recorder.NONE) {
                recorder.loaderRemoval(table, Keys.name(key));
            }
            return true;
        });
        return removed[0];
    }
}
