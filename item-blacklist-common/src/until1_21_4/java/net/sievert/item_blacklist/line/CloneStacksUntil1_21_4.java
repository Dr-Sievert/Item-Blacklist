package net.sievert.item_blacklist.line;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/** Pick-block stacks below 1.21.4: the block's own getCloneItemStack(level, pos, state). */
public final class CloneStacksUntil1_21_4 implements CloneStacks.Backend {
    /**
     * NeoForge marks this vanilla call deprecated, not for removal, in favour of its
     * player-aware form, which needs a player the callers do not have.
     */
    @Override
    @SuppressWarnings("deprecation")
    public ItemStack of(LevelReader level, BlockPos pos, BlockState state) {
        return state.getBlock().getCloneItemStack(level, pos, state);
    }
}
