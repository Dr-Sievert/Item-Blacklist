package net.sievert.item_blacklist.blacklist;

import net.sievert.item_blacklist.config.BlacklistConfig;
import net.sievert.item_blacklist.id.IdText;
import net.sievert.item_blacklist.line.Keys;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Turns a config into a snapshot against a server's registries, once per server starting.
 * It logs nothing and edits nothing: the unknown entries and the undefined tags come back as
 * lists for Lifecycle to warn about, and the config keeps them, so a mod added later finds its
 * entries still there. A configured tag always stays in the snapshot, defined or not: its
 * members come from each tag load, so a tag a datapack defines later is picked up at the next
 * reload. That is why the two lists are apart: an unknown entry is ignored, an undefined tag
 * is kept.
 */
public final class Resolver {
    /**
     * The snapshot and what the registries did not know.
     *
     * @param snapshot the resolved blacklist
     * @param unknown one entry per item, potion or enchantment the registries lack, in config
     *     order: "item <id>", "potion <id>", "enchantment <id>"; the snapshot leaves them out
     * @param undefinedTags one entry per configured tag no data defines yet, in config order:
     *     "item tag #<id>", "enchantment tag #<id>"; the snapshot keeps them
     */
    public record Result(BlacklistSnapshot snapshot, List<String> unknown,
            List<String> undefinedTags) {
        /** Copies the lists, so a result never changes. */
        public Result {
            unknown = List.copyOf(unknown);
            undefinedTags = List.copyOf(undefinedTags);
        }
    }

    /** minecraft:air, which the resolver treats as unknown. */
    private static final IdText AIR = new IdText(IdText.DEFAULT_NAMESPACE, "air");

    private Resolver() {
    }

    /** Config and registries in, snapshot, unknown entries and undefined tags out. */
    public static Result resolve(BlacklistConfig config, HolderLookup.Provider provider) {
        BlacklistSnapshot.Builder builder = BlacklistSnapshot.builder();
        List<String> unknown = new ArrayList<>();
        List<String> undefinedTags = new ArrayList<>();
        for (IdText id : config.items()) {
            ResourceKey<Item> key = Keys.of(Registries.ITEM, id.namespace(), id.path());
            // Air is never blacklisted: an empty stack is air.
            if (id.equals(AIR) || !BuiltInRegistries.ITEM.containsKey(key)) {
                unknown.add("item " + id);
            } else {
                builder.item(key);
            }
        }
        for (IdText id : config.itemTags()) {
            TagKey<Item> tag = Keys.tag(Registries.ITEM, id.namespace(), id.path());
            builder.itemTag(tag, Lookups.members(provider, Registries.ITEM, tag));
            if (!Lookups.tagKnown(provider, Registries.ITEM, tag)) {
                undefinedTags.add("item tag #" + id);
            }
        }
        for (IdText id : config.potions()) {
            ResourceKey<Potion> key = Keys.of(Registries.POTION, id.namespace(), id.path());
            if (BuiltInRegistries.POTION.containsKey(key)) {
                builder.potion(key);
            } else {
                unknown.add("potion " + id);
            }
        }
        for (IdText id : config.enchantments()) {
            ResourceKey<Enchantment> key =
                    Keys.of(Registries.ENCHANTMENT, id.namespace(), id.path());
            if (Lookups.holder(provider, Registries.ENCHANTMENT, key).isPresent()) {
                builder.enchantment(key);
            } else {
                unknown.add("enchantment " + id);
            }
        }
        for (IdText id : config.enchantmentTags()) {
            TagKey<Enchantment> tag = Keys.tag(Registries.ENCHANTMENT, id.namespace(), id.path());
            builder.enchantmentTag(tag, Lookups.members(provider, Registries.ENCHANTMENT, tag));
            if (!Lookups.tagKnown(provider, Registries.ENCHANTMENT, tag)) {
                undefinedTags.add("enchantment tag #" + id);
            }
        }
        return new Result(builder.build(), unknown, undefinedTags);
    }
}
