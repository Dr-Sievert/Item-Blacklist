package net.sievert.item_blacklist.line;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * An ingredient's items from 1.21.4 to 1.21.11: items(), a Stream of item holders, which
 * NeoForge's patch fills from a custom ingredient and Fabric API's custom ingredients override.
 */
public final class IngredientsSince1_21_4 implements Ingredients.Backend {
    /**
     * items() is marked deprecated, not for removal, and no other public call lists an
     * ingredient's items, so the deprecation note is silenced here.
     */
    @Override
    @SuppressWarnings("deprecation")
    public List<Item> alternatives(Ingredient ingredient) {
        return ingredient.items().map(Holder::value).distinct().toList();
    }
}
