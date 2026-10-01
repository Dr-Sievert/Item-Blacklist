package net.sievert.item_blacklist.line;

import net.minecraft.world.InteractionResult;

/**
 * CONSUME from 1.21.2 to 1.21.11, where it is a constant of type InteractionResult.Success in
 * the sealed interface InteractionResult, declared alike on every release of the window.
 */
public final class InteractionsSince1_21_2 implements Interactions.Backend {
    /** The constant CONSUME, typed InteractionResult.Success. */
    @Override
    public InteractionResult consume() {
        return InteractionResult.CONSUME;
    }
}
