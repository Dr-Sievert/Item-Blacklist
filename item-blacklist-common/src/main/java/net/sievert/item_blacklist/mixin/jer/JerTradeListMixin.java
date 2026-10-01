package net.sievert.item_blacklist.mixin.jer;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.integration.jer.JerReflect;
import net.sievert.item_blacklist.integration.jer.JerRules;
import java.util.LinkedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * JER's villager and wandering trader trades: the focused list is replaced by a clone without
 * the trades whose sampled cost A, cost B or result is blacklisted, whatever its count. It
 * names no JER field or constructor, so it fits every JER build; the trades' stacks are read
 * by name through JerReflect. A level whose trades all go disappears from the page, since JER
 * counts a level by the size of this list.
 */
@Pseudo
@Mixin(targets = "jeresources.collection.TradeList", remap = false)
public abstract class JerTradeListMixin {
    // @Inject and not @ModifyReturnValue: MixinExtras checks the handler's parameter against
    // the exact return type, JER's TradeList, which no source of the mod may name. The clone
    // keeps that runtime class, so the cast the callback's return makes passes.
    @Inject(method = "getFocusedList(Lmezz/jei/api/recipe/IFocus;)"
            + "Ljeresources/collection/TradeList;",
            at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void item_blacklist$visibleTrades(CallbackInfoReturnable<LinkedList<?>> cir) {
        LinkedList<?> original = cir.getReturnValue();
        LinkedList<?> visible = JerRules.visibleTrades(Blacklist.effective(), original,
                trade -> JerRules.asStack(JerReflect.call(trade, "getMinCostA")),
                trade -> JerRules.asStack(JerReflect.call(trade, "getMinCostB")),
                trade -> JerRules.asStack(JerReflect.call(trade, "getMinResult")));
        if (visible != original) {
            cir.setReturnValue(visible);
        }
    }
}
