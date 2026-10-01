package net.sievert.item_blacklist.mixin;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntComparators;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

@Mixin(Ingredient.class)
public abstract class IngredientMixin {

    @Inject(
            method = "getItems",
            at = @At("RETURN"),
            cancellable = true
    )
    private void item_blacklist$filterBlacklistedAlternatives(
            CallbackInfoReturnable<ItemStack[]> cir
    ) {
        if (BlacklistManager.isEmpty()) {
            return;
        }

        ItemStack[] items = cir.getReturnValue();

        if (items.length == 0) {
            return;
        }

        ItemStack[] filtered = Arrays.stream(items)
                .filter(stack -> !BlacklistManager.isBlacklisted(stack))
                .toArray(ItemStack[]::new);

        if (filtered.length != items.length) {
            cir.setReturnValue(filtered);
        }
    }

    @Inject(
            method = "getStackingIds",
            at = @At("HEAD"),
            cancellable = true
    )
    private void item_blacklist$filterBlacklistedStackingIds(
            CallbackInfoReturnable<IntList> cir
    ) {
        if (BlacklistManager.isEmpty()) {
            return;
        }

        ItemStack[] items =
                ((Ingredient) (Object) this).getItems();

        IntList stackingIds =
                new IntArrayList(items.length);

        for (ItemStack item : items) {
            stackingIds.add(
                    StackedContents.getStackingIndex(item)
            );
        }

        stackingIds.sort(
                IntComparators.NATURAL_COMPARATOR
        );

        cir.setReturnValue(stackingIds);
    }
}
