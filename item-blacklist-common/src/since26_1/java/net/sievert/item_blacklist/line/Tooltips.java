package net.sievert.item_blacklist.line;

import net.sievert.item_blacklist.blacklist.Components;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Whether a stack hides its tooltip, on the 26.x line: TOOLTIP_DISPLAY's hideTooltip, one form
 * on every release of the line. The 1.21.x twin is a facade, since the component changed at
 * 1.21.5.
 */
public final class Tooltips {
    private Tooltips() {
    }

    /**
     * True when the stack asks for no tooltip, whatever the creative flag: the old mod's rule,
     * so a hidden tooltip never shows the blacklist line alone.
     */
    public static boolean hidden(ItemStack stack) {
        TooltipDisplay display = Components.get(stack, DataComponents.TOOLTIP_DISPLAY);
        return display != null && display.hideTooltip();
    }
}
