package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.trades.TradeRules;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 1.21.1 to 1.21.10: judges each offer a villager or wandering trader makes from a listing,
 * where it is made, so no trade table is read or written. A refused offer becomes null, which
 * the loop skips, rolling the next listing as a shorter pool would. From 1.21.11 the method
 * takes the level first and the classes sit in npc.villager
 * (AbstractVillagerMixinSince1_21_11Until26_1); 26.x makes offers from data
 * (VillagerTradeMixinSince26_1Until26_2, VillagerTradeMixinSince26_2).
 */
@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerMixinUntil1_21_11 {
    @WrapOperation(method = "addOffersFromItemListings("
            + "Lnet/minecraft/world/item/trading/MerchantOffers;"
            + "[Lnet/minecraft/world/entity/npc/VillagerTrades$ItemListing;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/npc/VillagerTrades$ItemListing;getOffer("
                            + "Lnet/minecraft/world/entity/Entity;"
                            + "Lnet/minecraft/util/RandomSource;)"
                            + "Lnet/minecraft/world/item/trading/MerchantOffer;"))
    private MerchantOffer item_blacklist$judgeOffer(VillagerTrades.ItemListing listing,
            Entity trader, RandomSource random, Operation<MerchantOffer> original) {
        // Offers are made on the server only (getOffers throws on a client), so the server's
        // snapshot; the report only on its thread, as every offer hook records.
        return TradeRules.admit(Blacklist.server(), original.call(listing, trader, random),
                trader, Blacklist.serverRecorder());
    }
}
