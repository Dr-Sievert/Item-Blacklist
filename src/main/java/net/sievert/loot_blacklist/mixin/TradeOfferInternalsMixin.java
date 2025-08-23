package net.sievert.loot_blacklist.mixin;

import net.minecraft.village.TradeOffers;
import net.sievert.loot_blacklist.VillagerTradeBlacklist;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Consumer;

@Mixin(targets = "net.fabricmc.fabric.impl.object.builder.TradeOfferInternals", remap = false)
public abstract class TradeOfferInternalsMixin {

    @Redirect(
            method = "registerOffers",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"
            )
    )
    private static void loot_blacklist$filterTrades(
            Consumer<List<TradeOffers.Factory>> original,
            Object listObj
    ) {
        @SuppressWarnings("unchecked")
        List<TradeOffers.Factory> list = (List<TradeOffers.Factory>) listObj;

        // Let Fabric API add its trades first
        original.accept(list);

        // Then filter them against our (already validated) blacklist
        int before = list.size();
        list.removeIf(f -> !VillagerTradeBlacklist.shouldKeepFactory(f));
        int removed = before - list.size();

        if (removed > 0) {
            VillagerTradeBlacklist.incrementFabricRemoved();
        }
    }
}
