package net.sievert.item_blacklist.line;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pick-block stacks from 1.21.4: the state's getCloneItemStack(level, pos, includeData), here
 * without block-entity data, so the stack is the plain item the block is judged by.
 */
public final class CloneStacksSince1_21_4 implements CloneStacks.Backend {
    @Override
    public ItemStack of(LevelReader level, BlockPos pos, BlockState state) {
        return state.getCloneItemStack(level, pos, false);
    }
}
