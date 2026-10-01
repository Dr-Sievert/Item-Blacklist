package net.sievert.item_blacklist.mixin.jer;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.integration.jer.JerRules;
import java.util.List;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * JER's loot drop (a drop and its smelted form): blacklisted stacks leave the list, for every
 * page that reads it. A string target only: the Fabric 1.21.1 JER jar lacks JER's API
 * package, and the plugin skips a @Pseudo mixin whose target is absent.
 */
@Pseudo
@Mixin(targets = "jeresources.api.drop.LootDrop", remap = false)
public abstract class JerLootDropMixin {
    @ModifyReturnValue(method = "getDrops()Ljava/util/List;", at = @At("RETURN"), require = 0,
            remap = false)
    private List<ItemStack> item_blacklist$visibleStacks(List<ItemStack> original) {
        return JerRules.visibleStacks(Blacklist.effective(), original);
    }
}
