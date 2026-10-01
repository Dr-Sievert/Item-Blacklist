package net.sievert.item_blacklist.trades;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

/**
 * 26.2's loop of AbstractVillager.addOffersFromItemListings, written once without game types
 * so a unit test can run it: 26.1.2's loop rolls the same trade set again after an entry gave
 * no offer, and never ends when every entry gives none. The 26.1.2 guard mixin runs this one.
 */
public final class OfferLoop {
    private OfferLoop() {
    }

    /**
     * Rolls an entry of the pool, keeps it while it gives offers and removes it when it gives
     * none, until {@code wanted} offers were added or the pool is empty; how many were added.
     * The pool is changed. The random source is asked once per roll, as 26.2 asks it:
     * {@code roll} gets the pool's size and returns an index below it.
     */
    public static <T, O> int fill(List<T> pool, int wanted, IntUnaryOperator roll,
            Function<? super T, ? extends O> offer, Consumer<? super O> sink) {
        int found = 0;
        while (found < wanted && !pool.isEmpty()) {
            int index = roll.applyAsInt(pool.size());
            O made = offer.apply(pool.get(index));
            if (made == null) {
                pool.remove(index);
            } else {
                sink.accept(made);
                found++;
            }
        }
        return found;
    }
}
