package net.sievert.item_blacklist.mixin;

import mezz.jei.api.recipe.IFocus;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.sievert.item_blacklist.integration.jer.JerMixinUtil;

import java.util.List;

@Pseudo
@Mixin(
        targets = "jeresources.entry.DungeonEntry",
        remap = false
)
public abstract class JerDungeonEntryMixin {

    @Inject(
            method = "getItemStacks(Lmezz/jei/api/recipe/IFocus;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void item_blacklist$filterDungeonDrops(
            IFocus<ItemStack> focus,
            CallbackInfoReturnable<List<ItemStack>> cir
    ) {
        List<ItemStack> original =
                cir.getReturnValue();

        List<ItemStack> filtered =
                JerMixinUtil.filterStacks(
                        original
                );

        if (filtered != original) {
            cir.setReturnValue(
                    filtered
            );
        }
    }
}