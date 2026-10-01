package net.sievert.item_blacklist.line;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The items an ingredient accepts, unfiltered, on the 1.21.x line. The call changes twice
 * inside the line: getItems() (stacks) on 1.21.1, items() as a List of holders on 1.21.2 and
 * 1.21.3, as a Stream from 1.21.4. So this facade hands it to {@code IngredientsUntil1_21_2}
 * (src/until1_21_2), {@code IngredientsSince1_21_2Until1_21_4} (src/since1_21_2-until1_21_4) or
 * {@code IngredientsSince1_21_4} (src/since1_21_4). Each is a virtual call on the ingredient,
 * so Fabric's custom ingredients answer through their override and NeoForge's patched
 * Ingredient reads its custom ingredient. Read only after tags are bound. CLAUDE.md, "A name
 * renamed or removed inside a line".
 */
public final class Ingredients {
    /** The call that differs; each backend implements it for its window. */
    public interface Backend {
        /** Distinct items in the ingredient's own order. */
        List<Item> alternatives(Ingredient ingredient);
    }

    private static final Backend BACKEND = Backends.pick("Ingredients", Backend.class,
            Backends.until("1.21.2", "net.sievert.item_blacklist.line.IngredientsUntil1_21_2"),
            Backends.between("1.21.2", "1.21.4",
                    "net.sievert.item_blacklist.line.IngredientsSince1_21_2Until1_21_4"),
            Backends.since("1.21.4", "net.sievert.item_blacklist.line.IngredientsSince1_21_4"));

    private Ingredients() {
    }

    /**
     * Distinct items in the ingredient's own order; custom ingredients answer through their
     * override. Nothing of the mod filters this read, so it sees every real alternative.
     */
    public static List<Item> alternatives(Ingredient ingredient) {
        return BACKEND.alternatives(ingredient);
    }
}
