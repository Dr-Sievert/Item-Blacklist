package net.sievert.item_blacklist.recipes;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * What RecipeManagerMixinUntil1_21_2 adds to the 1.21.1 RecipeManager, for the 1.21.1 pass: the
 * tag ids the last apply read, the manager's registries and a replace that keeps the error
 * flag. It sits outside the mixin package, whose classes cannot be referenced, and is named
 * only by classes of this window.
 */
public interface RecipeManagerStateUntil1_21_2 {
    /**
     * The item tag ids each recipe's JSON named at the last apply, by recipe id text; the read
     * empties it, so a pass never judges with the names of an older load.
     */
    Map<String, Set<String>> item_blacklist$takeTagNames();

    /** The registries the manager resolves results with (its "registries" field). */
    HolderLookup.Provider item_blacklist$registries();

    /**
     * replaceRecipes(kept), then the manager's hadErrorsLoading() as it was before: the vanilla
     * replace clears the flag, and a removal is no reason to hide a datapack's parse errors.
     */
    void item_blacklist$replaceKeepingErrors(List<RecipeHolder<?>> kept);
}
