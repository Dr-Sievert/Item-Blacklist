package net.sievert.item_blacklist.neoforge.line;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * A NeoForge ingredient's items on 1.21.1: getItems(), which NeoForge's patch streams from the
 * custom ingredient's own getItems() when the ingredient is custom.
 */
public final class NeoForgeIngredientsUntil1_21_2 implements NeoForgeIngredients.Backend {
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
