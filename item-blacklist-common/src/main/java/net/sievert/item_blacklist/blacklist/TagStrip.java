package net.sievert.item_blacklist.blacklist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The strip of one tag load, written without game types so JUnit can test it (TagStripTest).
 * Used by TagFilter only.
 */
final class TagStrip {
    /**
     * The outcome of one strip, both maps unmodifiable.
     *
     * @param tags every incoming key, in incoming order
     * @param emptied keys not cleared whose non-empty list lost every value, with that incoming
     *     list
     */
    record Outcome<K, V>(Map<K, List<V>> tags, Map<K, List<V>> emptied) {
    }

    private TagStrip() {
    }

    /**
     * Every key of incoming stays. A key that cleared accepts is bound to List.of(); from every
     * other list the values removed accepts go. onCleared runs once per cleared key; onRemoved
     * once per value that leaves a list, the values of cleared lists included. The incoming map
     * and its lists are never changed; a list that loses nothing is the incoming instance.
     */
    static <K, V> Outcome<K, V> strip(Map<K, List<V>> incoming,
            Predicate<? super K> cleared, Predicate<? super V> removed,
            Consumer<? super K> onCleared, BiConsumer<? super K, ? super V> onRemoved) {
        Map<K, List<V>> tags = new LinkedHashMap<>();
        Map<K, List<V>> emptied = new LinkedHashMap<>();
        for (Map.Entry<K, List<V>> entry : incoming.entrySet()) {
            K key = entry.getKey();
            List<V> values = entry.getValue();
            if (cleared.test(key)) {
                onCleared.accept(key);
                for (V value : values) {
                    onRemoved.accept(key, value);
                }
                tags.put(key, List.of());
                continue;
            }
            List<V> kept = null;              // created at the first removal
            int index = 0;
            for (V value : values) {
                if (removed.test(value)) {
                    if (kept == null) {
                        kept = new ArrayList<>(values.subList(0, index));
                    }
                    onRemoved.accept(key, value);
                } else if (kept != null) {
                    kept.add(value);
                }
                index++;
            }
            if (kept == null) {
                tags.put(key, values);
            } else if (kept.isEmpty()) {
                // At least one value went, so the incoming list was not empty.
                tags.put(key, List.of());
                emptied.put(key, values);
            } else {
                tags.put(key, List.copyOf(kept));
            }
        }
        return new Outcome<>(Collections.unmodifiableMap(tags),
                Collections.unmodifiableMap(emptied));
    }
}
