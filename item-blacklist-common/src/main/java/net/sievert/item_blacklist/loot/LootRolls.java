package net.sievert.item_blacklist.loot;

import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.StackRules;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;

/**
 * The roll rules: what leaves a loot roll is judged by the stack rule, so a stack the JSON
 * walk cannot see (set_item, item modifiers, a nested table, a loader's additions) is still
 * refused. Rolls happen per use, so they never record.
 */
public final class LootRolls {
    private LootRolls() {
    }

    /** The consumer that hands on only the stacks the stack rule keeps. */
    public static Consumer<ItemStack> filtering(BlacklistSnapshot snapshot,
            Consumer<ItemStack> output) {
        return stack -> {
            if (!StackRules.blacklisted(snapshot, stack)) {
                output.accept(stack);
            }
        };
    }

    /** Removes the blacklisted stacks of a finished roll, in place. */
    public static void removeBlacklisted(BlacklistSnapshot snapshot, List<ItemStack> stacks) {
        stacks.removeIf(stack -> StackRules.blacklisted(snapshot, stack));
    }
}
