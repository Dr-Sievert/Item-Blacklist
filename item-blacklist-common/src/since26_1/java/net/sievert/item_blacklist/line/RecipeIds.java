package net.sievert.item_blacklist.line;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * A recipe's id as text on the 26.x line: the holder's id is a ResourceKey on every release of
 * it, named through Keys. The 1.21.x twin is a facade, since the id's type changed at 1.21.2.
 */
public final class RecipeIds {
    private RecipeIds() {
    }

    /** "namespace:path" of the recipe: the subject of a recipe record in the report. */
    public static String name(RecipeHolder<?> holder) {
        return Keys.name(holder.id());
    }

    /** The ids of every recipe the manager holds, sorted; unmodifiable. */
    public static Set<String> names(RecipeManager manager) {
        Set<String> names = new TreeSet<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            names.add(name(holder));
        }
        return Collections.unmodifiableSet(names);
    }
}
