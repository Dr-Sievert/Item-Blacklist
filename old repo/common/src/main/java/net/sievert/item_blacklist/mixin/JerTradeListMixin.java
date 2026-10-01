package net.sievert.item_blacklist.mixin;

import jeresources.collection.TradeList;
import mezz.jei.api.recipe.IFocus;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
        targets = "jeresources.collection.TradeList",
        remap = false
)
public abstract class JerTradeListMixin {

    @Shadow
    private AbstractVillager entity;

    @Inject(
            method = "getFocusedList",
            at = @At("RETURN"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void item_blacklist$filterTrades(
            IFocus<ItemStack> focus,
            CallbackInfoReturnable<TradeList> cir
    ) {
        TradeList original =
                cir.getReturnValue();

        if (original == null
                || original.isEmpty()) {
            return;
        }

        TradeList filtered =
                new TradeList(
                        this.entity
                );

        for (TradeList.Trade trade : original) {
            if (item_blacklist$isBlacklisted(
                    trade.getMinCostA()
            )) {
                continue;
            }

            if (item_blacklist$isBlacklisted(
                    trade.getMinCostB()
            )) {
                continue;
            }

            if (item_blacklist$isBlacklisted(
                    trade.getMinResult()
            )) {
                continue;
            }

            filtered.add(
                    trade
            );
        }

        if (filtered.size() != original.size()) {
            cir.setReturnValue(
                    filtered
            );
        }
    }

    @Unique
    private static boolean item_blacklist$isBlacklisted(
            ItemStack stack
    ) {
        return !stack.isEmpty()
                && BlacklistManager.isBlacklisted(
                stack
        );
    }
}