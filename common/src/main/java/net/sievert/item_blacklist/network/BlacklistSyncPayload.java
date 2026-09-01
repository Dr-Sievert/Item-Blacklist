package net.sievert.item_blacklist.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.sievert.item_blacklist.ItemBlacklist;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record BlacklistSyncPayload(
        List<ResourceLocation> items,
        List<ResourceLocation> potions,
        List<ResourceLocation> enchantments
) implements CustomPacketPayload {

    public static final Type<BlacklistSyncPayload> TYPE =
            new Type<>(
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
                    ResourceLocation.STREAM_CODEC.apply(
                            ByteBufCodecs.list()
                    ),
                    BlacklistSyncPayload::potions,
                    ResourceLocation.STREAM_CODEC.apply(
                            ByteBufCodecs.list()
                    ),
                    BlacklistSyncPayload::enchantments,
                    BlacklistSyncPayload::new
            );

    public BlacklistSyncPayload {
        items = List.copyOf(items);
        potions = List.copyOf(potions);
        enchantments = List.copyOf(enchantments);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
