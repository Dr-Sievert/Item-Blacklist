package net.sievert.item_blacklist.line;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The items an ingredient accepts, unfiltered, on the 26.x line: items(), a Stream of item
 * holders, the one form every release of the line has. A virtual call on the ingredient, so
 * Fabric's custom ingredients answer through their override and NeoForge's patched Ingredient
 * reads its custom ingredient. Read only after tags are bound. The 1.21.x twin is a facade,
 * since the call changed at 1.21.2 and 1.21.4.
 */
public final class Ingredients {
    private Ingredients() {
    }

    /**
     * Distinct items in the ingredient's own order; custom ingredients answer through their
     * override. items() is marked deprecated, not for removal, and no other public call lists
     * an ingredient's items, so the deprecation note is silenced here.
     */
    @SuppressWarnings("deprecation")
    public static List<Item> alternatives(Ingredient ingredient) {
        return ingredient.items().map(Holder::value).distinct().toList();
    }
}
