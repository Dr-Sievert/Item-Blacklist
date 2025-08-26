package net.sievert.item_blacklist.mixin;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagGroupLoader;
import net.minecraft.util.Identifier;
import net.sievert.item_blacklist.BlacklistConfig;
import net.sievert.item_blacklist.BlacklistTags;
import net.sievert.item_blacklist.ItemBlacklist;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

import static net.sievert.item_blacklist.BlacklistLogger.*;
import static net.sievert.item_blacklist.BlacklistLogger.Group.*;

/**
 * Mixin for TagGroupLoader.
 * Applies blacklist filtering to tag entries after groups are built,
 * tracks removed entries, and triggers global summary logic.
 */
@Mixin(TagGroupLoader.class)
public abstract class TagGroupLoaderMixin<T> {

    /**
     * Injects after TagGroupLoader#buildGroup to filter out blacklisted entries,
     * record per-entry removals, and notify global summary state.
     */
    @Inject(
            method = "buildGroup",
            at = @At("RETURN"),
            cancellable = true
    )
    private void item_blacklist$filterBuiltTags(
            Map<Identifier, List<TagGroupLoader.TrackedEntry>> tags,
            CallbackInfoReturnable<Map<Identifier, Collection<T>>> cir
    ) {
        BlacklistConfig config = ItemBlacklist.CONFIG;
        if (config == null || config.blacklist.isEmpty()) {
            BlacklistTags.warnNoConfig();
            return;
        }

        Map<Identifier, Collection<T>> original = cir.getReturnValue();
        Map<Identifier, Collection<T>> rebuilt = new HashMap<>();

        for (Map.Entry<Identifier, Collection<T>> entry : original.entrySet()) {
            Identifier tagId = entry.getKey();
            Collection<T> values = entry.getValue();

            List<T> filtered = new ArrayList<>();
            for (T val : values) {
                if (val instanceof RegistryEntry<?> regEntry) {
                    Identifier id = regEntry.getKey().map(RegistryKey::getValue).orElse(null);
                    if (id != null && config.blacklist.contains(id)) {
                        BlacklistTags.reportRemoval(tagId, id);
                        if (config.detailedTagLog) {
                            info(TAG, "Removed " + id + " from tag " + tagId);
                        }
                        continue;
                    }
                }
                filtered.add(val);
            }
            rebuilt.put(tagId, List.copyOf(filtered));
        }

        cir.setReturnValue(rebuilt);

        BlacklistTags.reportRegistryFiltered(config.detailedTagLog);
    }
}
