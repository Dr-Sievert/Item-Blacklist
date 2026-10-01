package net.sievert.item_blacklist.mixin.jer;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.integration.jer.JerReflect;
import net.sievert.item_blacklist.integration.jer.JerRules;
import java.util.List;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * JER's mob page: a drop leaves when its own getDrops(), read by name, holds no visible stack;
 * a drop that cannot be read stays. The drops are read through JerReflect, not a JER type, so
 * this also works where JER's LootDrop class sits in no API package the mod could name. JER's
 * field is never changed: a new list is returned.
 */
@Pseudo
@Mixin(targets = "jeresources.entry.MobEntry", remap = false)
public abstract class JerMobEntryMixin {
    @ModifyReturnValue(method = "getDrops()Ljava/util/List;", at = @At("RETURN"), require = 0,
            remap = false)
    private List<?> item_blacklist$visibleDrops(List<?> original) {
        return JerRules.visibleDrops(Blacklist.effective(), original,
                drop -> JerReflect.call(drop, "getDrops"));
    }
}
