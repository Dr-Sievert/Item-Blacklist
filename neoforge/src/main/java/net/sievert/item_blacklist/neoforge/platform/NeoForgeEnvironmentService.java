package net.sievert.item_blacklist.neoforge.platform;

import net.neoforged.fml.loading.FMLPaths;
import net.sievert.item_blacklist.platform.environment.EnvironmentService;

import java.nio.file.Path;

public final class NeoForgeEnvironmentService implements EnvironmentService {

    @Override
    public Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }
}