package net.sievert.item_blacklist.mixinplugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The rule the mixin plugin fails the load by, with no game running: a required mixin's target
 * that Mixin did not resolve is missing, while a @Pseudo mixin's target is optional, present or
 * absent, and never missing.
 */
class MixinTargetsTest {
    @Test
    void anUnresolvedRequiredTargetIsMissing() {
        MixinTargets.Verdict verdict = MixinTargets.classify(
                Map.of("a.B", "m.M"), Set.of(), Set.of());
        assertEquals(List.of("a.B (in m.M)"), verdict.missing());
        assertTrue(verdict.optionalPresent().isEmpty());
        assertTrue(verdict.optionalAbsent().isEmpty());
    }

    @Test
    void anUnresolvedPseudoTargetIsOptionalAndAbsent() {
        MixinTargets.Verdict verdict = MixinTargets.classify(
                Map.of("other.C", "m.P"), Set.of("m.P"), Set.of());
        assertEquals(List.of(), verdict.missing());
        assertEquals(new TreeSet<>(Set.of("other.C")), verdict.optionalAbsent());
        assertTrue(verdict.optionalPresent().isEmpty());
    }

    @Test
    void aResolvedPseudoTargetIsOptionalAndPresent() {
        MixinTargets.Verdict verdict = MixinTargets.classify(
                Map.of("other.C", "m.P"), Set.of("m.P"), Set.of("other.C"));
        assertEquals(List.of(), verdict.missing());
        assertEquals(new TreeSet<>(Set.of("other.C")), verdict.optionalPresent());
        assertTrue(verdict.optionalAbsent().isEmpty());
    }

    @Test
    void everyTargetResolvedGivesAnEmptyVerdict() {
        MixinTargets.Verdict verdict = MixinTargets.classify(
                Map.of("a.B", "m.M", "other.C", "m.P"), Set.of("m.P"), Set.of("a.B", "other.C"));
        assertEquals(List.of(), verdict.missing());
        assertTrue(verdict.optionalAbsent().isEmpty());
        assertEquals(new TreeSet<>(Set.of("other.C")), verdict.optionalPresent());
        MixinTargets.Verdict required = MixinTargets.classify(
                Map.of("a.B", "m.M"), Set.of(), Set.of("a.B"));
        assertEquals(List.of(), required.missing());
        assertTrue(required.optionalPresent().isEmpty());
        assertTrue(required.optionalAbsent().isEmpty());
    }

    @Test
    void setsAreSortedAndUnmodifiable() {
        MixinTargets.Verdict verdict = MixinTargets.classify(
                Map.of("z.Z", "m.P", "a.A", "m.P", "k.K", "m.P", "y.Y", "m.Q", "b.B", "m.Q"),
                Set.of("m.P", "m.Q"), Set.of("k.K", "y.Y"));
        assertEquals(List.of("a.A", "b.B", "z.Z"), List.copyOf(verdict.optionalAbsent()));
        assertEquals(List.of("k.K", "y.Y"), List.copyOf(verdict.optionalPresent()));
        assertThrows(UnsupportedOperationException.class, () -> verdict.missing().add("x"));
        assertThrows(UnsupportedOperationException.class,
                () -> verdict.optionalPresent().add("x"));
        assertThrows(UnsupportedOperationException.class,
                () -> verdict.optionalAbsent().add("x"));
        TreeSet<String> given = new TreeSet<>(List.of("b", "a"));
        MixinTargets.Verdict copied = new MixinTargets.Verdict(List.of(), given, given);
        given.add("c");
        assertEquals(List.of("a", "b"), List.copyOf(copied.optionalPresent()));
        assertEquals(List.of("a", "b"), List.copyOf(copied.optionalAbsent()));
    }
}
