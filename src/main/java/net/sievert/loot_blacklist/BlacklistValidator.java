package net.sievert.loot_blacklist;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class BlacklistValidator {
    private BlacklistValidator() {}

    /**
     * Vanilla validation: only handles scenario 2 (invalid vanilla IDs).
     * Returns the number of invalid vanilla entries (for totalInvalid accounting).
     * - Skips entries that don't contain ':' (scenario 1) — those are handled in modded validation.
     * - Fills `output` with valid vanilla Identifiers.
     */
    public static int validateVanillaOnly(Set<String> input, Logger logger, Set<Identifier> output) {
        Set<String> invalidVanilla = new HashSet<>();
        int invalidCount = 0;

        for (String raw : input) {
            // Skip scenario 1 here — we don't know modded state yet
            if (raw == null || !raw.contains(":")) continue;

            Identifier id;
            try {
                id = Identifier.of(raw);
            } catch (Exception ex) {
                // Broken identifier — treat as scenario 1 (skip here)
                continue;
            }

            if ("minecraft".equals(id.getNamespace())) {
                if (Registries.ITEM.containsId(id)) {
                    output.add(id);
                } else {
                    invalidVanilla.add(raw);
                    invalidCount++;
                }
            }
        }

        if (!invalidVanilla.isEmpty()) {
            logger.warn(
                    "Invalid vanilla blacklist entr{}: {}",
                    invalidVanilla.size() == 1 ? "y" : "ies",
                    quoteJoin(invalidVanilla)
            );
        }

        logger.info("Vanilla blacklist validated: {} item(s)", output.size());
        return invalidCount;
    }

    /**
     * Modded validation: logs & counts scenario 1 (invalid identifier) AND scenario 3 (invalid modded ID).
     * Returns set of valid modded Identifiers.
     * seenInvalid prevents duplicate logging of the same raw entry.
     * invalidCounter.count is incremented for each unique invalid logged here.
     */
    public static Set<Identifier> validateModdedOnly(Set<String> input, Logger logger, Counter invalidCounter, Set<String> seenInvalid) {
        Set<Identifier> valid = new HashSet<>();
        Set<String> invalidModded = new HashSet<>();

        for (String raw : input) {
            if (raw == null) continue;

            // Scenario 1: missing colon / obviously invalid
            if (!raw.contains(":")) {
                if (seenInvalid.add(raw)) {
                    logger.warn("Invalid blacklist entry: \"{}\"", raw);
                    invalidCounter.count++;
                }
                continue;
            }

            Identifier id;
            try {
                id = Identifier.of(raw);
            } catch (Exception ex) {
                if (seenInvalid.add(raw)) {
                    logger.warn("Invalid blacklist entry: \"{}\"", raw);
                    invalidCounter.count++;
                }
                continue;
            }

            // Scenario 3: modded namespace entries that don't exist
            if (!"minecraft".equals(id.getNamespace())) {
                if (Registries.ITEM.containsId(id)) {
                    valid.add(id);
                } else {
                    if (seenInvalid.add(raw)) {
                        invalidModded.add(raw);
                        invalidCounter.count++;
                    }
                }
            }
        }

        if (!invalidModded.isEmpty()) {
            logger.warn(
                    "Invalid modded blacklist entr{}: {}",
                    invalidModded.size() == 1 ? "y" : "ies",
                    quoteJoin(invalidModded)
            );
        }

        logger.info("Modded blacklist validated: {} item(s)", valid.size());
        return valid;
    }

    private static String quoteJoin(Set<String> entries) {
        return entries.stream()
                .map(s -> "\"" + s + "\"")
                .collect(Collectors.joining(", "));
    }

    public static class Counter {
        public int count = 0;
    }
}
