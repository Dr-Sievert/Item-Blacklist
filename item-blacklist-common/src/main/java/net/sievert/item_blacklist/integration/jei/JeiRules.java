package net.sievert.item_blacklist.integration.jei;

import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.StackRules;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * Which of JEI's brewing and anvil recipes the mod hides: rules of a snapshot and plain
 * stacks, naming nothing of JEI, so scenarios test them without JEI and the bridge only
 * applies them. The brewing rule is the brewing subsystem's own: a mix goes when its output or
 * an input is blacklisted, or when every alternative of its ingredient is.
 */
public final class JeiRules {
    private JeiRules() {
    }

    /**
     * The output or an input is blacklisted, or the ingredient list is not empty and every
     * stack of it is.
     */
    public static boolean hideBrewing(BlacklistSnapshot snapshot, ItemStack output,
            List<ItemStack> inputs, List<ItemStack> ingredients) {
        if (StackRules.blacklisted(snapshot, output)) {
            return true;
        }
        for (ItemStack input : inputs) {
            if (StackRules.blacklisted(snapshot, input)) {
                return true;
            }
        }
        if (ingredients.isEmpty()) {
            return false;
        }
        for (ItemStack ingredient : ingredients) {
            if (!StackRules.blacklisted(snapshot, ingredient)) {
                return false;
            }
        }
        return true;
    }

    /** Any stack of the three lists is blacklisted: the anvil would take or give it. */
    public static boolean hideAnvil(BlacklistSnapshot snapshot, List<ItemStack> left,
            List<ItemStack> right, List<ItemStack> outputs) {
        return anyBlacklisted(snapshot, left) || anyBlacklisted(snapshot, right)
                || anyBlacklisted(snapshot, outputs);
    }

    private static boolean anyBlacklisted(BlacklistSnapshot snapshot, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (StackRules.blacklisted(snapshot, stack)) {
                return true;
            }
        }
        return false;
    }
}
