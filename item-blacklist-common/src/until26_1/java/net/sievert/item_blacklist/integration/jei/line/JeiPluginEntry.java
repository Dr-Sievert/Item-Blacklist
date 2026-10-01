package net.sievert.item_blacklist.integration.jei.line;

import net.sievert.item_blacklist.line.Backends;
import mezz.jei.api.IModPlugin;

/**
 * Fabric's jei_mod_plugin entrypoint on the 1.21.x line: one plugin, picked by class name for
 * the running release, so JEI on Fabric never sees the plugin class of the other window and no
 * uid is registered twice. NeoForge never loads this class; its JEI scans the plugin classes.
 * The initializer must not throw: Fabric JEI 21.4 (1.21.5) lets an Error of it crash the
 * client, and the pick's only failure, a class not in the jar, is ruled out by the scenario
 * jei_classes_shipped.
 */
public final class JeiPluginEntry {
    /** The plugin of the running release, created once when JEI asks for the entrypoint. */
    public static final IModPlugin PLUGIN = Backends.pick("JeiPlugin", IModPlugin.class,
            Backends.until("1.21.11", "net.sievert.item_blacklist.integration.jei.line."
                    + "ItemBlacklistJeiPluginUntil1_21_11"),
            Backends.since("1.21.11", "net.sievert.item_blacklist.integration.jei.line."
                    + "ItemBlacklistJeiPluginSince1_21_11"));

    private JeiPluginEntry() {
    }
}
