package net.sievert.item_blacklist.fabric.platform;

import net.fabricmc.loader.api.FabricLoader;
import net.sievert.item_blacklist.platform.environment.EnvironmentService;

import java.nio.file.Path;

public final class FabricEnvironmentService implements EnvironmentService {

    @Override
    public Path getConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }
}