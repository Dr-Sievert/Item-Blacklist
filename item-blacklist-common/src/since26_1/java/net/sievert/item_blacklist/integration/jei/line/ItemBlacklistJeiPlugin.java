package net.sievert.item_blacklist.integration.jei.line;

import net.sievert.item_blacklist.ItemBlacklistMod;
import net.sievert.item_blacklist.integration.jei.JeiBridge;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;

/**
 * JEI's plugin on the 26.x line (JEI 29, 30, whose getPluginUid returns Identifier). It names
 * the id class because the override's return type is the id class. The 26.x jar holds this
 * one plugin class, so NeoForge's scan and Fabric's JeiPluginEntry find the same class. The
 * uid stays a static field and nothing else runs in the initializer or the constructor, as in
 * the 1.21.x plugins. Loaded by JEI only.
 */
@JeiPlugin
public final class ItemBlacklistJeiPlugin implements IModPlugin {
    private static final Identifier UID =
            Identifier.fromNamespaceAndPath(ItemBlacklistMod.MOD_ID, "jei");

    /** The uid item_blacklist:jei; returns the static field, so it cannot throw. */
    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    /** JEI started: the bridge keeps the runtime and filters it. */
    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JeiBridge.setRuntime(jeiRuntime);
    }

    /** JEI stopped: the bridge forgets the runtime. */
    @Override
    public void onRuntimeUnavailable() {
        JeiBridge.clearRuntime();
    }
}
