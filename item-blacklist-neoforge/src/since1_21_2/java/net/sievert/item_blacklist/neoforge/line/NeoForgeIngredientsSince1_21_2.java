package net.sievert.item_blacklist.neoforge.line;

import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * A NeoForge ingredient's items from 1.21.2 to 1.21.11: the custom ingredient's own items(), or
 * the plain ingredient's values. Both keep one descriptor through the window, while the
 * ingredient's own items() turned from a List into a Stream at 1.21.4; getValues() throws for
 * a custom ingredient, hence the branch first.
 */
public final class NeoForgeIngredientsSince1_21_2 implements NeoForgeIngredients.Backend {
    @Override
    public List<Item> alternatives(Ingredient ingredient) {
        Stream<Holder<Item>> holders = ingredient.isCustom()
                ? ingredient.getCustomIngredient().items()
                : ingredient.getValues().stream();
        return holders.map(Holder::value).distinct().toList();
    }
}
