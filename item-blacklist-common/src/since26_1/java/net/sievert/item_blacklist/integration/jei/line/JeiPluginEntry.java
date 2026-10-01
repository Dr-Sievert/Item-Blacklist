package net.sievert.item_blacklist.integration.jei.line;

import mezz.jei.api.IModPlugin;

/**
 * Fabric's jei_mod_plugin entrypoint on the 26.x line: the line has one plugin class, so no
 * pick is needed. The 1.21.x twin picks between two plugin classes, since the id class changed
 * at 1.21.11 there. NeoForge never loads this class; its JEI scans the plugin class.
 */
public final class JeiPluginEntry {
    /** The plugin, created once when JEI asks for the entrypoint. */
    public static final IModPlugin PLUGIN = new ItemBlacklistJeiPlugin();

    private JeiPluginEntry() {
    }
}
