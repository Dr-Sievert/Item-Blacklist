package net.sievert.item_blacklist.line;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block's pick-block stack on the 1.21.x line. Block.getCloneItemStack(level, pos, state)
 * went at 1.21.4, where BlockState.getCloneItemStack(level, pos, includeData) took its place,
 * so this facade hands the call to {@code CloneStacksUntil1_21_4} (src/until1_21_4) or
 * {@code CloneStacksSince1_21_4} (src/since1_21_4). CLAUDE.md, "A name renamed or removed
 * inside a line".
 */
public final class CloneStacks {
    /** The call that differs; each backend implements it for its window. */
    public interface Backend {
        /** The block's pick-block stack without block-entity data. */
        ItemStack of(LevelReader level, BlockPos pos, BlockState state);
    }

    private static final Backend BACKEND = Backends.pick("CloneStacks", Backend.class,
            Backends.until("1.21.4", "net.sievert.item_blacklist.line.CloneStacksUntil1_21_4"),
            Backends.since("1.21.4", "net.sievert.item_blacklist.line.CloneStacksSince1_21_4"));

    private CloneStacks() {
    }

    /** The block's pick-block stack without block-entity data: what a player would pick. */
    public static ItemStack of(LevelReader level, BlockPos pos, BlockState state) {
        return BACKEND.of(level, pos, state);
    }
}
