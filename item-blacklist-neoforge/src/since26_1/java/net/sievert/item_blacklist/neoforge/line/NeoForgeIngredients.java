package net.sievert.item_blacklist.neoforge.line;

import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The items a NeoForge ingredient accepts, custom ingredients included, on the 26.x line: the
 * custom ingredient's own items(), or the plain ingredient's values, the one form every
 * release of the line has. It reads past NeoForge's cache of a custom ingredient's items, so a
 * tag-based custom ingredient is read fresh at every filter call. The 1.21.x twin is a facade,
 * since getValues() changed its type at 1.21.2.
 */
public final class NeoForgeIngredients {
    private NeoForgeIngredients() {
    }

    /**
     * Distinct items in the ingredient's own order, a custom ingredient's included. getValues()
     * throws for a custom ingredient, hence the branch first.
     */
    public static List<Item> alternatives(Ingredient ingredient) {
        Stream<Holder<Item>> holders = ingredient.isCustom()
                ? ingredient.getCustomIngredient().items()
                : ingredient.getValues().stream();
        return holders.map(Holder::value).distinct().toList();
    }
}
