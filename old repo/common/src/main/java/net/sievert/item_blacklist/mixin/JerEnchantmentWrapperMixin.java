package net.sievert.item_blacklist.mixin;

import jeresources.entry.EnchantmentEntry;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Pseudo
@Mixin(
        targets = "jeresources.jei.enchantment.EnchantmentWrapper",
        remap = false
)
public abstract class JerEnchantmentWrapperMixin {

    @Shadow
    @Final
    private List<EnchantmentEntry> enchantments;

    @Shadow
    @Final
    @Mutable
    private int lastSet;

    @Shadow
    private int set;

    @Unique
    private List<EnchantmentEntry> item_blacklist$originalEnchantments;

    @Inject(
            method = "<init>",
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void item_blacklist$captureEnchantments(
            CallbackInfo ci
    ) {
        this.item_blacklist$originalEnchantments =
                new ArrayList<>(
                        this.enchantments
                );

        item_blacklist$refreshEnchantments();
    }

    @Inject(
            method = "getEnchantments()Ljava/util/List;",
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private void item_blacklist$refreshBeforeDisplay(
            CallbackInfoReturnable<List<EnchantmentEntry>> cir
    ) {
        item_blacklist$refreshEnchantments();
    }

    @Unique
    private void item_blacklist$refreshEnchantments() {
        if (this.item_blacklist$originalEnchantments == null) {
            return;
        }

        this.enchantments.clear();

        for (EnchantmentEntry entry :
                this.item_blacklist$originalEnchantments) {
            if (BlacklistManager.isBlacklistedEnchantment(
                    entry.getEnchantmentHolder()
            )) {
                continue;
            }

            this.enchantments.add(
                    entry
            );
        }

        this.lastSet =
                this.enchantments.size() / 12;

        if (this.set > this.lastSet) {
            this.set = 0;
        }
    }
}