package net.sievert.item_blacklist.line;

import net.minecraft.world.InteractionResult;

/** CONSUME below 1.21.2, where it is a constant of the enum InteractionResult. */
public final class InteractionsUntil1_21_2 implements Interactions.Backend {
    /** The enum constant CONSUME. */
    @Override
    public InteractionResult consume() {
        return InteractionResult.CONSUME;
    }
}
