package net.sievert.item_blacklist.fabric.gametest;

import net.sievert.item_blacklist.gametest.AnnotationEra;

/**
 * The annotations era on Fabric (up to 1.21.4): the scenarios go into vanilla's
 * GameTestRegistry, which fabric-gametest-api-v1 2.x's GameTest server runs in full under
 * -Dfabric-api.gametest (it has no filter).
 */
public final class FabricGameTestsUntil1_21_5 implements GameTestRegistration.Backend {
    @Override
    public void register() {
        AnnotationEra.register();
    }
}
