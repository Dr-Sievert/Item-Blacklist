package net.sievert.item_blacklist.report;

import net.sievert.item_blacklist.id.IdText;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * One flush of a {@link RemovalReport}: per kind, each subject with its causes, every map and
 * set ordered by {@link IdText#PATH_FIRST} and deeply unmodifiable, so it can be read and
 * rendered outside the report's lock while the report takes new records.
 *
 * @param number the flush's number within its report, from 1; 0 for the empty one before any
 * @param reloadFailed the reload this flush closed failed
 * @param entries per kind, subject to causes; a kind without a record is absent
 */
public record ReportSnapshot(int number, boolean reloadFailed,
        Map<Kind, SortedMap<String, SortedSet<String>>> entries) {
    /** What a record is about; see the mapping in {@link Recorder}. */
    public enum Kind {
        /** A configured item. */
        ITEM,
        /** A configured tag, cleared. */
        TAG,
        /** A configured potion. */
        POTION,
        /** A configured enchantment. */
        ENCHANTMENT,
        /** An entry stripped from a tag. */
        TAG_ENTRY,
        /** A recipe removed. */
        RECIPE,
        /** A loot entry removed. */
        LOOT,
        /** A brewing mix removed. */
        BREWING,
        /** An entry removed from a loader's table. */
        LOADER,
        /** A trade offer refused. */
        TRADE
    }

    /** Takes a deep, sorted, unmodifiable copy; empty kinds are left out. */
    public ReportSnapshot {
        entries = copy(Objects.requireNonNull(entries, "entries"));
    }

    /** The snapshot a report holds before its first flush: number 0, nothing recorded. */
    static ReportSnapshot empty() {
        return new ReportSnapshot(0, false, Map.of());
    }

    /** Distinct (subject, cause) pairs of a kind, the "" cause included; 0 for an absent kind. */
    public int count(Kind kind) {
        SortedMap<String, SortedSet<String>> subjects = entries.get(kind);
        if (subjects == null) {
            return 0;
        }
        int pairs = 0;
        for (SortedSet<String> causes : subjects.values()) {
            pairs += causes.size();
        }
        return pairs;
    }

    /** The subjects of a kind, in PATH_FIRST order; empty for an absent kind. */
    public Set<String> subjects(Kind kind) {
        SortedMap<String, SortedSet<String>> subjects = entries.get(kind);
        return subjects == null ? Collections.emptySortedSet() : subjects.keySet();
    }

    private static Map<Kind, SortedMap<String, SortedSet<String>>> copy(
            Map<Kind, ? extends Map<String, ? extends Set<String>>> source) {
        Map<Kind, SortedMap<String, SortedSet<String>>> kinds = new EnumMap<>(Kind.class);
        for (Map.Entry<Kind, ? extends Map<String, ? extends Set<String>>> kind
                : source.entrySet()) {
            if (kind.getValue() == null || kind.getValue().isEmpty()) {
                continue;
            }
            SortedMap<String, SortedSet<String>> subjects = new TreeMap<>(IdText.PATH_FIRST);
            for (Map.Entry<String, ? extends Set<String>> subject : kind.getValue().entrySet()) {
                SortedSet<String> causes = new TreeSet<>(IdText.PATH_FIRST);
                causes.addAll(subject.getValue());
                subjects.put(subject.getKey(), Collections.unmodifiableSortedSet(causes));
            }
            kinds.put(kind.getKey(), Collections.unmodifiableSortedMap(subjects));
        }
        return Collections.unmodifiableMap(kinds);
    }
}
