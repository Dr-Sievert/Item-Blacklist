package net.sievert.item_blacklist.blacklist;

import net.sievert.item_blacklist.line.Keys;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Registry and tag reads through a receiver typed {@code HolderLookup.Provider}, whose
 * lookupOrThrow has one owner and descriptor on every release. The same call on a receiver
 * typed RegistryAccess compiles to a member that 1.21.2 renamed, so every lookup goes through
 * here: one class holds that rule. May be called by everything that has a provider
 * ({@code server.registryAccess()}, {@code level.registryAccess()}).
 */
public final class Lookups {
    private Lookups() {
    }

    /** provider.lookupOrThrow(registry), compiled against HolderLookup.Provider. */
    public static <T> HolderLookup.RegistryLookup<T> of(HolderLookup.Provider provider,
            ResourceKey<? extends Registry<T>> registry) {
        return provider.lookupOrThrow(registry);
    }

    /** The holder of a key in the provider's registry, if the registry holds it. */
    public static <T> Optional<Holder.Reference<T>> holder(HolderLookup.Provider provider,
            ResourceKey<? extends Registry<T>> registry, ResourceKey<T> key) {
        return of(provider, registry).get(key);
    }

    /**
     * The bound members of a tag as keys, in the tag's order; empty when the tag is not defined
     * or its registry's tags are not bound. Never throws.
     */
    public static <T> Set<ResourceKey<T>> members(HolderLookup.Provider provider,
            ResourceKey<? extends Registry<T>> registry, TagKey<T> tag) {
        try {
            Optional<HolderSet.Named<T>> set = of(provider, registry).get(tag);
            if (set.isEmpty()) {
                return Set.of();
            }
            Set<ResourceKey<T>> keys = new LinkedHashSet<>();
            for (Holder<T> holder : set.get()) {
                holder.unwrapKey().ifPresent(keys::add);
            }
            return Collections.unmodifiableSet(keys);
        } catch (IllegalStateException e) {
            // From 1.21.2 a registry whose tags are not bound yet throws "Tags not bound".
            return Set.of();
        }
    }

    /**
     * Whether the registry knows the tag (1.21.1) or the last tag load defined it (from
     * 1.21.2); false for a tag no data defines, on every release. Never throws. It only words
     * a warning: a configured tag stays in the blacklist either way.
     */
    public static <T> boolean tagKnown(HolderLookup.Provider provider,
            ResourceKey<? extends Registry<T>> registry, TagKey<T> tag) {
        try {
            return of(provider, registry).get(tag).isPresent();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** The keys of the holders of an incoming tag list, in order; keyless holders are skipped. */
    public static <T> Set<ResourceKey<T>> keys(Collection<Holder<T>> holders) {
        Set<ResourceKey<T>> keys = new LinkedHashSet<>();
        for (Holder<T> holder : holders) {
            holder.unwrapKey().ifPresent(keys::add);
        }
        return Collections.unmodifiableSet(keys);
    }

    /**
     * An item's id, "namespace:path", read with getResourceKey and named with Keys.name, the
     * one form that links on every release (Registry.getKey returns the id class); the fallback
     * for an item the registry does not hold.
     */
    public static String itemName(Item item, String fallback) {
        return BuiltInRegistries.ITEM.getResourceKey(item).map(key -> Keys.name(key))
                .orElse(fallback);
    }

    /** The items' ids in order, each as {@link #itemName}. */
    public static List<String> itemNames(List<Item> items, String fallback) {
        List<String> names = new ArrayList<>(items.size());
        for (Item item : items) {
            names.add(itemName(item, fallback));
        }
        return names;
    }
}
