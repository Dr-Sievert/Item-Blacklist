package net.sievert.item_blacklist.platform;

import net.sievert.item_blacklist.platform.network.NetworkService;

public final class ItemBlacklistServices {

    private static NetworkService network;

    private ItemBlacklistServices() {}

    public static void registerNetwork(
            NetworkService service
    ) {
        if (network != null) {
            throw new IllegalStateException(
                    "Network service is already registered"
            );
        }

        network = service;
    }

    public static NetworkService network() {
        if (network == null) {
            throw new IllegalStateException(
                    "Network service has not been registered"
            );
        }

        return network;
    }
}