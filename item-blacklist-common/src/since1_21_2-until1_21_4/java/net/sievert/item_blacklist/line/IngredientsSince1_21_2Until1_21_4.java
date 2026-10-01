package net.sievert.item_blacklist.line;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * An ingredient's items on 1.21.2 and 1.21.3: items(), a List of item holders, which NeoForge's
 * patch fills from a custom ingredient and Fabric API's custom ingredients override. It became
 * a Stream at 1.21.4, hence the window's end.
 */
public final class IngredientsSince1_21_2Until1_21_4 implements Ingredients.Backend {
    @Override
    public List<Item> alternatives(Ingredient ingredient) {
        Set<Item> items = new LinkedHashSet<>();
        for (Holder<Item> holder : ingredient.items()) {
            items.add(holder.value());
        }
        return List.copyOf(items);
    }
}
