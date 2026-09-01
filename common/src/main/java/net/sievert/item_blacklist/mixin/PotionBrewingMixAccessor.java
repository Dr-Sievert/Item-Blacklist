package net.sievert.item_blacklist.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.item.alchemy.PotionBrewing$Mix")
public interface PotionBrewingMixAccessor {

    @Accessor("from")
    Holder<?> item_blacklist$getFrom();

    @Accessor("ingredient")
    Ingredient item_blacklist$getIngredient();

    @Accessor("to")
    Holder<?> item_blacklist$getTo();
}