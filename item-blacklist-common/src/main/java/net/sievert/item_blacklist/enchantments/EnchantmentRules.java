package net.sievert.item_blacklist.enchantments;

import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.Components;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * The enchantment rules, pure functions of a snapshot, so the four enchantment mixins stay a
 * fetch, a call and an apply, and the scenarios can test the rules on built snapshots. They
 * read the snapshot handed in and write nothing, so they are safe on worldgen workers and on
 * clients, where two of the hooks also run.
 */
public final class EnchantmentRules {
    private EnchantmentRules() {
    }

    /**
     * False only for a holder whose key the snapshot blacklists, explicitly or through a tag; a
     * holder without a key is allowed, since it has no id a config could name. The emptiness
     * check comes first: the single-provider hook runs on worldgen workers and the anvil hook
     * on clients, and with nothing blacklisted they must cost no more than that.
     */
    public static boolean allowed(BlacklistSnapshot snapshot, Holder<Enchantment> enchantment) {
        return snapshot.enchantments().isEmpty() || !snapshot.enchantment(enchantment);
    }

    /**
     * The candidates without blacklisted holders, filtered lazily as the game consumes them;
     * the same stream when the snapshot blacklists no enchantment, so vanilla is untouched then.
     */
    public static Stream<Holder<Enchantment>> allowedStream(BlacklistSnapshot snapshot,
            Stream<Holder<Enchantment>> candidates) {
        if (snapshot.enchantments().isEmpty()) {
            return candidates;
        }
        return candidates.filter(holder -> !snapshot.enchantment(holder));
    }

    /**
     * The candidates without blacklisted holders, in their order; the same list when nothing
     * goes, else an unmodifiable copy, as the game's own list (Stream.toList) is.
     */
    public static List<Holder<Enchantment>> allowedList(BlacklistSnapshot snapshot,
            List<Holder<Enchantment>> candidates) {
        if (snapshot.enchantments().isEmpty() || candidates.isEmpty()) {
            return candidates;
        }
        List<Holder<Enchantment>> kept = null;
        for (int i = 0; i < candidates.size(); i++) {
            Holder<Enchantment> holder = candidates.get(i);
            if (snapshot.enchantment(holder)) {
                // The first holder that goes: copy what came before it, then keep collecting.
                if (kept == null) {
                    kept = new ArrayList<>(candidates.subList(0, i));
                }
            } else if (kept != null) {
                kept.add(holder);
            }
        }
        return kept == null ? candidates : List.copyOf(kept);
    }

    /**
     * The input when a plain book came out of enchanting as an enchanted book without a stored
     * enchantment, else the result. The game turns a book into an enchanted book before it
     * applies what it selected, so a book whose every candidate is blacklisted would become an
     * enchanted book with nothing on it. Inert on a snapshot without enchantments.
     */
    public static ItemStack keepPlainBook(BlacklistSnapshot snapshot, ItemStack input,
            ItemStack result) {
        if (snapshot.enchantments().isEmpty() || !input.is(Items.BOOK)
                || !result.is(Items.ENCHANTED_BOOK)) {
            return result;
        }
        ItemEnchantments stored = Components.get(result, DataComponents.STORED_ENCHANTMENTS);
        return stored == null || stored.isEmpty() ? input : result;
    }
}
