package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.ServerState;
import net.sievert.item_blacklist.trades.TradeRules;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.21.11: a fletcher's tipped-arrow listing gives no offer when no potion may be offered;
 * vanilla would pick from an empty list and throw. The same guard as
 * TippedArrowOfferMixinUntil1_21_11, for the release whose class sits in npc.villager and
 * whose getOffer takes the level first.
 */
@Mixin(targets =
        "net.minecraft.world.entity.npc.villager.VillagerTrades$TippedArrowForItemsAndEmeralds")
public abstract class TippedArrowOfferMixinSince1_21_11Until26_1 {
    @Inject(method = "getOffer(Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/entity/Entity;Lnet/minecraft/util/RandomSource;)"
            + "Lnet/minecraft/world/item/trading/MerchantOffer;",
            at = @At("HEAD"), cancellable = true)
    private void item_blacklist$refuseEmptyPool(CallbackInfoReturnable<MerchantOffer> cir) {
        BlacklistSnapshot snapshot = Blacklist.server();
        if (snapshot.isEmpty()) {
            // The cheap check first (JER runs this on clients too); with nothing blacklisted
            // the brewing filter restored every mix, so the pool is vanilla's.
            return;
        }
        ServerState state = Blacklist.serverState();
        if (state != null && TradeRules.arrowPoolEmpty(snapshot, state.server().potionBrewing())) {
            cir.setReturnValue(null);
        }
    }
}
