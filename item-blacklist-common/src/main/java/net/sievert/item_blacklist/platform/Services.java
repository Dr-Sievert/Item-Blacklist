package net.sievert.item_blacklist.platform;

import java.util.ServiceLoader;

/** Finds the loader's implementations through META-INF/services. */
public final class Services {
    /** The running loader's Platform, found once when this class loads. */
    public static final Platform PLATFORM = load(Platform.class);

    private Services() {
    }

    /** The first implementation of the type the loader's jar lists; throws without one. */
    public static <T> T load(Class<T> type) {
        return ServiceLoader.load(type, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No service for " + type.getName()));
    }
}
