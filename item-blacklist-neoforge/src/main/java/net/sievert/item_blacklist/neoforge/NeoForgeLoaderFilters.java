package net.sievert.item_blacklist.neoforge;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.ServerState;
import net.sievert.item_blacklist.blacklist.StackRules;
import net.sievert.item_blacklist.filters.LoaderFilterRules;
import net.sievert.item_blacklist.report.Recorder;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapsUpdatedEvent;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

/**
 * NeoForge's own tables the mod filters: its data maps (compostables, furnace fuels) after a
 * reload, and the burn time of a blacklisted stack. NeoForge's composter and furnace read these
 * instead of vanilla's tables, so the vanilla filters alone would leave them working. Called
 * once by the @Mod class, on both dists; listeners are added with addListener (no
 * EventBusSubscriber, whose bus argument is gone on later releases).
 */
public final class NeoForgeLoaderFilters {
    private NeoForgeLoaderFilters() {
    }

    /**
     * Adds the two listeners to NeoForge's game bus. The data-map listener runs at HIGHEST, so
     * every other listener of the same event, and the fuel table and the sync built after it,
     * see the filtered maps. The burn-time listener runs at LOWEST and receives cancelled
     * events: setBurnTime cancels the event, so a listener of another mod that gives a
     * blacklisted stack a burn time would otherwise hide the stack from this one.
     */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, DataMapsUpdatedEvent.class,
                NeoForgeLoaderFilters::onDataMapsUpdated);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true,
                FurnaceFuelBurnTimeEvent.class, NeoForgeLoaderFilters::onFurnaceFuelBurnTime);
    }

    /**
     * A server's data maps were loaded: blacklisted items leave neoforge:compostables and
     * neoforge:furnace_fuels, recorded into that server's report. Client syncs are left alone
     * (their maps are the server's, already filtered, and unmodifiable). The same event fires
     * at the world load, before any state of the new server exists and on the loader's sync
     * executor (the Render thread in singleplayer), so the listener takes the state once and
     * acts only on that server's own thread, as the tag hook does: a state left behind by a
     * server whose stop never came is not used.
     */
    static void onDataMapsUpdated(DataMapsUpdatedEvent event) {
        if (event.getCause() != DataMapsUpdatedEvent.UpdateCause.SERVER_RELOAD) {
            return;
        }
        ServerState state = Blacklist.serverState();
        if (state == null || !state.onServerThread()) {
            return;
        }
        BlacklistSnapshot snapshot = state.snapshot();
        if (snapshot.isEmpty()) {
            return;
        }
        // The report of the state found, which is Blacklist.recorder() for that server.
        Recorder recorder = state.report();
        event.ifRegistry(Registries.ITEM, registry -> {
            LoaderFilterRules.stripKeys(registry.getDataMap(NeoForgeDataMaps.COMPOSTABLES),
                    snapshot, LoaderFilterRules.NEOFORGE_COMPOSTABLES, recorder);
            LoaderFilterRules.stripKeys(registry.getDataMap(NeoForgeDataMaps.FURNACE_FUELS),
                    snapshot, LoaderFilterRules.NEOFORGE_FURNACE_FUELS, recorder);
        });
    }

    /**
     * Every burn-time query, on both sides: a blacklisted stack burns 0, judged by the stack
     * rule (its item, an enchantment, its potion). Reads the effective snapshot, since a
     * client's furnace screen asks too, and never records.
     */
    static void onFurnaceFuelBurnTime(FurnaceFuelBurnTimeEvent event) {
        BlacklistSnapshot snapshot = Blacklist.effective();
        if (snapshot.isEmpty()) {
            return;
        }
        if (StackRules.blacklisted(snapshot, event.getItemStack())) {
            // Also cancels the event, so later listeners keep the 0.
            event.setBurnTime(0);
        }
    }
}
