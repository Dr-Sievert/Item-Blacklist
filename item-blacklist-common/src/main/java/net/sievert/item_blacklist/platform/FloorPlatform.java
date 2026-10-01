package net.sievert.item_blacklist.platform;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The base of every loader's platform. A loader's platform supplies what only the loader
 * knows: its name, its dev check and the running release. This class derives the rest from
 * them, so that no loader declares a list of its own: the capabilities are every
 * {@link Capability} whose window contains the running release, which the capability's
 * since and until already say; a declared list could only repeat that, or forget to.
 */
public abstract class FloorPlatform implements Platform {
    private volatile Set<Capability> capabilities;

    /**
     * Computed on first use, not in the constructor: the service lookup that creates the
     * platform can come before NeoForge's mod list, which its version probe reads.
     */
    @Override
    public final Set<Capability> capabilities() {
        Set<Capability> known = capabilities;
        if (known == null) {
            String release = minecraftVersion();
            Set<Capability> present = EnumSet.noneOf(Capability.class);
            for (Capability capability : Capability.values()) {
                if (capability.contains(release)) {
                    present.add(capability);
                }
            }
            known = Collections.unmodifiableSet(present);
            capabilities = known;
        }
        return known;
    }
}
