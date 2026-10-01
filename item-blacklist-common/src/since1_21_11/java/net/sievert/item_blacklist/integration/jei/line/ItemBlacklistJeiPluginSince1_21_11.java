package net.sievert.item_blacklist.integration.jei.line;

import net.sievert.item_blacklist.ItemBlacklistMod;
import net.sievert.item_blacklist.integration.jei.JeiBridge;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.Identifier;

/**
 * JEI's plugin from 1.21.11 on the 1.21.x line (JEI 27, whose getPluginUid returns
 * Identifier). It names the id class because the override's return type is the id class. On
 * NeoForge JEI instantiates every annotated plugin class of the jar: below 1.21.11 this one
 * fails in its static field with NoClassDefFoundError, which JEI's finder catches and logs.
 * So the uid stays a static field and nothing else runs in the initializer or the
 * constructor. On Fabric JEI reaches it through JeiPluginEntry only, on the releases of its
 * window. Loaded by JEI only.
 */
@JeiPlugin
public final class ItemBlacklistJeiPluginSince1_21_11 implements IModPlugin {
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
