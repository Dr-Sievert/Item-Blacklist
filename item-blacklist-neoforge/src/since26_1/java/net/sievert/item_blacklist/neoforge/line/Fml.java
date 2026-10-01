package net.sievert.item_blacklist.neoforge.line;

import net.neoforged.fml.loading.FMLLoader;

/**
 * FML on this line, whose releases all run FML 10 or later (NeoForge 21.9 on, and 26.x): the
 * loader is an instance. FML 4 to 9 (NeoForge 21.1 to 21.8) have a static method instead, so
 * shared code asks this class, never FMLLoader.
 */
public final class Fml {
    private Fml() {
    }

    /** Whether FML runs a production game, not a dev run from the IDE or Gradle. */
    public static boolean isProduction() {
        return FMLLoader.getCurrent().isProduction();
    }
}
