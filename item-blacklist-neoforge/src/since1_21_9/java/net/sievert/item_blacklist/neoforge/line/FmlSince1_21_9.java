package net.sievert.item_blacklist.neoforge.line;

import net.neoforged.fml.loading.FMLLoader;

/** The production check from 1.21.9 (FML 10): the loader is an instance. */
public final class FmlSince1_21_9 implements Fml.Backend {
    @Override
    public boolean isProduction() {
        return FMLLoader.getCurrent().isProduction();
    }
}
