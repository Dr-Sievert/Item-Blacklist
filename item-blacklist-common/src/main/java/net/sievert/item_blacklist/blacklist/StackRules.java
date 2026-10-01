package net.sievert.item_blacklist.blacklist;

import net.sievert.item_blacklist.line.CloneStacks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The stack rule and the block rule, once for every hook: a stack is blacklisted when it is
 * not empty and its item is, or an enchantment it carries or stores is, or its potion is.
 * Rules of a snapshot handed in; they never ask Blacklist, so scenarios test them on built
 * snapshots. Container contents (bundles, shulker boxes) are not looked into.
 */
public final class StackRules {
    /** Why a stack is blacklisted; the first that applies, in this order. */
    public enum Reason {
        /** Not blacklisted. */
        NONE,
        /** Its item is. */
        ITEM,
        /** An enchantment it carries or stores is. */
        ENCHANTMENT,
        /** Its potion is. */
        POTION
    }

    private StackRules() {
    }

    /**
     * The item, then ENCHANTMENTS or STORED_ENCHANTMENTS, then POTION_CONTENTS; NONE at once for
     * an empty snapshot or an empty stack. Components are read only when the snapshot holds
     * enchantments or potions, so the common case costs one identity lookup.
     */
    public static Reason reason(BlacklistSnapshot snapshot, ItemStack stack) {
        if (snapshot.isEmpty() || stack.isEmpty()) {
            return Reason.NONE;
        }
        if (snapshot.item(stack.getItem())) {
            return Reason.ITEM;
        }
        if (!snapshot.enchantments().isEmpty()
                && (anyBlacklisted(snapshot, Components.get(stack, DataComponents.ENCHANTMENTS))
                || anyBlacklisted(snapshot,
                        Components.get(stack, DataComponents.STORED_ENCHANTMENTS)))) {
            return Reason.ENCHANTMENT;
        }
        if (!snapshot.potions().isEmpty()) {
            PotionContents contents = Components.get(stack, DataComponents.POTION_CONTENTS);
            if (contents != null && contents.potion().map(h -> snapshot.potion(h)).orElse(false)) {
                return Reason.POTION;
            }
        }
        return Reason.NONE;
    }

    /** Whether the stack is blacklisted for any reason. */
    public static boolean blacklisted(BlacklistSnapshot snapshot, ItemStack stack) {
        return reason(snapshot, stack) != Reason.NONE;
    }

    /**
     * The block's own item, else its pick-block stack (CloneStacks), judged by the stack rule:
     * a block whose item form differs from the block (a crop, a wall torch) is judged by what
     * a player would pick.
     */
    public static boolean blockBlacklisted(BlacklistSnapshot snapshot, LevelReader level,
            BlockPos pos, BlockState state) {
        if (snapshot.isEmpty()) {
            return false;
        }
        Item item = state.getBlock().asItem();
        if (item != Items.AIR && snapshot.item(item)) {
            return true;
        }
        return blacklisted(snapshot, CloneStacks.of(level, pos, state));
    }

    private static boolean anyBlacklisted(BlacklistSnapshot snapshot,
            ItemEnchantments enchantments) {
        if (enchantments == null) {
            return false;
        }
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            if (snapshot.enchantment(holder)) {
                return true;
            }
        }
        return false;
    }
}
