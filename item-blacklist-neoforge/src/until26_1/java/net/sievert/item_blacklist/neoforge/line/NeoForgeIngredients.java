package net.sievert.item_blacklist.neoforge.line;

import net.sievert.item_blacklist.line.Backends;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The items a NeoForge ingredient accepts, custom ingredients included, on the 1.21.x line:
 * getItems() on 1.21.1; from 1.21.2 the custom ingredient's own items() or the plain one's
 * values, read past NeoForge's cache of a custom ingredient's items, so a tag-based custom
 * ingredient is read fresh at every filter call. The facade hands the call to
 * {@code NeoForgeIngredientsUntil1_21_2} (src/until1_21_2) or
 * {@code NeoForgeIngredientsSince1_21_2} (src/since1_21_2). CLAUDE.md, "Vanilla backends and
 * loader backends".
 */
public final class NeoForgeIngredients {
    /** The call that differs; each backend implements it for its window. */
    public interface Backend {
        /** Distinct items in the ingredient's own order. */
        List<Item> alternatives(Ingredient ingredient);
    }

    private static final Backend BACKEND = Backends.pick("NeoForgeIngredients", Backend.class,
            Backends.until("1.21.2",
                    "net.sievert.item_blacklist.neoforge.line.NeoForgeIngredientsUntil1_21_2"),
            Backends.since("1.21.2",
                    "net.sievert.item_blacklist.neoforge.line.NeoForgeIngredientsSince1_21_2"));

    private NeoForgeIngredients() {
    }

    /** Distinct items in the ingredient's own order, a custom ingredient's included. */
    public static List<Item> alternatives(Ingredient ingredient) {
        return BACKEND.alternatives(ingredient);
    }
}
