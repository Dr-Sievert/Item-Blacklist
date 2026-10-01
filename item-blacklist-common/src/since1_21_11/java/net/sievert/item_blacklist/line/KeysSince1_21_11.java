package net.sievert.item_blacklist.line;

import net.minecraft.core.Registry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

/** Keys from 1.21.11: ids are {@code Identifier}, a key's id is {@code identifier()}. */
public final class KeysSince1_21_11 implements Keys.Backend {
    @Override
    public <T> ResourceKey<T> of(
            ResourceKey<? extends Registry<T>> registry, String namespace, String path) {
        return ResourceKey.create(registry, Identifier.fromNamespaceAndPath(namespace, path));
    }

    @Override
    public String name(ResourceKey<?> key) {
        return key.identifier().toString();
    }

    @Override
    public <T> TagKey<T> tag(
            ResourceKey<? extends Registry<T>> registry, String namespace, String path) {
        return TagKey.create(registry, Identifier.fromNamespaceAndPath(namespace, path));
    }

    @Override
    public String name(TagKey<?> tag) {
        return tag.location().toString();
    }

    @Override
    public <P extends CustomPacketPayload> CustomPacketPayload.Type<P> payloadType(
            String namespace, String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(namespace, path));
    }

    @Override
    public String name(CustomPacketPayload.Type<?> type) {
        return type.id().toString();
    }
}
