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
 * 1.21.1 to 1.21.10: a librarian's book listing gives no offer when its enchantment tag is
 * named in the config or was emptied by the tag strip; vanilla would sell a plain book for it.
 * The class is package-private in vanilla, so it is named by string. The handler takes the
 * callback only, so it names neither the entity's level nor 1.21.11's ServerLevel: the one
 * split left is the target at 1.21.11 (EnchantBookOfferMixinSince1_21_11Until26_1).
 */
@Mixin(targets = "net.minecraft.world.entity.npc.VillagerTrades$EnchantBookForEmeralds")
public abstract class EnchantBookOfferMixinUntil1_21_11 {
    @Shadow
    @Final
    private TagKey<Enchantment> tradeableEnchantments;

    @Inject(method = "getOffer(Lnet/minecraft/world/entity/Entity;"
            + "Lnet/minecraft/util/RandomSource;)Lnet/minecraft/world/item/trading/MerchantOffer;",
            at = @At("HEAD"), cancellable = true)
    private void item_blacklist$refuseEmptiedTag(CallbackInfoReturnable<MerchantOffer> cir) {
        // effective(): JER calls getOffer on clients, where a synced snapshot has no tag sets,
        // so the guard never fires there. A refused listing is no offer, so nothing is recorded.
        if (!TradeRules.bookListingAllowed(Blacklist.effective(), this.tradeableEnchantments)) {
            cir.setReturnValue(null);
        }
    }
}
