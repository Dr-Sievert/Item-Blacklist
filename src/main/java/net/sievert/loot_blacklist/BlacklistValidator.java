package net.sievert.loot_blacklist;

import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static net.sievert.loot_blacklist.LootBlacklistLogger.*;
import static net.sievert.loot_blacklist.LootBlacklistLogger.Group.*;

public final class BlacklistValidator {
    private BlacklistValidator() {}

    public static int validateVanillaOnly(Set<String> input, Set<Identifier> output) {
        Set<String> invalidVanilla = new HashSet<>();
        int invalidCount = 0;

        for (String raw : input) {
            if (raw == null || !raw.contains(":")) continue;

            Identifier id;
            try {
                id = Identifier.of(raw);
            } catch (Exception ex) {
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
            warn(VALIDATION,
                    "Invalid vanilla blacklist " +
                            pluralize(invalidVanilla.size(), "entry", "entries") +
                            ": " + quoteJoin(invalidVanilla)
            );
        }

        info(VALIDATION, "Vanilla blacklist validated: " +
                output.size() + " " + pluralize(output.size(), "item", "items"));
        return invalidCount;
    }

    public static Set<Identifier> validateModdedOnly(Set<String> input, Counter invalidCounter, Set<String> seenInvalid) {
        Set<Identifier> valid = new HashSet<>();
        Set<String> invalidModded = new HashSet<>();

        for (String raw : input) {
            if (raw == null) continue;
            if (!raw.contains(":")) {
                if (seenInvalid.add(raw)) {
                    warn(VALIDATION, "Invalid blacklist entry: \"" + raw + "\"");
                    invalidCounter.count++;
                }
                continue;
            }

            Identifier id;
            try {
                id = Identifier.of(raw);
            } catch (Exception ex) {
                if (seenInvalid.add(raw)) {
                    warn(VALIDATION, "Invalid blacklist entry: \"" + raw + "\"");
                    invalidCounter.count++;
                }
                continue;
            }

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
            warn(VALIDATION,
                    "Invalid modded blacklist " +
                            pluralize(invalidModded.size(), "entry", "entries") +
                            ": " + quoteJoin(invalidModded)
            );
        }

        info(VALIDATION, "Modded blacklist validated: " +
                valid.size() + " " + pluralize(valid.size(), "item", "items"));
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
