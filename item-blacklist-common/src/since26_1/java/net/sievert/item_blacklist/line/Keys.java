package net.sievert.item_blacklist.line;

import net.sievert.item_blacklist.ItemBlacklistMod;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

/**
 * Ids on this line, built from {@code Identifier}, the id class from 1.21.11 on; a key's id
 * is {@code identifier()}. Below 1.21.11 the class is {@code ResourceLocation}; shared code
 * names neither and builds ids here, so it compiles on every line. CLAUDE.md, "Rules that
 * are load-bearing".
 */
public final class Keys {
    private Keys() {
    }

    /** A key in the mod's namespace, e.g. {@code Keys.of(Registries.BLOCK, "my_block")}. */
    public static <T> ResourceKey<T> of(ResourceKey<? extends Registry<T>> registry, String path) {
        return of(registry, ItemBlacklistMod.MOD_ID, path);
    }

    /** A key in another namespace, e.g. the test mod's. */
    public static <T> ResourceKey<T> of(
            ResourceKey<? extends Registry<T>> registry, String namespace, String path) {
        return ResourceKey.create(registry, Identifier.fromNamespaceAndPath(namespace, path));
    }

    /** The key's id as "namespace:path". */
    public static String name(ResourceKey<?> key) {
        return key.identifier().toString();
    }

    /**
     * A tag key, e.g. {@code Keys.tag(Registries.ITEM, "minecraft", "planks")}: TagKey.create
     * takes the id class, so shared code builds tag keys here.
     */
    public static <T> TagKey<T> tag(
            ResourceKey<? extends Registry<T>> registry, String namespace, String path) {
        return TagKey.create(registry, Identifier.fromNamespaceAndPath(namespace, path));
    }

    /**
     * The tag's id as "namespace:path", without '#': TagKey.location() returns the id class, so
     * shared code names a tag here.
     */
    public static String name(TagKey<?> tag) {
        return tag.location().toString();
    }

    /**
     * The payload channel "item_blacklist:path": CustomPacketPayload.Type is built from the id
     * class, so shared code builds channels here.
     */
    public static <P extends CustomPacketPayload> CustomPacketPayload.Type<P> payloadType(
            String path) {
        return new CustomPacketPayload.Type<>(
                Identifier.fromNamespaceAndPath(ItemBlacklistMod.MOD_ID, path));
    }

    /** The channel's id as "namespace:path": Type.id() returns the id class. */
    public static String name(CustomPacketPayload.Type<?> type) {
        return type.id().toString();
    }
}
