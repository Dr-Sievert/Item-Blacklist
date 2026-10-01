package net.sievert.item_blacklist.line;

import net.sievert.item_blacklist.ItemBlacklistMod;
import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

/**
 * Ids on the 1.21.x line. The id class is {@code ResourceLocation} up to 1.21.10 and
 * {@code Identifier} from 1.21.11, so this facade hands the call to a backend picked for the
 * running release: {@code KeysUntil1_21_11} (src/until1_21_11) or {@code KeysSince1_21_11}
 * (src/since1_21_11). Shared code names neither id class and builds ids here. CLAUDE.md,
 * "Rules that are load-bearing".
 */
public final class Keys {
    /** The calls that differ; each backend implements them for its window. */
    public interface Backend {
        /** The window's ResourceKey.create with its id class. */
        <T> ResourceKey<T> of(
                ResourceKey<? extends Registry<T>> registry, String namespace, String path);

        /** The window's ResourceKey.location() (identifier() from 1.21.11) as text. */
        String name(ResourceKey<?> key);

        /** The window's TagKey.create with its id class. */
        <T> TagKey<T> tag(
                ResourceKey<? extends Registry<T>> registry, String namespace, String path);

        /** The window's TagKey.location() as text. */
        String name(TagKey<?> tag);

        /** The window's CustomPacketPayload.Type constructor with its id class. */
        <P extends CustomPacketPayload> CustomPacketPayload.Type<P> payloadType(
                String namespace, String path);

        /** The window's CustomPacketPayload.Type.id() as text. */
        String name(CustomPacketPayload.Type<?> type);
    }

    private static final Backend BACKEND = Backends.pick("Keys", Backend.class,
            Backends.until("1.21.11", "net.sievert.item_blacklist.line.KeysUntil1_21_11"),
            Backends.since("1.21.11", "net.sievert.item_blacklist.line.KeysSince1_21_11"));

    private Keys() {
    }

    /** A key in the mod's namespace, e.g. {@code Keys.of(Registries.BLOCK, "my_block")}. */
    public static <T> ResourceKey<T> of(ResourceKey<? extends Registry<T>> registry, String path) {
        return BACKEND.of(registry, ItemBlacklistMod.MOD_ID, path);
    }

    /** A key in another namespace, e.g. the test mod's. */
    public static <T> ResourceKey<T> of(
            ResourceKey<? extends Registry<T>> registry, String namespace, String path) {
        return BACKEND.of(registry, namespace, path);
    }

    /** The key's id as "namespace:path". */
    public static String name(ResourceKey<?> key) {
        return BACKEND.name(key);
    }

    /**
     * A tag key, e.g. {@code Keys.tag(Registries.ITEM, "minecraft", "planks")}: TagKey.create
     * takes the id class, so shared code builds tag keys here.
     */
    public static <T> TagKey<T> tag(
            ResourceKey<? extends Registry<T>> registry, String namespace, String path) {
        return BACKEND.tag(registry, namespace, path);
    }

    /**
     * The tag's id as "namespace:path", without '#': TagKey.location() returns the id class, so
     * shared code names a tag here.
     */
    public static String name(TagKey<?> tag) {
        return BACKEND.name(tag);
    }

    /**
     * The payload channel "item_blacklist:path": CustomPacketPayload.Type is built from the id
     * class, so shared code builds channels here.
     */
    public static <P extends CustomPacketPayload> CustomPacketPayload.Type<P> payloadType(
            String path) {
        return BACKEND.payloadType(ItemBlacklistMod.MOD_ID, path);
    }

    /** The channel's id as "namespace:path": Type.id() returns the id class. */
    public static String name(CustomPacketPayload.Type<?> type) {
        return BACKEND.name(type);
    }
}
