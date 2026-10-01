package net.sievert.item_blacklist.mixin.jer;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.integration.jer.JerReflect;
import net.sievert.item_blacklist.integration.jer.JerRules;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * JER's enchantment page of an item: the list without blacklisted enchantments, rebuilt from
 * JER's original list whenever the snapshot's generation changes, and the last page
 * recomputed for JER's pages of 11. JER's fields are reached by name through JerReflect, never
 * by @Shadow, and a new list is assigned instead of editing JER's; the @Unique members look
 * nothing up in JER, so no JER build can make them fail.
 */
@Pseudo
@Mixin(targets = "jeresources.jei.enchantment.EnchantmentWrapper", remap = false)
public abstract class JerEnchantmentWrapperMixin {
    /** JER's list as its constructor left it; null when it could not be read. */
    @Unique
    private List<Object> item_blacklist$originals;
    /** Whether a refresh has written the fields, and for which snapshot generation. */
    @Unique
    private boolean item_blacklist$shown;
    @Unique
    private long item_blacklist$generation;

    // No descriptor: the one constructor's names ItemStack, which does not match under
    // remap = false on the 1.21.x Fabric line (intermediary names at run time).
    @Inject(method = "<init>", at = @At("RETURN"), require = 0, remap = false)
    private void item_blacklist$keepOriginals(CallbackInfo ci) {
        if (JerReflect.get(this, "enchantments") instanceof List<?> entries) {
            this.item_blacklist$originals = new ArrayList<>(entries);
        }
        item_blacklist$refresh();
    }

    @Inject(method = "getEnchantments()Ljava/util/List;", at = @At("HEAD"), require = 0,
            remap = false)
    private void item_blacklist$refreshBeforeRead(CallbackInfoReturnable<List<?>> cir) {
        item_blacklist$refresh();
    }

    /**
     * Writes JER's list and page count for the current snapshot, once per generation. All
     * three fields are read first and a refused page count puts the list back: JER draws page
     * {@code set} as subList(set * 11, ...), so a shorter list with JER's old page count would
     * throw while the page is drawn. Any other failure leaves the page as JER made it.
     */
    @Unique
    private void item_blacklist$refresh() {
        List<Object> originals = this.item_blacklist$originals;
        BlacklistSnapshot snapshot = Blacklist.effective();
        if (originals == null
                || (this.item_blacklist$shown
                        && this.item_blacklist$generation == snapshot.generation())) {
            return;
        }
        this.item_blacklist$shown = true;
        this.item_blacklist$generation = snapshot.generation();
        Object self = this;
        if (!(JerReflect.get(self, "enchantments") instanceof List<?> shownBefore)
                || !(JerReflect.get(self, "lastSet") instanceof Integer)
                || !(JerReflect.get(self, "set") instanceof Integer page)) {
            return;
        }
        List<Object> visible = JerRules.visibleEnchantments(snapshot, originals,
                entry -> JerRules.asEnchantmentHolder(
                        JerReflect.call(entry, "getEnchantmentHolder")));
        int lastPage = JerRules.lastPage(visible.size());
        if (!JerReflect.set(self, "enchantments", new ArrayList<>(visible))) {
            return;
        }
        if (!JerReflect.set(self, "lastSet", lastPage)) {
            // list and page count must stay consistent: put JER's list back
            JerReflect.set(self, "enchantments", shownBefore);
            return;
        }
        if (page > lastPage) {
            JerReflect.set(self, "set", 0);
        }
    }
}
