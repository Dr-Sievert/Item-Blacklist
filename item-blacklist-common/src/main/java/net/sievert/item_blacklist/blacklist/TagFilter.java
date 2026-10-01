package net.sievert.item_blacklist.blacklist;

import net.sievert.item_blacklist.line.Keys;
import net.sievert.item_blacklist.report.Recorder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;

/**
 * The tag rule: what one registry's tag load binds, given a snapshot. Pure: no log line, no
 * state; called by Blacklist.onTagLoad and by scenarios. Members of the configured tags are
 * read from the incoming, unfiltered map, the new blacklist is built from them, and only then
 * is the strip made with it, so an item that only a configured tag names leaves every other
 * tag in the same load.
 */
public final class TagFilter {
    /**
     * What one load binds and what it publishes.
     *
     * @param tags the map to bind: every incoming key, blacklisted entries gone
     * @param snapshot the snapshot to publish; the input snapshot when nothing it holds changed
     */
    public record Result<T>(Map<TagKey<T>, List<Holder<T>>> tags, BlacklistSnapshot snapshot) {
    }

    /** The registry argument of Recorder.blacklistedTag and Recorder.tagRemoval. */
    static final String ITEM = "item";
    static final String BLOCK = "block";
    static final String ENCHANTMENT = "enchantment";
    static final String POTION = "potion";

    private TagFilter() {
    }

    /**
     * Registry ITEM, BLOCK, ENCHANTMENT, POTION: filtered; any other, or an empty snapshot: the
     * incoming map itself. Every incoming key is kept; a configured tag, and a tag whose members
     * all went, are bound to an empty list; the incoming map is never changed.
     */
    @SuppressWarnings("unchecked")
    public static <T> Result<T> apply(BlacklistSnapshot snapshot,
            ResourceKey<? extends Registry<T>> registry,
            Map<TagKey<T>, List<Holder<T>>> incoming, Recorder recorder) {
        if (snapshot.isEmpty()) {
            return new Result<>(incoming, snapshot);
        }
        // ResourceKey has no equals override and its instances are interned: an identity test.
        if (Registries.ITEM.equals(registry)) {
            return (Result<T>) (Result<?>) items(snapshot,
                    (Map<TagKey<Item>, List<Holder<Item>>>) (Map<?, ?>) incoming, recorder);
        }
        if (Registries.BLOCK.equals(registry)) {
            return (Result<T>) (Result<?>) blocks(snapshot,
                    (Map<TagKey<Block>, List<Holder<Block>>>) (Map<?, ?>) incoming, recorder);
        }
        if (Registries.ENCHANTMENT.equals(registry)) {
            return (Result<T>) (Result<?>) enchantments(snapshot,
                    (Map<TagKey<Enchantment>, List<Holder<Enchantment>>>) (Map<?, ?>) incoming,
                    recorder);
        }
        if (Registries.POTION.equals(registry)) {
            return (Result<T>) (Result<?>) potions(snapshot,
                    (Map<TagKey<Potion>, List<Holder<Potion>>>) (Map<?, ?>) incoming, recorder);
        }
        return new Result<>(incoming, snapshot);
    }

    /** ITEM: members from incoming, then the new snapshot, then the strip with it. */
    private static Result<Item> items(BlacklistSnapshot snapshot,
            Map<TagKey<Item>, List<Holder<Item>>> incoming, Recorder recorder) {
        if (snapshot.explicitItems().isEmpty() && snapshot.itemTags().isEmpty()) {
            return new Result<>(incoming, snapshot);
        }
        Map<TagKey<Item>, Set<ResourceKey<Item>>> members = members(snapshot.itemTags(), incoming);
        // The new blacklist; which tags end emptied is not known yet, and no query of the
        // strip reads it.
        BlacklistSnapshot probe = snapshot.withItemTags(members, Map.of());
        TagStrip.Outcome<TagKey<Item>, Holder<Item>> out = TagStrip.strip(incoming,
                tag -> probe.itemTag(tag),
                holder -> probe.item(holder.value()),
                tag -> recorder.blacklistedTag(ITEM, Keys.name(tag)),
                (tag, holder) -> recordRemoval(recorder, ITEM, tag, holder));
        Map<TagKey<Item>, Set<ResourceKey<Item>>> emptied = new HashMap<>();
        out.emptied().forEach((tag, list) -> emptied.put(tag, Lookups.keys(list)));
        return new Result<>(out.tags(), snapshot.withItemTags(members, emptied));
    }

    /**
     * BLOCK: a block goes when its item (not air) is blacklisted; the snapshot is unchanged. A
     * configured item tag id is not cleared in the block registry, as the old mod did not.
     */
    private static Result<Block> blocks(BlacklistSnapshot snapshot,
            Map<TagKey<Block>, List<Holder<Block>>> incoming, Recorder recorder) {
        if (snapshot.items().isEmpty()) {
            return new Result<>(incoming, snapshot);
        }
        // The snapshot read here is the one the ITEM pass of the same load published: the tag
        // mixins sort the item registry first.
        TagStrip.Outcome<TagKey<Block>, Holder<Block>> out = TagStrip.strip(incoming,
                tag -> false,
                holder -> blockBlacklisted(snapshot, holder.value()),
                tag -> {
                },
                (tag, holder) -> recordRemoval(recorder, BLOCK, tag, holder));
        return new Result<>(out.tags(), snapshot);
    }

    /** ENCHANTMENT: as ITEM, with the enchantment half of the snapshot. */
    private static Result<Enchantment> enchantments(BlacklistSnapshot snapshot,
            Map<TagKey<Enchantment>, List<Holder<Enchantment>>> incoming, Recorder recorder) {
        if (snapshot.enchantments().isEmpty() && snapshot.enchantmentTags().isEmpty()) {
            return new Result<>(incoming, snapshot);
        }
        Map<TagKey<Enchantment>, Set<ResourceKey<Enchantment>>> members =
                members(snapshot.enchantmentTags(), incoming);
        BlacklistSnapshot probe = snapshot.withEnchantmentTags(members, Set.of());
        TagStrip.Outcome<TagKey<Enchantment>, Holder<Enchantment>> out = TagStrip.strip(incoming,
                tag -> probe.enchantmentTag(tag),
                holder -> probe.enchantment(holder),
                tag -> recorder.blacklistedTag(ENCHANTMENT, Keys.name(tag)),
                (tag, holder) -> recordRemoval(recorder, ENCHANTMENT, tag, holder));
        return new Result<>(out.tags(),
                snapshot.withEnchantmentTags(members, Set.copyOf(out.emptied().keySet())));
    }

    /**
     * POTION: blacklisted potions leave every potion tag (26.x #minecraft:tradeable); the
     * snapshot is unchanged. No vanilla potion tag exists on 1.21.x, so there it has nothing to
     * strip.
     */
    private static Result<Potion> potions(BlacklistSnapshot snapshot,
            Map<TagKey<Potion>, List<Holder<Potion>>> incoming, Recorder recorder) {
        if (snapshot.potions().isEmpty()) {
            return new Result<>(incoming, snapshot);
        }
        TagStrip.Outcome<TagKey<Potion>, Holder<Potion>> out = TagStrip.strip(incoming,
                tag -> false,
                holder -> snapshot.potion(holder),
                tag -> {
                },
                (tag, holder) -> recordRemoval(recorder, POTION, tag, holder));
        return new Result<>(out.tags(), snapshot);
    }

    /** Whether a block leaves the block tags: its item is not air and is blacklisted. */
    private static boolean blockBlacklisted(BlacklistSnapshot snapshot, Block block) {
        Item item = block.asItem();
        return item != Items.AIR && snapshot.item(item);
    }

    /** One TAG_ENTRY record; a holder without a key is not recorded. */
    private static <T> void recordRemoval(Recorder recorder, String registry, TagKey<T> tag,
            Holder<T> holder) {
        holder.unwrapKey().ifPresent(
                key -> recorder.tagRemoval(registry, Keys.name(tag), Keys.name(key)));
    }

    /**
     * The member keys of every configured tag; an empty set for a configured tag this load does
     * not define, which stays in the blacklist all the same.
     */
    private static <T> Map<TagKey<T>, Set<ResourceKey<T>>> members(Set<TagKey<T>> configured,
            Map<TagKey<T>, List<Holder<T>>> incoming) {
        Map<TagKey<T>, Set<ResourceKey<T>>> map = new HashMap<>();
        for (TagKey<T> tag : configured) {
            List<Holder<T>> list = incoming.get(tag);
            map.put(tag, list == null ? Set.of() : Lookups.keys(list));
        }
        return map;
    }
}
