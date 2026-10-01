package net.sievert.item_blacklist.integration.jei.line;

import net.sievert.item_blacklist.line.Backends;
import java.util.Collection;
import java.util.List;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import mezz.jei.api.runtime.IJeiRuntime;

/**
 * JEI's brewing and anvil categories on the 1.21.x line. RecipeTypes.BREWING and ANVIL and the
 * recipe manager's parameters are RecipeType in JEI 19 (1.21.1) and IRecipeType from JEI 20
 * (1.21.4), so a class compiled at the floor would fail with NoSuchFieldError from 1.21.4 on:
 * this facade hands the calls to {@code JeiRecipesUntil1_21_4} (src/until1_21_4) or
 * {@code JeiRecipesSince1_21_4} (src/since1_21_4), one source text. Called by JeiBridge only,
 * so the backend is picked on a client with JEI only. CLAUDE.md, "A name renamed or removed
 * inside a line".
 */
public final class JeiRecipes {
    /** The calls that differ; each backend implements them for its window. */
    public interface Backend {
        /** The category's brewing recipes; with includeHidden also those JEI hides. */
        List<IJeiBrewingRecipe> brewing(IJeiRuntime runtime, boolean includeHidden);

        /** The category's anvil recipes; with includeHidden also those JEI hides. */
        List<IJeiAnvilRecipe> anvil(IJeiRuntime runtime, boolean includeHidden);

        /** Hides these brewing recipes in JEI. */
        void hideBrewing(IJeiRuntime runtime, Collection<IJeiBrewingRecipe> recipes);

        /** Shows these brewing recipes in JEI again. */
        void unhideBrewing(IJeiRuntime runtime, Collection<IJeiBrewingRecipe> recipes);

        /** Hides these anvil recipes in JEI. */
        void hideAnvil(IJeiRuntime runtime, Collection<IJeiAnvilRecipe> recipes);

        /** Shows these anvil recipes in JEI again. */
        void unhideAnvil(IJeiRuntime runtime, Collection<IJeiAnvilRecipe> recipes);
    }

    private static final Backend BACKEND = Backends.pick("JeiRecipes", Backend.class,
            Backends.until("1.21.4",
                    "net.sievert.item_blacklist.integration.jei.line.JeiRecipesUntil1_21_4"),
            Backends.since("1.21.4",
                    "net.sievert.item_blacklist.integration.jei.line.JeiRecipesSince1_21_4"));

    private JeiRecipes() {
    }

    /** Every brewing recipe, hidden ones included. */
    public static List<IJeiBrewingRecipe> brewing(IJeiRuntime runtime) {
        return BACKEND.brewing(runtime, true);
    }

    /** The brewing recipes JEI shows now. */
    public static List<IJeiBrewingRecipe> visibleBrewing(IJeiRuntime runtime) {
        return BACKEND.brewing(runtime, false);
    }

    /** Every anvil recipe, hidden ones included. */
    public static List<IJeiAnvilRecipe> anvil(IJeiRuntime runtime) {
        return BACKEND.anvil(runtime, true);
    }

    /** The anvil recipes JEI shows now. */
    public static List<IJeiAnvilRecipe> visibleAnvil(IJeiRuntime runtime) {
        return BACKEND.anvil(runtime, false);
    }

    /** Hides these brewing recipes in JEI. */
    public static void hideBrewing(IJeiRuntime runtime, Collection<IJeiBrewingRecipe> recipes) {
        BACKEND.hideBrewing(runtime, recipes);
    }

    /** Shows these brewing recipes in JEI again. */
    public static void unhideBrewing(IJeiRuntime runtime, Collection<IJeiBrewingRecipe> recipes) {
        BACKEND.unhideBrewing(runtime, recipes);
    }

    /** Hides these anvil recipes in JEI. */
    public static void hideAnvil(IJeiRuntime runtime, Collection<IJeiAnvilRecipe> recipes) {
        BACKEND.hideAnvil(runtime, recipes);
    }

    /** Shows these anvil recipes in JEI again. */
    public static void unhideAnvil(IJeiRuntime runtime, Collection<IJeiAnvilRecipe> recipes) {
        BACKEND.unhideAnvil(runtime, recipes);
    }
}
