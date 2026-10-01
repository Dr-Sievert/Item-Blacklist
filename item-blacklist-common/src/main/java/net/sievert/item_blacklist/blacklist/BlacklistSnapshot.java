package net.sievert.item_blacklist.blacklist;

import net.sievert.item_blacklist.id.IdText;
import net.sievert.item_blacklist.line.Keys;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * The blacklist as one immutable value: what a server resolved from its config and what its
 * last tag load derived. Hooks read it on any thread without a lock, since nothing in it ever
 * changes; a change is a new snapshot, published whole. Every collection it returns is
 * unmodifiable and ordered by {@link IdText#PATH_FIRST} of its names, and every query is one
 * hash lookup.
 */
public final class BlacklistSnapshot implements IdQuery {
    private static final AtomicLong GENERATIONS = new AtomicLong();

    /**
     * No entry at all: what every hook sees without a server and without a synced blacklist.
     * Built from empty collections, so its class init calls no Keys backend and no registry.
     */
    public static final BlacklistSnapshot EMPTY = new BlacklistSnapshot(0L, Set.of(), Map.of(),
            Map.of(), Set.of(), Set.of(), Map.of(), Set.of());

    private final long generation;
    private final Set<ResourceKey<Item>> explicitItems;
    /** Explicit and tag-derived items that exist and are not air. */
    private final Set<ResourceKey<Item>> items;
    /** The same items as objects, compared by identity, for the per-stack query. */
    private final Set<Item> itemIdentity;
    /** Key set: the configured item tags; values: their members before the strip. */
    private final Map<TagKey<Item>, Set<ResourceKey<Item>>> itemTagMembers;
    private final Map<TagKey<Item>, Set<ResourceKey<Item>>> emptiedItemTags;
    private final Set<ResourceKey<Potion>> potions;
    private final Set<ResourceKey<Enchantment>> explicitEnchantments;
    /** Explicit and tag-derived enchantments. */
    private final Set<ResourceKey<Enchantment>> enchantments;
    private final Map<TagKey<Enchantment>, Set<ResourceKey<Enchantment>>> enchantmentTagMembers;
    private final Set<TagKey<Enchantment>> emptiedEnchantmentTags;
    private final Set<String> itemNames;
    private final Set<String> itemTagNames;
    private final Set<String> potionNames;
    private final Set<String> enchantmentNames;

    /**
     * The one constructor: copies and sorts what it is given and derives the rest, so a
     * snapshot is consistent whichever way it was made (the builder, a tag load, a payload).
     */
    private BlacklistSnapshot(long generation,
            Collection<ResourceKey<Item>> explicitItems,
            Map<TagKey<Item>, ? extends Collection<ResourceKey<Item>>> itemTagMembers,
            Map<TagKey<Item>, ? extends Collection<ResourceKey<Item>>> emptiedItemTags,
            Collection<ResourceKey<Potion>> potions,
            Collection<ResourceKey<Enchantment>> explicitEnchantments,
            Map<TagKey<Enchantment>, ? extends Collection<ResourceKey<Enchantment>>>
                    enchantmentTagMembers,
            Collection<TagKey<Enchantment>> emptiedEnchantmentTags) {
        this.generation = generation;
        this.itemTagMembers = sortedTags(itemTagMembers);
        this.emptiedItemTags = sortedTags(emptiedItemTags);
        this.potions = sortedKeys(potions);
        this.enchantmentTagMembers = sortedTags(enchantmentTagMembers);
        this.emptiedEnchantmentTags = sortedTagSet(emptiedEnchantmentTags);

        // A key without an item, and air, are dropped from both item sets: an empty stack is
        // air, and a blacklisted air would blacklist every empty stack.
        List<ResourceKey<Item>> explicitKept = new ArrayList<>();
        for (ResourceKey<Item> key : explicitItems) {
            if (itemOf(key).isPresent()) {
                explicitKept.add(key);
            }
        }
        this.explicitItems = sortedKeys(explicitKept);
        List<ResourceKey<Item>> all = new ArrayList<>(this.explicitItems);
        for (Set<ResourceKey<Item>> members : this.itemTagMembers.values()) {
            all.addAll(members);
        }
        Set<Item> identity = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ResourceKey<Item>> itemsKept = new ArrayList<>();
        for (ResourceKey<Item> key : all) {
            Optional<Item> item = itemOf(key);
            if (item.isPresent()) {
                identity.add(item.get());
                itemsKept.add(key);
            }
        }
        this.items = sortedKeys(itemsKept);
        this.itemIdentity = Collections.unmodifiableSet(identity);

        this.explicitEnchantments = sortedKeys(explicitEnchantments);
        List<ResourceKey<Enchantment>> allEnchantments = new ArrayList<>(this.explicitEnchantments);
        for (Set<ResourceKey<Enchantment>> members : this.enchantmentTagMembers.values()) {
            allEnchantments.addAll(members);
        }
        this.enchantments = sortedKeys(allEnchantments);

        this.itemNames = names(this.items, BlacklistSnapshot::keyName);
        this.itemTagNames = names(this.itemTagMembers.keySet(), BlacklistSnapshot::tagName);
        this.potionNames = names(this.potions, BlacklistSnapshot::keyName);
        this.enchantmentNames = names(this.enchantments, BlacklistSnapshot::keyName);
    }

    /** A builder for a snapshot of explicit entries and configured tags with their members. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A JVM-wide counter's value, taken when this snapshot was made; 0 for EMPTY. Compared by
     * readers that cache what they derived from a snapshot, to see whether anything changed.
     */
    public long generation() {
        return generation;
    }

    /** No explicit entry and no configured tag: every query answers false. */
    public boolean isEmpty() {
        return explicitItems.isEmpty() && itemTagMembers.isEmpty() && potions.isEmpty()
                && explicitEnchantments.isEmpty() && enchantmentTagMembers.isEmpty();
    }

    /** Whether the item is blacklisted, explicitly or through a configured tag. */
    public boolean item(Item item) {
        return itemIdentity.contains(item);
    }

    /** Whether the item of this key is blacklisted, explicitly or through a configured tag. */
    public boolean item(ResourceKey<Item> key) {
        return items.contains(key);
    }

    /** Whether the config names this item tag. */
    public boolean itemTag(TagKey<Item> tag) {
        return itemTagMembers.containsKey(tag);
    }

    /** Whether the config names this item tag, or the last strip left it without a member. */
    public boolean itemTagEmptied(TagKey<Item> tag) {
        return itemTag(tag) || emptiedItemTags.containsKey(tag);
    }

    /** Whether the potion of this key is blacklisted. */
    public boolean potion(ResourceKey<Potion> key) {
        return potions.contains(key);
    }

    /** Whether the potion is blacklisted; a holder without a key never is. */
    public boolean potion(Holder<Potion> holder) {
        return holder.unwrapKey().map(potions::contains).orElse(false);
    }

    /** Whether the enchantment of this key is blacklisted, explicitly or through a tag. */
    public boolean enchantment(ResourceKey<Enchantment> key) {
        return enchantments.contains(key);
    }

    /** Whether the enchantment is blacklisted; a holder without a key never is. */
    public boolean enchantment(Holder<Enchantment> holder) {
        return holder.unwrapKey().map(enchantments::contains).orElse(false);
    }

    /** Whether the config names this enchantment tag. */
    public boolean enchantmentTag(TagKey<Enchantment> tag) {
        return enchantmentTagMembers.containsKey(tag);
    }

    /** Whether the config names this enchantment tag, or the last strip emptied it. */
    public boolean enchantmentTagEmptied(TagKey<Enchantment> tag) {
        return enchantmentTag(tag) || emptiedEnchantmentTags.contains(tag);
    }

    /** Explicit and tag-derived items. */
    public Set<ResourceKey<Item>> items() {
        return items;
    }

    /** The items the config names itself. */
    public Set<ResourceKey<Item>> explicitItems() {
        return explicitItems;
    }

    /** The item tags the config names, a tag no data defines included. */
    public Set<TagKey<Item>> itemTags() {
        return itemTagMembers.keySet();
    }

    /** Per configured item tag, its members as the last tag load read them, before the strip. */
    public Map<TagKey<Item>, Set<ResourceKey<Item>>> itemTagMembers() {
        return itemTagMembers;
    }

    /** Tags the config does not name whose members the last strip removed, with those members. */
    public Map<TagKey<Item>, Set<ResourceKey<Item>>> emptiedItemTags() {
        return emptiedItemTags;
    }

    /** The blacklisted potions. */
    public Set<ResourceKey<Potion>> potions() {
        return potions;
    }

    /** Explicit and tag-derived enchantments. */
    public Set<ResourceKey<Enchantment>> enchantments() {
        return enchantments;
    }

    /** The enchantments the config names itself. */
    public Set<ResourceKey<Enchantment>> explicitEnchantments() {
        return explicitEnchantments;
    }

    /** The enchantment tags the config names. */
    public Set<TagKey<Enchantment>> enchantmentTags() {
        return enchantmentTagMembers.keySet();
    }

    /** Per configured enchantment tag, its members before the strip. */
    public Map<TagKey<Enchantment>, Set<ResourceKey<Enchantment>>> enchantmentTagMembers() {
        return enchantmentTagMembers;
    }

    /** Enchantment tags the config does not name that the last strip emptied. */
    public Set<TagKey<Enchantment>> emptiedEnchantmentTags() {
        return emptiedEnchantmentTags;
    }

    /**
     * This blacklist with the item tags of a new tag load: each configured tag gets its members
     * from {@code members} (none when absent there), so the configured set never changes; the
     * derived items are rebuilt; the potion and enchantment halves stay. A new generation.
     */
    public BlacklistSnapshot withItemTags(Map<TagKey<Item>, Set<ResourceKey<Item>>> members,
            Map<TagKey<Item>, Set<ResourceKey<Item>>> emptied) {
        Map<TagKey<Item>, Set<ResourceKey<Item>>> next = new LinkedHashMap<>();
        for (TagKey<Item> tag : itemTagMembers.keySet()) {
            Set<ResourceKey<Item>> given = members.get(tag);
            next.put(tag, given == null ? Set.of() : given);
        }
        return new BlacklistSnapshot(GENERATIONS.incrementAndGet(), explicitItems, next, emptied,
                potions, explicitEnchantments, enchantmentTagMembers, emptiedEnchantmentTags);
    }

    /**
     * This blacklist with the enchantment tags of a new tag load, as {@link #withItemTags} does
     * for items; the item and potion halves stay. A new generation.
     */
    public BlacklistSnapshot withEnchantmentTags(
            Map<TagKey<Enchantment>, Set<ResourceKey<Enchantment>>> members,
            Set<TagKey<Enchantment>> emptied) {
        Map<TagKey<Enchantment>, Set<ResourceKey<Enchantment>>> next = new LinkedHashMap<>();
        for (TagKey<Enchantment> tag : enchantmentTagMembers.keySet()) {
            Set<ResourceKey<Enchantment>> given = members.get(tag);
            next.put(tag, given == null ? Set.of() : given);
        }
        return new BlacklistSnapshot(GENERATIONS.incrementAndGet(), explicitItems,
                itemTagMembers, emptiedItemTags, potions, explicitEnchantments, next, emptied);
    }

    @Override
    public boolean itemId(String id) {
        return itemNames.contains(id);
    }

    @Override
    public boolean itemTagId(String id) {
        return itemTagNames.contains(id);
    }

    @Override
    public boolean potionId(String id) {
        return potionNames.contains(id);
    }

    @Override
    public boolean enchantmentId(String id) {
        return enchantmentNames.contains(id);
    }

    /**
     * The item of a key, unless the key names no item or names air. containsKey first: the
     * item registry has a default value, so a lookup alone could answer air for a missing key.
     */
    private static Optional<Item> itemOf(ResourceKey<Item> key) {
        if (!BuiltInRegistries.ITEM.containsKey(key)) {
            return Optional.empty();
        }
        return BuiltInRegistries.ITEM.getOptional(key).filter(item -> item != Items.AIR);
    }

    private static String keyName(ResourceKey<?> key) {
        return Keys.name(key);
    }

    private static String tagName(TagKey<?> tag) {
        return Keys.name(tag);
    }

    /** The values, without duplicates, in PATH_FIRST order of their names. */
    private static <K> List<K> sorted(Collection<K> values, Function<? super K, String> name) {
        TreeMap<String, K> byName = new TreeMap<>(IdText.PATH_FIRST);
        for (K value : values) {
            byName.put(name.apply(value), value);
        }
        return new ArrayList<>(byName.values());
    }

    private static <T> Set<ResourceKey<T>> sortedKeys(Collection<ResourceKey<T>> keys) {
        return Collections.unmodifiableSet(
                new LinkedHashSet<>(sorted(keys, BlacklistSnapshot::keyName)));
    }

    private static <T> Set<TagKey<T>> sortedTagSet(Collection<TagKey<T>> tags) {
        return Collections.unmodifiableSet(
                new LinkedHashSet<>(sorted(tags, BlacklistSnapshot::tagName)));
    }

    private static <T> Map<TagKey<T>, Set<ResourceKey<T>>> sortedTags(
            Map<TagKey<T>, ? extends Collection<ResourceKey<T>>> tags) {
        Map<TagKey<T>, Set<ResourceKey<T>>> map = new LinkedHashMap<>();
        for (TagKey<T> tag : sorted(tags.keySet(), BlacklistSnapshot::tagName)) {
            Collection<ResourceKey<T>> members = tags.get(tag);
            map.put(tag, members == null ? Set.of() : sortedKeys(members));
        }
        return Collections.unmodifiableMap(map);
    }

    private static <K> Set<String> names(Collection<K> values, Function<? super K, String> name) {
        Set<String> names = new HashSet<>();
        for (K value : values) {
            names.add(name.apply(value));
        }
        return Collections.unmodifiableSet(names);
    }

    /**
     * Collects explicit entries and configured tags with their members; build() sorts them and
     * derives the rest. A second call for the same tag merges its members.
     */
    public static final class Builder {
        private final Set<ResourceKey<Item>> items = new LinkedHashSet<>();
        private final Map<TagKey<Item>, Set<ResourceKey<Item>>> itemTags = new LinkedHashMap<>();
        private final Set<ResourceKey<Potion>> potions = new LinkedHashSet<>();
        private final Set<ResourceKey<Enchantment>> enchantments = new LinkedHashSet<>();
        private final Map<TagKey<Enchantment>, Set<ResourceKey<Enchantment>>> enchantmentTags =
                new LinkedHashMap<>();

        private Builder() {
        }

        /** An item the config names. */
        public Builder item(ResourceKey<Item> key) {
            items.add(key);
            return this;
        }

        /** A configured item tag with its members as bound now; members may be empty. */
        public Builder itemTag(TagKey<Item> tag, Collection<ResourceKey<Item>> members) {
            itemTags.computeIfAbsent(tag, t -> new LinkedHashSet<>()).addAll(members);
            return this;
        }

        /** A potion the config names. */
        public Builder potion(ResourceKey<Potion> key) {
            potions.add(key);
            return this;
        }

        /** An enchantment the config names. */
        public Builder enchantment(ResourceKey<Enchantment> key) {
            enchantments.add(key);
            return this;
        }

        /** A configured enchantment tag with its members as bound now; members may be empty. */
        public Builder enchantmentTag(TagKey<Enchantment> tag,
                Collection<ResourceKey<Enchantment>> members) {
            enchantmentTags.computeIfAbsent(tag, t -> new LinkedHashSet<>()).addAll(members);
            return this;
        }

        /** The snapshot, with a new generation; nothing emptied yet (only a tag load knows). */
        public BlacklistSnapshot build() {
            return new BlacklistSnapshot(GENERATIONS.incrementAndGet(), items, itemTags, Map.of(),
                    potions, enchantments, enchantmentTags, Set.of());
        }
    }
}
