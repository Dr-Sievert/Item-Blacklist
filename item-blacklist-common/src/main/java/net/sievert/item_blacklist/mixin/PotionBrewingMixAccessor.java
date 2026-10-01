package net.sievert.item_blacklist.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads a brewing mix's endpoints and reagent. The record PotionBrewing.Mix is package-private
 * up to 1.21.11 and private on 26.x, so it is targeted by name; its three fields keep their
 * names and types on every release and loader. The endpoints are potion holders in a potion
 * mix and item holders in a container mix, hence {@code Holder<?>}.
 */
@Mixin(targets = "net.minecraft.world.item.alchemy.PotionBrewing$Mix")
public interface PotionBrewingMixAccessor {
    /** The input: a potion (potion mix) or a bottle item (container mix). */
    @Accessor("from")
    Holder<?> item_blacklist$from();

    /** The reagent put in the top slot. */
    @Accessor("ingredient")
    Ingredient item_blacklist$ingredient();

    /** The output, of the same kind as the input. */
    @Accessor("to")
    Holder<?> item_blacklist$to();
}
