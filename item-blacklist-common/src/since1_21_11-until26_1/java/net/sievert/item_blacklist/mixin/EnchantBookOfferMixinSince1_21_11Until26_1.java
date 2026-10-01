package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.trades.TradeRules;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.21.11: a librarian's book listing gives no offer when its enchantment tag is named in the
 * config or was emptied by the tag strip. The same guard as EnchantBookOfferMixinUntil1_21_11,
 * for the release whose class sits in npc.villager and whose getOffer takes the level first.
 */
@Mixin(targets = "net.minecraft.world.entity.npc.villager.VillagerTrades$EnchantBookForEmeralds")
public abstract class EnchantBookOfferMixinSince1_21_11Until26_1 {
    @Shadow
    @Final
    private TagKey<Enchantment> tradeableEnchantments;

    @Inject(method = "getOffer(Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/entity/Entity;Lnet/minecraft/util/RandomSource;)"
            + "Lnet/minecraft/world/item/trading/MerchantOffer;",
            at = @At("HEAD"), cancellable = true)
    private void item_blacklist$refuseEmptiedTag(CallbackInfoReturnable<MerchantOffer> cir) {
        // effective(): JER calls getOffer on clients, where a synced snapshot has no tag sets,
        // so the guard never fires there. A refused listing is no offer, so nothing is recorded.
        if (!TradeRules.bookListingAllowed(Blacklist.effective(), this.tradeableEnchantments)) {
            cir.setReturnValue(null);
        }
    }
}
