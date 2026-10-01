package net.sievert.item_blacklist.integration.jer;

import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.StackRules;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * The filters of Just Enough Resources' pages, each a function of the snapshot it is handed:
 * the JER mixins fetch the snapshot and apply the result, and the scenarios run the same code
 * on the live and on built snapshots. Names nothing of JER or JEI, so it loads on every server
 * and client whether JER is installed or not, and never asks Blacklist itself.
 */
public final class JerRules {
    private JerRules() {
    }

    /**
     * The stacks that are not blacklisted, in their order; a null element is kept. The same
     * instance when nothing is removed, so a hook changes nothing for a page that shows nothing
     * blacklisted.
     */
    public static List<ItemStack> visibleStacks(BlacklistSnapshot snapshot,
            List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty() || snapshot.isEmpty()) {
            return stacks;
        }
        List<ItemStack> kept = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            if (stack == null || !StackRules.blacklisted(snapshot, stack)) {
                kept.add(stack);
            }
        }
        return kept.size() == stacks.size() ? stacks : kept;
    }

    /**
     * False when a non-empty stack of the trade is blacklisted, whatever its count: JER shows a
     * sampled offer, whose stacks may carry a count of 0. A null stack (one that could not be
     * read) counts as visible, so a trade JER changed shape for stays shown.
     */
    public static boolean tradeVisible(BlacklistSnapshot snapshot, ItemStack costA,
            ItemStack costB, ItemStack result) {
        return !blacklistedAnyCount(snapshot, costA)
                && !blacklistedAnyCount(snapshot, costB)
                && !blacklistedAnyCount(snapshot, result);
    }

    /**
     * A clone of {@code trades} without the trades tradeVisible refuses; the same instance when
     * none is refused. LinkedList.clone keeps the runtime class and every field of a subclass,
     * so JER's own list type comes back without naming it or calling its constructor. Each
     * reader gives one stack of a trade, or null when it cannot be read.
     */
    @SuppressWarnings("unchecked")
    public static <L extends LinkedList<?>> L visibleTrades(BlacklistSnapshot snapshot, L trades,
            Function<Object, ItemStack> costA, Function<Object, ItemStack> costB,
            Function<Object, ItemStack> result) {
        if (trades == null || trades.isEmpty() || snapshot.isEmpty()) {
            return trades;
        }
        Set<Object> refused = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object trade : trades) {
            if (trade != null && !tradeVisible(snapshot, costA.apply(trade), costB.apply(trade),
                    result.apply(trade))) {
                refused.add(trade);
            }
        }
        if (refused.isEmpty()) {
            return trades;
        }
        L copy = (L) trades.clone();
        copy.removeIf(refused::contains);
        return copy;
    }

    /**
     * The drops whose stacks, as {@code read} gives them, are visible (dropVisible); a null
     * drop goes, as it did in the old mod. The same instance when none goes.
     */
    public static <E> List<E> visibleDrops(BlacklistSnapshot snapshot, List<E> drops,
            Function<? super E, ?> read) {
        if (drops == null || drops.isEmpty() || snapshot.isEmpty()) {
            return drops;
        }
        List<E> kept = new ArrayList<>(drops.size());
        for (E drop : drops) {
            if (drop != null && dropVisible(snapshot, read.apply(drop))) {
                kept.add(drop);
            }
        }
        return kept.size() == drops.size() ? drops : kept;
    }

    /**
     * Whether a drop whose stacks were read as {@code stacks} stays: anything that is no
     * Collection (a failed read, JerReflect.FAILED) stays; an empty collection goes; one with a
     * stack that is not blacklisted stays; one whose stacks are all blacklisted goes; one that
     * holds no ItemStack at all stays, since the rule has nothing to judge.
     */
    public static boolean dropVisible(BlacklistSnapshot snapshot, Object stacks) {
        if (!(stacks instanceof Collection<?> collection)) {
            return true;
        }
        if (collection.isEmpty()) {
            return false;
        }
        boolean sawStack = false;
        for (Object element : collection) {
            if (element instanceof ItemStack stack) {
                sawStack = true;
                if (!StackRules.blacklisted(snapshot, stack)) {
                    return true;
                }
            }
        }
        return !sawStack;
    }

    /**
     * The entries whose enchantment is not blacklisted, in their order; an entry whose holder
     * is null (one that could not be read) is kept. The same instance when none goes.
     */
    public static <E> List<E> visibleEnchantments(BlacklistSnapshot snapshot, List<E> entries,
            Function<E, Holder<Enchantment>> holder) {
        if (entries == null || entries.isEmpty() || snapshot.isEmpty()) {
            return entries;
        }
        List<E> kept = new ArrayList<>(entries.size());
        for (E entry : entries) {
            Holder<Enchantment> value = holder.apply(entry);
            if (value == null || !snapshot.enchantment(value)) {
                kept.add(entry);
            }
        }
        return kept.size() == entries.size() ? entries : kept;
    }

    /**
     * The index of the last page of a list of this size, max(0, (entries + 10) / 11 - 1): JER
     * draws 11 enchantments per page, while its own count divides by 12.
     */
    public static int lastPage(int entries) {
        return Math.max(0, (entries + 10) / 11 - 1);
    }

    /** The value as a stack, or null: what a reflective read gives is typed Object. */
    public static ItemStack asStack(Object value) {
        return value instanceof ItemStack stack ? stack : null;
    }

    /**
     * The value as an enchantment holder, or null: what a reflective read gives is typed
     * Object. A holder of another registry is never blacklisted, since its key never is.
     */
    @SuppressWarnings("unchecked")
    public static Holder<Enchantment> asEnchantmentHolder(Object value) {
        return value instanceof Holder<?> holder ? (Holder<Enchantment>) holder : null;
    }

    /**
     * The stack rule for a stack whatever its count: null and ItemStack.EMPTY never are. A
     * count of 0 or below makes a stack empty to the game, so it is set to 1 for the check and
     * set back to what getCount gave (0) in finally; the stack is empty again either way, and
     * setCount only writes the field on every release.
     */
    private static boolean blacklistedAnyCount(BlacklistSnapshot snapshot, ItemStack stack) {
        if (stack == null || stack == ItemStack.EMPTY) {
            return false;
        }
        int count = stack.getCount();
        if (count > 0) {
            return StackRules.blacklisted(snapshot, stack);
        }
        stack.setCount(1);
        try {
            return StackRules.blacklisted(snapshot, stack);
        } finally {
            stack.setCount(count);
        }
    }
}
