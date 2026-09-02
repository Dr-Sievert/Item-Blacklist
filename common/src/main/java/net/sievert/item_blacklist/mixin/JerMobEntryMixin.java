package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.integration.jer.JerMixinUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Pseudo
@Mixin(
        targets = "jeresources.entry.MobEntry",
        remap = false
)
public abstract class JerMobEntryMixin {

    @Inject(
            method = "getDrops()Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private void item_blacklist$filterMobDrops(
            CallbackInfoReturnable<List<?>> cir
    ) {
        List<?> original =
                cir.getReturnValue();

        if (original == null
                || original.isEmpty()) {
            return;
        }

        List<Object> filtered =
                new ArrayList<>(
                        original.size()
                );

        for (Object drop : original) {
            if (JerMixinUtil.hasVisibleLootDrop(
                    drop
            )) {
                filtered.add(
                        drop
                );
            }
        }

        if (filtered.size() != original.size()) {
            cir.setReturnValue(
                    filtered
            );
        }
    }
}