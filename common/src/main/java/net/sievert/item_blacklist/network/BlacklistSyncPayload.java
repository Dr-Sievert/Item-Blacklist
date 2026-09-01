package net.sievert.item_blacklist.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.sievert.item_blacklist.ItemBlacklist;

import java.util.List;

public record BlacklistSyncPayload(
        List<ResourceLocation> items
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BlacklistSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            ItemBlacklist.MOD_ID,
                            "sync_blacklist"
                    )
            );

    public static final StreamCodec<ByteBuf, BlacklistSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC.apply(
                            ByteBufCodecs.list()
                    ),
                    BlacklistSyncPayload::items,
                    BlacklistSyncPayload::new
            );

    public BlacklistSyncPayload {
        items = List.copyOf(items);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}