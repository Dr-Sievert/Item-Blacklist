package net.sievert.item_blacklist.line;

import net.sievert.item_blacklist.blacklist.Components;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Hidden tooltips from 1.21.5 to 1.21.11: TOOLTIP_DISPLAY with hideTooltip set. Read through
 * Components, since ItemStack.get moved to another interface at 1.21.5 on Fabric.
 */
public final class TooltipsSince1_21_5 implements Tooltips.Backend {
    /** Whether the stack's TOOLTIP_DISPLAY hides the whole tooltip. */
    @Override
    public boolean hidden(ItemStack stack) {
        TooltipDisplay display = Components.get(stack, DataComponents.TOOLTIP_DISPLAY);
        return display != null && display.hideTooltip();
    }
}
