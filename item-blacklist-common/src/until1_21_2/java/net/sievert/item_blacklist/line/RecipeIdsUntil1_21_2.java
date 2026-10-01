package net.sievert.item_blacklist.line;

import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Recipe ids below 1.21.2: the holder's id is the id class itself, whose text is the id. Named
 * only here, in a window that holds 1.21.1 alone.
 */
public final class RecipeIdsUntil1_21_2 implements RecipeIds.Backend {
    @Override
    public String name(RecipeHolder<?> holder) {
        return holder.id().toString();
    }
}
