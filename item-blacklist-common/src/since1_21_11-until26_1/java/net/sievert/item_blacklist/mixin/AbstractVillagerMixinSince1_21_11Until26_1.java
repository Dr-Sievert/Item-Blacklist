package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.trades.TradeRules;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 1.21.11: judges each offer a villager or wandering trader makes from a listing, where it is
 * made; a refused offer becomes null, which the loop skips. The same hook as
 * AbstractVillagerMixinUntil1_21_11, for the one release whose method takes the level first
 * and whose classes sit in npc.villager.
 */
@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerMixinSince1_21_11Until26_1 {
    @WrapOperation(method = "addOffersFromItemListings("
            + "Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/item/trading/MerchantOffers;"
            + "[Lnet/minecraft/world/entity/npc/villager/VillagerTrades$ItemListing;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/npc/villager/VillagerTrades$ItemListing;"
                            + "getOffer("
                            + "Lnet/minecraft/server/level/ServerLevel;"
                            + "Lnet/minecraft/world/entity/Entity;"
                            + "Lnet/minecraft/util/RandomSource;)"
                            + "Lnet/minecraft/world/item/trading/MerchantOffer;"))
    private MerchantOffer item_blacklist$judgeOffer(VillagerTrades.ItemListing listing,
            ServerLevel level, Entity trader, RandomSource random,
            Operation<MerchantOffer> original) {
        // Offers are made on the server only, so the server's snapshot; the report only on
        // its thread, as every offer hook records.
        return TradeRules.admit(Blacklist.server(), original.call(listing, level, trader, random),
                trader, Blacklist.serverRecorder());
    }
}
