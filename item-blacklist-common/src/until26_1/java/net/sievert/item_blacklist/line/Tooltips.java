package net.sievert.item_blacklist.line;

import net.minecraft.world.item.ItemStack;

/**
 * Whether a stack hides its tooltip, on the 1.21.x line: the HIDE_TOOLTIP component up to
 * 1.21.4, TOOLTIP_DISPLAY's hideTooltip from 1.21.5, where HIDE_TOOLTIP is gone. This facade
 * hands the call to {@code TooltipsUntil1_21_5} (src/until1_21_5) or
 * {@code TooltipsSince1_21_5} (src/since1_21_5). CLAUDE.md, "A name renamed or removed inside
 * a line".
 */
public final class Tooltips {
    /** The call that differs; each backend implements it for its window. */
    public interface Backend {
        /** Whether the stack carries the window's "hide the tooltip" component. */
        boolean hidden(ItemStack stack);
    }

    private static final Backend BACKEND = Backends.pick("Tooltips", Backend.class,
            Backends.until("1.21.5", "net.sievert.item_blacklist.line.TooltipsUntil1_21_5"),
            Backends.since("1.21.5", "net.sievert.item_blacklist.line.TooltipsSince1_21_5"));

    private Tooltips() {
    }

    /**
     * True when the stack asks for no tooltip, whatever the creative flag: the old mod's rule,
     * so a hidden tooltip never shows the blacklist line alone.
     */
    public static boolean hidden(ItemStack stack) {
        return BACKEND.hidden(stack);
    }
}
