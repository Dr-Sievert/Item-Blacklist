package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.StackRules;
import net.sievert.item_blacklist.itemuse.ItemUse;
import net.sievert.item_blacklist.line.Tooltips;
import java.util.ArrayList;
import java.util.List;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The red last tooltip line of a blacklisted stack, unless the stack hides its tooltip. Reads
 * effective(): tooltips are drawn on clients from the synced snapshot. The handler runs on the
 * render thread and on the client's search-tree worker, so it reads one volatile field, tests
 * isEmpty() first and writes nothing. Non-cancelling, so Fabric API's tooltip callback at the
 * same RETURN still runs; on NeoForge the tooltip event's lines come before this one.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackTooltipMixin {
    @ModifyReturnValue(method = "getTooltipLines(Lnet/minecraft/world/item/Item$TooltipContext;"
            + "Lnet/minecraft/world/entity/player/Player;"
            + "Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;",
            at = @At("RETURN"))
    private List<Component> item_blacklist$appendBlacklistLine(List<Component> lines) {
        BlacklistSnapshot snapshot = Blacklist.effective();
        if (snapshot.isEmpty()) {
            return lines;
        }
        ItemStack stack = (ItemStack) (Object) this;
        if (!StackRules.blacklisted(snapshot, stack) || Tooltips.hidden(stack)) {
            return lines;
        }
        // A copy: the method's early return can be an immutable list.
        List<Component> withLine = new ArrayList<>(lines);
        withLine.add(ItemUse.tooltipLine());
        return withLine;
    }
}
