package net.sievert.item_blacklist.integration.jei;

import net.sievert.item_blacklist.log.Log;
import net.sievert.item_blacklist.log.LogTag;
import net.sievert.item_blacklist.network.ClientSyncSlot;
import net.sievert.item_blacklist.platform.Services;

/**
 * The guard in front of every JEI call. It names nothing of JEI, not even in a catch clause:
 * the JVM may load what a class names when it verifies it, so a JEI type here would fail every
 * server and client without JEI. Only after the check does it reach {@link JeiBridge}, which
 * holds every use of JEI's API. Called by the client sync and by the jei scenarios.
 */
public final class JeiHook {
    private JeiHook() {
    }

    /** Asks JEI to hide what the current blacklist hides; returns at once without JEI. */
    public static void refilter() {
        if (!active()) {
            return;
        }
        try {
            JeiBridge.refilter();
        } catch (RuntimeException | LinkageError e) {
            // A JEI build whose API differs must cost the filtering of JEI, not the game.
            Log.warn(LogTag.RECIPE, "JEI refilter failed: {}", e.toString());
        }
    }

    /** Drops what the bridge holds of JEI's runtime, at a disconnect; returns without JEI. */
    public static void clearRuntime() {
        if (!active()) {
            return;
        }
        try {
            JeiBridge.clearRuntime();
        } catch (RuntimeException | LinkageError e) {
            Log.warn(LogTag.RECIPE, "Clearing JEI's runtime failed: {}", e.toString());
        }
    }

    /**
     * Whether JEI is there to call: a physical client (the client sync installed its slot) with
     * JEI loaded, so a server with JEI in its mods folder never calls the bridge.
     */
    private static boolean active() {
        return ClientSyncSlot.installed() && Services.PLATFORM.isModLoaded("jei");
    }
}
