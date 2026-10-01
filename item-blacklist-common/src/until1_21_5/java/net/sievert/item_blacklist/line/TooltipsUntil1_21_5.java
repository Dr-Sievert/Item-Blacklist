package net.sievert.item_blacklist.line;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/** Hidden tooltips below 1.21.5: the unit component HIDE_TOOLTIP. */
public final class TooltipsUntil1_21_5 implements Tooltips.Backend {
    /** Whether the stack has HIDE_TOOLTIP; has keeps its owner and name on every release. */
    @Override
    public boolean hidden(ItemStack stack) {
        return stack.has(DataComponents.HIDE_TOOLTIP);
    }
}
