package net.sievert.item_blacklist.line;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * An ingredient's items on 1.21.1: getItems(), an array of stacks, which NeoForge's patch fills
 * from a custom ingredient and Fabric API's custom ingredients override.
 */
public final class IngredientsUntil1_21_2 implements Ingredients.Backend {
    @Override
    public List<Item> alternatives(Ingredient ingredient) {
        Set<Item> items = new LinkedHashSet<>();
        for (ItemStack stack : ingredient.getItems()) {
            if (!stack.isEmpty()) {
                items.add(stack.getItem());
            }
        }
        return List.copyOf(items);
    }
}
