package net.sievert.item_blacklist.line;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block's pick-block stack on the 26.x line: the state's getCloneItemStack(level, pos,
 * includeData), the one form every release of the line has. The 1.21.x twin is a facade,
 * since the call changed at 1.21.4.
 */
public final class CloneStacks {
    private CloneStacks() {
    }

    /** The block's pick-block stack without block-entity data: what a player would pick. */
    public static ItemStack of(LevelReader level, BlockPos pos, BlockState state) {
        return state.getCloneItemStack(level, pos, false);
    }
}
