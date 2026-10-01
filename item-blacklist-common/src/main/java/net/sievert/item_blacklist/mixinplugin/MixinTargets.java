package net.sievert.item_blacklist.mixinplugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Classifies the targets a mixin config declares, for the mixin plugin's own-targets check:
 * a required target Mixin did not resolve fails the load, while the target of a @Pseudo mixin,
 * another mod's class, is optional. JDK only, so the rule is unit-tested without a game.
 */
public final class MixinTargets {
    /**
     * missing: "target (in mixin)" for required mixins whose target was not resolved;
     * optionalPresent and optionalAbsent: the targets of @Pseudo mixins, resolved or not.
     */
    public record Verdict(List<String> missing, SortedSet<String> optionalPresent,
            SortedSet<String> optionalAbsent) {
        /** Copies, so a verdict is unmodifiable and its sets are sorted whatever it was given. */
        public Verdict {
            missing = List.copyOf(missing);
            optionalPresent = Collections.unmodifiableSortedSet(new TreeSet<>(optionalPresent));
            optionalAbsent = Collections.unmodifiableSortedSet(new TreeSet<>(optionalAbsent));
        }
    }

    private MixinTargets() {
    }

    /**
     * declared: target to mixin, of every listed mixin; pseudo: the mixins that carry @Pseudo;
     * resolved: the targets Mixin resolved. A declared target that is not resolved is missing,
     * unless its mixin is pseudo: then it is optional and absent.
     */
    public static Verdict classify(Map<String, String> declared, Set<String> pseudo,
            Set<String> resolved) {
        List<String> missing = new ArrayList<>();
        SortedSet<String> present = new TreeSet<>();
        SortedSet<String> absent = new TreeSet<>();
        for (Map.Entry<String, String> entry : declared.entrySet()) {
            boolean found = resolved.contains(entry.getKey());
            if (pseudo.contains(entry.getValue())) {
                (found ? present : absent).add(entry.getKey());
            } else if (!found) {
                missing.add(entry.getKey() + " (in " + entry.getValue() + ")");
            }
        }
        return new Verdict(missing, present, absent);
    }
}
