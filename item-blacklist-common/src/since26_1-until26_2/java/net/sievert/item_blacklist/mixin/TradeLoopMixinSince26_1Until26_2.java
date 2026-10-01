package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.trades.OfferLoop;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.1.2 only: a trade set with allow_duplicates rolls its whole list again after an entry gave
 * no offer, so it never ends when every entry gives none, which the offer hook can cause. This
 * runs 26.2's loop instead (OfferLoop), which drops an entry that gave none. Vanilla's sets
 * never allow duplicates, so only datapack sets reach it; 26.2 needs no guard.
 */
@Mixin(AbstractVillager.class)
public abstract class TradeLoopMixinSince26_1Until26_2 {
    @Inject(method = "addOffersFromItemListings("
            + "Lnet/minecraft/world/level/storage/loot/LootContext;"
            + "Lnet/minecraft/world/item/trading/MerchantOffers;"
            + "Lnet/minecraft/core/HolderSet;I)V",
            at = @At("HEAD"), cancellable = true)
    private static void item_blacklist$dropNullEntries(LootContext lootContext,
            MerchantOffers merchantOffers, HolderSet<VillagerTrade> potentialOffers,
            int numberOfOffers, CallbackInfo ci) {
        List<Holder<VillagerTrade>> pool = new ArrayList<>();
        potentialOffers.forEach(pool::add);
        // One nextInt(size) per roll, as 26.2; each getOffer still passes the offer hook.
        OfferLoop.fill(pool, numberOfOffers, bound -> lootContext.getRandom().nextInt(bound),
                trade -> trade.value().getOffer(lootContext), merchantOffers::add);
        ci.cancel();
    }
}
