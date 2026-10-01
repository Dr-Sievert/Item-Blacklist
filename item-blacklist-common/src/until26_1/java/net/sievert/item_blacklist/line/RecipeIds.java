package net.sievert.item_blacklist.line;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * A recipe's id as text on the 1.21.x line. RecipeHolder.id() returns the id class on 1.21.1
 * and a ResourceKey from 1.21.2, so this facade hands the call to {@code RecipeIdsUntil1_21_2}
 * (src/until1_21_2) or {@code RecipeIdsSince1_21_2} (src/since1_21_2). Shared code and the
 * scenarios name recipes here. CLAUDE.md, "A name renamed or removed inside a line".
 */
public final class RecipeIds {
    /** The call that differs; each backend implements it for its window. */
    public interface Backend {
        /** "namespace:path" of the holder's id, in the window's id type. */
        String name(RecipeHolder<?> holder);
    }

    private static final Backend BACKEND = Backends.pick("RecipeIds", Backend.class,
            Backends.until("1.21.2", "net.sievert.item_blacklist.line.RecipeIdsUntil1_21_2"),
            Backends.since("1.21.2", "net.sievert.item_blacklist.line.RecipeIdsSince1_21_2"));

    private RecipeIds() {
    }

    /** "namespace:path" of the recipe: the subject of a recipe record in the report. */
    public static String name(RecipeHolder<?> holder) {
        return BACKEND.name(holder);
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
