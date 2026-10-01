package net.sievert.item_blacklist.line;

import net.minecraft.world.InteractionResult;

/**
 * The "consumed, no swing" interaction result on the 26.x line: InteractionResult.CONSUME, one
 * declaration on every release of the line. The 1.21.x twin is a facade, since the constant's
 * type changed at 1.21.2.
 */
public final class Interactions {
    private Interactions() {
    }

    /**
     * InteractionResult.CONSUME: the action counts as done and nothing swings. Compare it by
     * identity only, as on the 1.21.x line.
     */
    public static InteractionResult consume() {
        return InteractionResult.CONSUME;
    }
}
