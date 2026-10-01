package net.sievert.item_blacklist.integration.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * The diff one JEI pass applies, on strings standing for recipes: hide what is shown and
 * blacklisted, show again only what this mod hid, never touch what another mod hid, and
 * compare recipes by identity. Strings made with new String are equal but not identical.
 */
class JeiHidingTest {
    private final String a = new String("a");
    private final String b = new String("b");
    private final String c = new String("c");

    @Test
    void freshRuntimeHidesVisibleMatches() {
        JeiHiding.Plan<String> plan =
                JeiHiding.plan(List.of(a, b, c), List.of(a, b, c), only(b), ours());
        assertEquals(List.of(b), plan.hide());
        assertEquals(List.of(), plan.unhide());
        assertClaims(plan, b);
    }

    @Test
    void leavesOtherModsHiddenRecipes() {
        JeiHiding.Plan<String> plan = JeiHiding.plan(List.of(a, b), List.of(a), only(b), ours());
        assertEquals(List.of(), plan.hide());
        assertEquals(List.of(), plan.unhide());
        assertClaims(plan);
    }

    @Test
    void unhidesOursNoLongerWanted() {
        JeiHiding.Plan<String> plan = JeiHiding.plan(List.of(a, b), List.of(a), none(), ours(b));
        assertEquals(List.of(), plan.hide());
        assertEquals(List.of(b), plan.unhide());
        assertClaims(plan);
    }

    @Test
    void keepsOursStillWanted() {
        JeiHiding.Plan<String> plan =
                JeiHiding.plan(List.of(a, b), List.of(a), only(b), ours(b));
        assertEquals(List.of(), plan.hide());
        assertEquals(List.of(), plan.unhide());
        assertClaims(plan, b);
    }

    @Test
    void rehidesWhenJeiShowsOursAgain() {
        JeiHiding.Plan<String> plan =
                JeiHiding.plan(List.of(a, b), List.of(a, b), only(b), ours(b));
        assertEquals(List.of(b), plan.hide());
        assertEquals(List.of(), plan.unhide());
        assertClaims(plan, b);
    }

    @Test
    void neverUnhidesOtherModsRecipes() {
        JeiHiding.Plan<String> plan = JeiHiding.plan(List.of(a, b), List.of(a), none(), ours());
        assertEquals(List.of(), plan.hide());
        assertEquals(List.of(), plan.unhide());
        assertClaims(plan);
    }

    @Test
    void identityNotEquals() {
        String x1 = new String("x");
        String x2 = new String("x");
        JeiHiding.Plan<String> plan =
                JeiHiding.plan(List.of(x1, x2), List.of(x1), r -> r == x2, ours(x2));
        assertEquals(List.of(), plan.hide());
        assertEquals(List.of(), plan.unhide());
        assertEquals(1, plan.hiddenByUs().size());
        assertTrue(plan.hiddenByUs().contains(x2), "x2 is still claimed");
        assertFalse(plan.hiddenByUs().contains(x1), "x1, equal but not identical, is not");
    }

    @Test
    void dropsClaimsOnRecipesGoneFromRuntime() {
        String z = new String("z");
        JeiHiding.Plan<String> plan = JeiHiding.plan(List.of(a), List.of(a), none(), ours(z));
        assertEquals(List.of(), plan.hide());
        assertEquals(List.of(), plan.unhide(), "z is not in the runtime, so it is not unhidden");
        assertClaims(plan);
    }

    @Test
    void emptyCategory() {
        JeiHiding.Plan<String> plan = JeiHiding.plan(List.of(), List.of(), r -> true, ours());
        assertEquals(List.of(), plan.hide());
        assertEquals(List.of(), plan.unhide());
        assertClaims(plan);
    }

    @Test
    void ruleEvaluatedOncePerRecipe() {
        AtomicInteger calls = new AtomicInteger();
        JeiHiding.plan(List.of(a, b, c), List.of(a, b), r -> {
            calls.incrementAndGet();
            return r == b;
        }, ours(c));
        assertEquals(3, calls.get());
    }

    @Test
    void inputsAreNotChanged() {
        Set<String> claimed = ours(b);
        JeiHiding.Plan<String> plan = JeiHiding.plan(List.of(a, b), List.of(a), none(), claimed);
        assertEquals(1, claimed.size(), "the claim passed in is not edited");
        assertTrue(claimed.contains(b));
        assertNotSame(claimed, plan.hiddenByUs(), "the plan holds a new set");
    }

    /** A rule that hides exactly this recipe, by identity. */
    private static Predicate<String> only(String recipe) {
        return r -> r == recipe;
    }

    /** A rule that hides nothing. */
    private static Predicate<String> none() {
        return r -> false;
    }

    /** An identity set holding these recipes, as the bridge keeps its claims. */
    private static Set<String> ours(String... recipes) {
        Set<String> set = JeiHiding.identitySet();
        for (String recipe : recipes) {
            set.add(recipe);
        }
        return set;
    }

    /** The plan claims exactly these recipes, compared by identity. */
    private static void assertClaims(JeiHiding.Plan<String> plan, String... expected) {
        assertEquals(expected.length, plan.hiddenByUs().size(), "claims " + plan.hiddenByUs());
        for (String recipe : expected) {
            boolean found = false;
            for (String claimed : plan.hiddenByUs()) {
                if (claimed == recipe) {
                    found = true;
                }
            }
            assertTrue(found, "claims " + recipe);
        }
    }
}
