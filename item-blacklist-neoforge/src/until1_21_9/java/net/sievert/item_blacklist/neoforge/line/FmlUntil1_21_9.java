package net.sievert.item_blacklist.neoforge.line;

import net.neoforged.fml.loading.FMLLoader;

/** The production check below 1.21.9 (FML 4 to 9): a static method. */
public final class FmlUntil1_21_9 implements Fml.Backend {
    @Override
    public boolean isProduction() {
        return FMLLoader.isProduction();
    }
}
