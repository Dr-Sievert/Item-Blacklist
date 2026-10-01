package net.sievert.item_blacklist.line;

import net.minecraft.world.item.crafting.RecipeHolder;

/** Recipe ids from 1.21.2: the holder's id is a ResourceKey, named through Keys. */
public final class RecipeIdsSince1_21_2 implements RecipeIds.Backend {
    @Override
    public String name(RecipeHolder<?> holder) {
        return Keys.name(holder.id());
    }
}
