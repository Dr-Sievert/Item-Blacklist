package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.trades.TradeRules;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 1.21.1 to 1.21.4: the wandering trader's one rare pick, which updateTrades makes with a
 * direct getOffer call outside addOffersFromItemListings; its five common picks pass
 * AbstractVillagerMixinUntil1_21_11. A refused rare pick gives no rare offer: vanilla does not
 * roll it again. From 1.21.5 every pick goes through addOffersFromItemListings.
 */
@Mixin(WanderingTrader.class)
public abstract class WanderingTraderMixinUntil1_21_5 {
    @WrapOperation(method = "updateTrades()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/npc/VillagerTrades$ItemListing;getOffer("
                            + "Lnet/minecraft/world/entity/Entity;"
                            + "Lnet/minecraft/util/RandomSource;)"
                            + "Lnet/minecraft/world/item/trading/MerchantOffer;"))
    private MerchantOffer item_blacklist$judgeRareOffer(VillagerTrades.ItemListing listing,
            Entity trader, RandomSource random, Operation<MerchantOffer> original) {
        // The null vanilla already checks for skips a refused pick.
        return TradeRules.admit(Blacklist.server(), original.call(listing, trader, random),
                trader, Blacklist.serverRecorder());
    }
}
