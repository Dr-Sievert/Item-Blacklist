package net.sievert.item_blacklist.mixin;

import net.minecraft.world.item.ItemStack;
import net.sievert.item_blacklist.integration.jer.JerMixinUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Pseudo
@Mixin(
        targets = "jeresources.entry.PlantEntry",
        remap = false
)
public abstract class JerPlantEntryMixin {

    @Inject(
            method = "getLootDropStacks()Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void item_blacklist$filterPlantDrops(
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