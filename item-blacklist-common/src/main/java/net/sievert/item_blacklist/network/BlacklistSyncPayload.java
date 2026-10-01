package net.sievert.item_blacklist.network;

import net.sievert.item_blacklist.line.Keys;
import java.util.List;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * The server's blacklist as a client needs it for display and prediction: the tag-expanded
 * items, potions and enchantments, no tags. Sent at every join and every reload on the old
 * channel item_blacklist:sync_blacklist, with the old bytes (ResourceKey's codec writes the id
 * as the old id codec did), so the server still decides and a client only shows.
 */
public record BlacklistSyncPayload(List<ResourceKey<Item>> items,
        List<ResourceKey<Potion>> potions,
        List<ResourceKey<Enchantment>> enchantments) implements CustomPacketPayload {
    /** Per list: what a client decodes at most, so a forged count never allocates more. */
    public static final int MAX_ENTRIES = 65_536;

    /**
     * The largest estimated size Sync sends: a conservative cap below the game's limits of a
     * custom payload, so a huge blacklist is refused with one WARN instead of a kicked client.
     */
    public static final int MAX_BYTES = 900_000;

    /** The channel, built through Keys: Type's constructor takes the id class. */
    public static final CustomPacketPayload.Type<BlacklistSyncPayload> TYPE =
            Keys.payloadType("sync_blacklist");

    /**
     * Three lists of registry keys, each capped at MAX_ENTRIES on both sides: the encoder throws
     * above the cap too. ResourceKey.streamCodec and ByteBufCodecs.list(int) have one
     * descriptor on every release, so this codec is written once.
     */
    public static final StreamCodec<ByteBuf, BlacklistSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceKey.streamCodec(Registries.ITEM)
                            .apply(ByteBufCodecs.list(MAX_ENTRIES)),
                    BlacklistSyncPayload::items,
                    ResourceKey.streamCodec(Registries.POTION)
                            .apply(ByteBufCodecs.list(MAX_ENTRIES)),
                    BlacklistSyncPayload::potions,
                    ResourceKey.streamCodec(Registries.ENCHANTMENT)
                            .apply(ByteBufCodecs.list(MAX_ENTRIES)),
                    BlacklistSyncPayload::enchantments,
                    BlacklistSyncPayload::new);

    /** Copies the lists: a payload is a snapshot, whatever the caller does with its lists. */
    public BlacklistSyncPayload {
        items = List.copyOf(items);
        potions = List.copyOf(potions);
        enchantments = List.copyOf(enchantments);
    }

    /** The channel this payload travels on. */
    @Override
    public CustomPacketPayload.Type<BlacklistSyncPayload> type() {
        return TYPE;
    }
}
