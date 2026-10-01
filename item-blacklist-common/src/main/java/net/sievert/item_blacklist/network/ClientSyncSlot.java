package net.sievert.item_blacklist.network;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Where server-loaded code hands a received payload to the client side without naming a
 * client class: NeoForge's payload handler is registered in the @Mod class, which a dedicated
 * server loads too, so it calls {@link #deliver} here, and only a physical client installs the
 * receiver behind it. Names no client class, not even in a lambda.
 */
public final class ClientSyncSlot {
    private static volatile Consumer<BlacklistSyncPayload> receiver;

    private ClientSyncSlot() {
    }

    /** Called by ClientSync.install() on physical clients only. */
    public static void install(Consumer<BlacklistSyncPayload> target) {
        receiver = Objects.requireNonNull(target, "target");
    }

    /** NeoForge's payload handler: hands the payload on; does nothing until installed. */
    public static void deliver(BlacklistSyncPayload payload) {
        Consumer<BlacklistSyncPayload> current = receiver;
        if (current != null) {
            current.accept(payload);
        }
    }

    /**
     * Whether a client installed its receiver: true on a physical client only, so JeiHook never
     * reaches JEI on a server that has JEI in its mods folder.
     */
    public static boolean installed() {
        return receiver != null;
    }
}
