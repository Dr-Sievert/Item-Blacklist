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
 * JER's world-gen (ore) page: blacklisted drops leave the list; the ore blocks themselves are
 * not filtered, as in the old mod.
 */
@Pseudo
@Mixin(targets = "jeresources.entry.WorldGenEntry", remap = false)
public abstract class JerWorldGenEntryMixin {
    @ModifyReturnValue(method = "getDrops()Ljava/util/List;", at = @At("RETURN"), require = 0,
            remap = false)
    private List<ItemStack> item_blacklist$visibleStacks(List<ItemStack> original) {
        return JerRules.visibleStacks(Blacklist.effective(), original);
    }
}
