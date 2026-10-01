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
 * JER's dungeon chest page: blacklisted stacks leave the list that every count, slot and page
 * of the page reads. @Pseudo with a string target and require = 0, so without JER, or with a
 * JER build that lacks the method, the plugin skips it and nothing fails. The descriptor is
 * needed: DungeonEntry also has a getItemStacks(Stream) overload.
 */
@Pseudo
@Mixin(targets = "jeresources.entry.DungeonEntry", remap = false)
public abstract class JerDungeonEntryMixin {
    @ModifyReturnValue(method = "getItemStacks(Lmezz/jei/api/recipe/IFocus;)Ljava/util/List;",
            at = @At("RETURN"), require = 0, remap = false)
    private List<ItemStack> item_blacklist$visibleStacks(List<ItemStack> original) {
        return JerRules.visibleStacks(Blacklist.effective(), original);
    }
}
