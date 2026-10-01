package net.sievert.item_blacklist.brewing;

import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.Lookups;
import net.sievert.item_blacklist.line.Ingredients;
import net.sievert.item_blacklist.line.Keys;
import net.sievert.item_blacklist.mixin.PotionBrewingAccessor;
import net.sievert.item_blacklist.mixin.PotionBrewingMixAccessor;
import net.sievert.item_blacklist.report.Recorder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The vanilla brewing mixes of one PotionBrewing object, filtered from the lists it had when
 * first seen, so a second call with the same snapshot changes nothing and a smaller blacklist
 * gives mixes back. Called by Lifecycle after every server starting and good reload, and by
 * ClientSync for the connection's own object; the server's object and each client's are kept
 * apart by identity, and the client's has no ServerState, so the originals live in a map keyed
 * weakly by the object. A mix goes when its output or its input is blacklisted, or when every
 * alternative of its reagent is; the rules are public so scenarios test them on built
 * snapshots.
 */
public final class BrewingFilter {
    /** The mix lists as first read, per object; the object is the key, weakly. */
    private static final Map<PotionBrewing, Originals> ORIGINALS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private BrewingFilter() {
    }

    /**
     * Replaces both mix lists of this PotionBrewing by its originals without every mix the
     * snapshot condemns, and records each removal with its causes. An empty snapshot gives the
     * originals back.
     */
    public static void apply(PotionBrewing brewing, BlacklistSnapshot snapshot,
            Recorder recorder) {
        PotionBrewingAccessor lists = (PotionBrewingAccessor) brewing;
        // computeIfAbsent of a synchronized map runs under its lock, so the server thread and
        // the client thread never see a half-built entry.
        Originals originals = ORIGINALS.computeIfAbsent(brewing, b -> new Originals(
                List.copyOf(lists.item_blacklist$potionMixes()),
                List.copyOf(lists.item_blacklist$containerMixes())));
        lists.item_blacklist$setPotionMixes(
                kept(originals.potionMixes(), snapshot, recorder, false));
        lists.item_blacklist$setContainerMixes(
                kept(originals.containerMixes(), snapshot, recorder, true));
    }

    /**
     * The ids that condemn a potion mix, first reason only, in the order output, input,
     * reagent; empty when the mix stays. A holder without a key is never blacklisted.
     */
    public static List<String> potionMixCauses(BlacklistSnapshot snapshot, Holder<Potion> from,
            Ingredient reagent, Holder<Potion> to) {
        if (snapshot.isEmpty()) {
            return List.of();
        }
        if (snapshot.potion(to)) {
            return List.of(name(to));
        }
        if (snapshot.potion(from)) {
            return List.of(name(from));
        }
        return reagentCauses(snapshot, reagent);
    }

    /**
     * The same for a container mix, whose endpoints are the bottle items' own registry holders:
     * the output's item, else the input's, else the reagent, each with the configured tags that
     * hold it.
     */
    public static List<String> containerMixCauses(BlacklistSnapshot snapshot, Holder<Item> from,
            Ingredient reagent, Holder<Item> to) {
        if (snapshot.isEmpty()) {
            return List.of();
        }
        if (snapshot.item(to.value())) {
            return itemCauses(snapshot, to.value());
        }
        if (snapshot.item(from.value())) {
            return itemCauses(snapshot, from.value());
        }
        return reagentCauses(snapshot, reagent);
    }

    /**
     * The ids of a reagent whose every alternative is blacklisted, each followed by the
     * configured tags that hold it; empty when one alternative is allowed or the reagent lists
     * none. The alternatives are read unfiltered (Ingredients), after tags are bound.
     */
    public static List<String> reagentCauses(BlacklistSnapshot snapshot, Ingredient reagent) {
        if (snapshot.isEmpty()) {
            return List.of();
        }
        List<Item> alternatives = Ingredients.alternatives(reagent);
        if (alternatives.isEmpty()) {
            return List.of();
        }
        Set<String> causes = new LinkedHashSet<>();
        for (Item item : alternatives) {
            if (!snapshot.item(item)) {
                return List.of();
            }
            causes.addAll(itemCauses(snapshot, item));
        }
        return List.copyOf(causes);
    }

    /**
     * "input=..., ingredient=[...], output=..." of an element of either mix list: the report's
     * subject, and the scenarios' name of a mix.
     */
    public static String describe(Object mix) {
        PotionBrewingMixAccessor accessor = (PotionBrewingMixAccessor) mix;
        return BrewingText.mix(name(accessor.item_blacklist$from()),
                Lookups.itemNames(Ingredients.alternatives(accessor.item_blacklist$ingredient()),
                        BrewingText.DIRECT),
                name(accessor.item_blacklist$to()));
    }

    /**
     * The mixes of one list the snapshot keeps, as an immutable list (what vanilla stores
     * itself); each removal is recorded once per cause.
     */
    @SuppressWarnings("unchecked")
    private static List<Object> kept(List<Object> mixes, BlacklistSnapshot snapshot,
            Recorder recorder, boolean container) {
        if (snapshot.isEmpty()) {
            return mixes;
        }
        List<Object> kept = new ArrayList<>(mixes.size());
        for (Object element : mixes) {
            PotionBrewingMixAccessor mix = (PotionBrewingMixAccessor) element;
            // The list's element type fixes the endpoints: items in the container list,
            // potions in the potion list.
            List<String> causes = container
                    ? containerMixCauses(snapshot, (Holder<Item>) mix.item_blacklist$from(),
                            mix.item_blacklist$ingredient(),
                            (Holder<Item>) mix.item_blacklist$to())
                    : potionMixCauses(snapshot, (Holder<Potion>) mix.item_blacklist$from(),
                            mix.item_blacklist$ingredient(),
                            (Holder<Potion>) mix.item_blacklist$to());
            if (causes.isEmpty()) {
                kept.add(element);
                continue;
            }
            String subject = describe(element);
            for (String cause : causes) {
                recorder.brewingRemoval(subject, cause);
            }
        }
        return List.copyOf(kept);
    }

    /**
     * The item's id, then "#tag" for every configured tag whose members hold it, in the
     * snapshot's order: the old log's attribution of a removal to the tag that caused it.
     */
    private static List<String> itemCauses(BlacklistSnapshot snapshot, Item item) {
        List<String> causes = new ArrayList<>();
        Optional<ResourceKey<Item>> key = BuiltInRegistries.ITEM.getResourceKey(item);
        causes.add(key.map(k -> Keys.name(k)).orElse(BrewingText.DIRECT));
        if (key.isPresent()) {
            for (Map.Entry<TagKey<Item>, Set<ResourceKey<Item>>> entry
                    : snapshot.itemTagMembers().entrySet()) {
                if (entry.getValue().contains(key.get())) {
                    causes.add("#" + Keys.name(entry.getKey()));
                }
            }
        }
        return causes;
    }

    /** A holder's id; never getRegisteredName, whose "[unregistered]" must not reach a report. */
    private static String name(Holder<?> holder) {
        return holder.unwrapKey().map(key -> Keys.name(key)).orElse(BrewingText.DIRECT);
    }

    /** The two mix lists of one PotionBrewing as first read. */
    private record Originals(List<Object> potionMixes, List<Object> containerMixes) {
    }
}
