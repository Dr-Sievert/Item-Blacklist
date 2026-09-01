package net.sievert.item_blacklist.platform;

import net.sievert.item_blacklist.platform.environment.EnvironmentService;

public final class ItemBlacklistServices {

    private static EnvironmentService environment;

    private ItemBlacklistServices() {}

    public static void registerEnvironment(EnvironmentService service) {
        if (environment != null) {
            throw new IllegalStateException("Environment service is already registered");
        }

        environment = service;
    }

    public static EnvironmentService environment() {
        if (environment == null) {
            throw new IllegalStateException("Environment service has not been registered");
        }

        return environment;
    }
}