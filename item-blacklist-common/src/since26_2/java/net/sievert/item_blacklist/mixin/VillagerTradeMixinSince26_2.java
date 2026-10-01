package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.trades.TradeRules;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * From 26.2: judges every offer a data trade makes, for villagers and the wandering trader
 * alike; a refused offer becomes null, which every caller skips. This also refuses a tipped
 * arrow left without a potion by an emptied potion tag, and a trade_rebalance item with a
 * blacklisted enchantment (the whole offer: the data gives no way to strip one enchantment).
 * The same source as VillagerTradeMixinSince26_1Until26_2: a mixin valid on the 26.x line
 * only is a pair of one-release windows, since a class of src/since26_1 cannot be gated.
 */
@Mixin(VillagerTrade.class)
public abstract class VillagerTradeMixinSince26_2 {
    @ModifyReturnValue(method = "getOffer(Lnet/minecraft/world/level/storage/loot/LootContext;)"
            + "Lnet/minecraft/world/item/trading/MerchantOffer;",
            at = @At("RETURN"))
    private MerchantOffer item_blacklist$judgeOffer(MerchantOffer offer,
            @Local(argsOnly = true) LootContext lootContext) {
        // Every return, since the nesting of the returns differs between 26.1.2 and 26.2; a
        // null stays null. The merchant is the context's this_entity, which the trade set sets.
        // JER calls offer code on clients, so the report is written on the server's thread only.
        return TradeRules.admit(Blacklist.server(), offer,
                lootContext.getOptionalParameter(LootContextParams.THIS_ENTITY),
                Blacklist.serverRecorder());
    }
}
