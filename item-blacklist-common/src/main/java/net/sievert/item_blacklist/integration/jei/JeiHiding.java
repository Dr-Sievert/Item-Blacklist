package net.sievert.item_blacklist.integration.jei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What one filter pass does to one of JEI's recipe categories, as a pure diff of plain lists:
 * it names nothing of JEI or the game, so JeiHidingTest covers every case without JEI, and the
 * bridge only applies the result. The mod hides a recipe only while JEI shows it and shows
 * again only what it hid itself, so a recipe another mod hid is never touched.
 */
public final class JeiHiding {
    /**
     * The result of one pass over one category.
     *
     * @param hide the recipes JEI shows now that the rule hides: to hide now
     * @param unhide the recipes this mod hid that the rule no longer hides: to show again
     * @param hiddenByUs what this mod holds hidden after the pass, compared by identity
     */
    public record Plan<R>(List<R> hide, List<R> unhide, Set<R> hiddenByUs) {
    }

    private JeiHiding() {
    }

    /**
     * An empty set compared by identity: JEI's recipe objects are stable instances within one
     * runtime, and two equal recipes of different mods must not share a claim.
     */
    public static <R> Set<R> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    /**
     * One pass: {@code all} is every recipe of the category, hidden ones included;
     * {@code visible} what JEI shows now; {@code hide} the rule, asked once per recipe;
     * {@code ours} what this mod hid on this runtime. A recipe the rule hides is hidden and
     * claimed when shown, kept when already ours, and left alone when another mod hid it; a
     * recipe of ours the rule no longer hides is shown again. A claim on a recipe that is not
     * in {@code all} any more is dropped, never unhidden.
     */
    public static <R> Plan<R> plan(List<R> all, List<R> visible, Predicate<? super R> hide,
            Set<R> ours) {
        Set<R> shown = identitySet();
        shown.addAll(visible);
        List<R> toHide = new ArrayList<>();
        List<R> toUnhide = new ArrayList<>();
        Set<R> next = identitySet();
        for (R recipe : all) {
            boolean wanted = hide.test(recipe);
            boolean isShown = shown.contains(recipe);
            boolean isOurs = ours.contains(recipe);
            if (wanted && isShown) {
                // Shown and blacklisted: hide it and claim it. Also when it was ours before and
                // JEI shows it again (a runtime that reset its hidden set).
                toHide.add(recipe);
                next.add(recipe);
            } else if (wanted && isOurs) {
                // Still hidden by this mod.
                next.add(recipe);
            } else if (!wanted && !isShown && isOurs) {
                // Hidden by this mod and no longer blacklisted: show it again.
                toUnhide.add(recipe);
            }
            // Wanted and hidden but not ours: another mod hid it; left alone and not claimed.
            // Not wanted and shown: nothing to do; a stale claim on it is dropped.
        }
        return new Plan<>(List.copyOf(toHide), List.copyOf(toUnhide), next);
    }
}
