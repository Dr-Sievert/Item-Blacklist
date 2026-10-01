package net.sievert.item_blacklist.blacklist;

import net.sievert.item_blacklist.ItemBlacklistMod;
import net.sievert.item_blacklist.brewing.BrewingFilter;
import net.sievert.item_blacklist.config.BlacklistConfig;
import net.sievert.item_blacklist.filters.CompostFilter;
import net.sievert.item_blacklist.line.Keys;
import net.sievert.item_blacklist.log.Log;
import net.sievert.item_blacklist.log.LogTag;
import net.sievert.item_blacklist.platform.Services;
import net.sievert.item_blacklist.report.Recorder;
import net.sievert.item_blacklist.report.ReportRenderer;
import net.sievert.item_blacklist.report.ReportSnapshot;
import java.util.List;
import java.util.concurrent.CompletionException;
import net.minecraft.server.MinecraftServer;

/**
 * Every moment of a server's life the mod acts on, the same on every release and loader:
 * resolve at "starting", one blocking forced reload at "started", the static filters and the
 * report after every reload, the drop at "stopped". Called by ItemBlacklistMod (the loaders'
 * listeners) and MinecraftServerMixin only. Each hook is idempotent and keyed by the server's
 * identity; each step catches what fails inside it and logs ERROR with its name, so a fault
 * of the port fails the run in the log instead of stopping the server.
 */
public final class Lifecycle {
    private Lifecycle() {
    }

    /**
     * The server is starting, before its levels load: reads the config, resolves it against
     * the server's registries, opens the state and runs the static filters. From here every
     * hook that reads the snapshot is live, so the spawn area's generation is filtered too.
     */
    public static void starting(MinecraftServer server) {
        ServerState old = Blacklist.serverState();
        if (old != null) {
            if (old.server() == server) {
                return;
            }
            // The stop hook of the previous server never came: stop it now.
            Log.warn(LogTag.INIT, "A state of another server was still open; it is stopped now");
            stopped(old.server());
        }
        Blacklist.clearRemote();

        BlacklistConfig[] config = {BlacklistConfig.EMPTY};
        step("config", () -> config[0] = ItemBlacklistMod.loadForServer());

        Resolver.Result[] result =
                {new Resolver.Result(BlacklistSnapshot.EMPTY, List.of(), List.of())};
        step("resolve", () -> result[0] = Resolver.resolve(config[0], server.registryAccess()));
        for (String unknown : result[0].unknown()) {
            Log.warn(LogTag.CONFIG, "Unknown {}, ignored", unknown);
        }
        // A tag stays in the blacklist (a datapack may define it at the next reload), so its
        // line must not say "ignored".
        for (String tag : result[0].undefinedTags()) {
            Log.warn(LogTag.CONFIG, "Undefined {}, kept", tag);
        }
        BlacklistSnapshot snapshot = result[0].snapshot();
        Log.info(LogTag.INIT, "Resolved {} items ({} from {} tags), {} potions, {} enchantments",
                snapshot.items().size(), snapshot.items().size() - snapshot.explicitItems().size(),
                snapshot.itemTags().size(), snapshot.potions().size(),
                snapshot.enchantments().size());

        ServerState state = Blacklist.start(server, config[0], snapshot);
        staticFilters(server, state);
    }

    /**
     * The server has started, outside any task and before its first tick: the scaffold's line
     * and MixinAudit have run (ItemBlacklistMod). Once per state, one forced reload runs here
     * and blocks, since reloadResources waits for itself on the server thread, so the tags,
     * recipes and loot are filtered and reported before any connection is ticked; then the
     * "Filters active" line. A missed "starting" is made up for first.
     */
    public static void started(MinecraftServer server) {
        ServerState state = Blacklist.serverState();
        if (state == null || state.server() != server) {
            starting(server);
            state = Blacklist.serverState();
            if (state == null || state.server() != server) {
                return;
            }
        }
        if (state.startReloadDone() || state.startReloadRunning()) {
            return;
        }
        state.beginStartReload();
        long started = System.nanoTime();
        step("start reload",
                () -> server.reloadResources(server.getPackRepository().getSelectedIds()));
        long millis = (System.nanoTime() - started) / 1_000_000L;
        if (!state.startReloadDone()) {
            Log.error(LogTag.INIT, "The start reload did not report back; filtering is incomplete");
            state.finishStartReload(false);
        }
        if (state.startReloadSucceeded()) {
            BlacklistSnapshot snapshot = state.snapshot();
            Log.info(LogTag.INIT,
                    "Filters active: {} items, {} potions, {} enchantments;"
                            + " start reload took {} ms",
                    snapshot.items().size(), snapshot.potions().size(),
                    snapshot.enchantments().size(), millis);
        }
    }

    /**
     * A reloadResources call of this server completed, the start reload included: called by
     * MinecraftServerMixin on the server thread. Counts it, runs the static filters again on
     * success, records the configured entries as the report's headers, flushes the report and
     * logs it; a failed start reload is an ERROR, a later one a WARN.
     */
    public static void afterReload(MinecraftServer server, Throwable failureOrNull) {
        ServerState state = Blacklist.serverState();
        if (state == null || state.server() != server) {
            return;
        }
        state.reloadCompleted();
        boolean start = state.startReloadRunning();
        boolean ok = failureOrNull == null;
        if (start) {
            state.finishStartReload(ok);
        }
        ReportSnapshot flushed;
        try {
            if (ok) {
                staticFilters(server, state);
            }
            step("report headers", () -> {
                Recorder recorder = state.report();
                BlacklistSnapshot snapshot = state.snapshot();
                snapshot.explicitItems().forEach(key -> recorder.blacklistedItem(Keys.name(key)));
                snapshot.potions().forEach(key -> recorder.blacklistedPotion(Keys.name(key)));
                snapshot.explicitEnchantments()
                        .forEach(key -> recorder.blacklistedEnchantment(Keys.name(key)));
            });
        } finally {
            // Copied and reset under the report's lock, also when an Error left a step above,
            // so no record of this reload carries into the next report; logged outside it.
            flushed = state.report().flush(!ok);
        }
        step("report", () -> {
            for (ReportRenderer.Line line
                    : ReportRenderer.lines(flushed, state.config().detailedLog())) {
                Log.info(line.tag(), "{}", line.message());
            }
        });
        if (!ok) {
            Throwable cause = failureOrNull instanceof CompletionException completion
                    && completion.getCause() != null ? completion.getCause() : failureOrNull;
            if (start) {
                Log.error(LogTag.INIT, "Start reload failed: {}", cause.toString());
            } else {
                Log.warn(LogTag.INIT,
                        "Reload failed, the previous filtering of recipes and loot stays: {}",
                        cause.toString());
            }
        }
    }

    /**
     * The server stopped: gives the compost table back and drops the state with its slots, so
     * nothing of this world reaches the next one in the same JVM.
     */
    public static void stopped(MinecraftServer server) {
        step("compost restore", CompostFilter::restore);
        Blacklist.stop(server);
    }

    /**
     * The tables no hook at the point of use covers, each refilled from kept originals, so a
     * second call with the same snapshot changes nothing and a smaller blacklist gives entries
     * back: the vanilla brewing mixes, the loader's own brewing recipes, the compost table.
     */
    private static void staticFilters(MinecraftServer server, ServerState state) {
        BlacklistSnapshot snapshot = state.snapshot();
        Recorder recorder = state.report();
        step("brewing filter",
                () -> BrewingFilter.apply(server.potionBrewing(), snapshot, recorder));
        step("loader brewing filter", () -> Services.PLATFORM.filterLoaderBrewing(
                server.potionBrewing(), snapshot, recorder));
        step("compost filter", () -> CompostFilter.apply(snapshot, recorder));
    }

    /** Runs one step; a RuntimeException is logged at ERROR with the step's name. */
    private static void step(String name, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException e) {
            Log.error(LogTag.INIT, "Step '" + name + "' failed", e);
        }
    }
}
