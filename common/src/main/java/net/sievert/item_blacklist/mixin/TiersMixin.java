package net.sievert.item_blacklist.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

@Mixin(Tiers.class)
public abstract class TiersMixin {

    @Inject(
            method = "getRepairIngredient",
            at = @At("RETURN"),
            cancellable = true
    )
    private void item_blacklist$filterRepairIngredient(
            CallbackInfoReturnable<Ingredient> cir
    ) {
        Ingredient ingredient =
                cir.getReturnValue();

        ItemStack[] filteredItems =
                Arrays.stream(ingredient.getItems())
                        .filter(stack ->
                                !BlacklistManager.isBlacklisted(stack)
                        )
                        .toArray(ItemStack[]::new);

        cir.setReturnValue(
                Ingredient.of(filteredItems)
        );
    }
}