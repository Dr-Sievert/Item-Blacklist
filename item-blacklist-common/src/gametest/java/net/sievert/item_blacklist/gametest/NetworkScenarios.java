package net.sievert.item_blacklist.gametest;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.id.IdText;
import net.sievert.item_blacklist.line.Keys;
import net.sievert.item_blacklist.network.BlacklistSyncPayload;
import net.sievert.item_blacklist.network.ClientSyncSlot;
import net.sievert.item_blacklist.network.Sync;
import net.sievert.item_blacklist.platform.Services;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.VarInt;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * The network-entry scenarios (prefix net_): the payload's codec and channel, what the server
 * sends, the send guard for a player without the channel, the size limits, and the client slot
 * on a server. What only a client shows is checked by hand. Each is synchronous and holds
 * before and after a reload: it reads the live snapshot at call time and changes no state.
 */
public final class NetworkScenarios {
    private NetworkScenarios() {
    }

    /** The net_ scenarios; called by ItemBlacklistGameTests.register. */
    public static void register(BiConsumer<String, Consumer<GameTestHelper>> sink) {
        sink.accept("net_client_slot_inert", NetworkScenarios::netClientSlotInert);
        sink.accept("net_join_without_channel", NetworkScenarios::netJoinWithoutChannel);
        sink.accept("net_payload_limits", NetworkScenarios::netPayloadLimits);
        sink.accept("net_payload_matches_state", NetworkScenarios::netPayloadMatchesState);
        sink.accept("net_payload_round_trip", NetworkScenarios::netPayloadRoundTrip);
    }

    /**
     * The codec gives back equal lists (keys are interned), uses every byte, round-trips the
     * empty payload, and the channel is item_blacklist:sync_blacklist, named through Keys.
     */
    static void netPayloadRoundTrip(GameTestHelper helper) {
        BlacklistSyncPayload sent = Sync.payloadOf(TestGame.live());
        Check.isTrue(helper, !sent.items().isEmpty() && !sent.potions().isEmpty()
                        && !sent.enchantments().isEmpty(),
                "the fixture's payload has three non-empty lists");
        BlacklistSyncPayload received = roundTrip(helper, sent, 1);
        Check.equal(helper, received.items(), sent.items(), "decoded items");
        Check.equal(helper, received.potions(), sent.potions(), "decoded potions");
        Check.equal(helper, received.enchantments(), sent.enchantments(), "decoded enchantments");
        Check.equal(helper, received, sent, "decoded payload");

        BlacklistSyncPayload empty = Sync.payloadOf(BlacklistSnapshot.EMPTY);
        Check.equal(helper, roundTrip(helper, empty, 3), empty, "decoded empty payload");

        Check.equal(helper, Keys.name(BlacklistSyncPayload.TYPE), "item_blacklist:sync_blacklist",
                "channel");
        Check.equal(helper, sent.type(), BlacklistSyncPayload.TYPE, "the payload's type()");
        helper.succeed();
    }

    /**
     * The payload is built from the server's snapshot, tag-expanded and ordered by PATH_FIRST,
     * and survives snapshotOf with the same items, potions and enchantments and no tags.
     */
    static void netPayloadMatchesState(GameTestHelper helper) {
        BlacklistSnapshot live = TestGame.live();
        BlacklistSyncPayload payload = Sync.payloadOf(live);
        BlacklistSnapshot synced = Sync.snapshotOf(payload);

        Check.equal(helper, synced.items(), live.items(), "synced items");
        Check.equal(helper, synced.potions(), live.potions(), "synced potions");
        Check.equal(helper, synced.enchantments(), live.enchantments(), "synced enchantments");
        Check.isTrue(helper, synced.itemTags().isEmpty() && synced.enchantmentTags().isEmpty(),
                "a synced snapshot carries no tags");
        Check.equal(helper, payload.items().size(), live.items().size(), "sent item count");

        Check.isTrue(helper, pathFirstStrict(payload.items()) && pathFirstStrict(payload.potions())
                && pathFirstStrict(payload.enchantments()),
                "lists sorted by PATH_FIRST, no duplicate");
        Check.isTrue(helper, payload.items().contains(TestGame.key(Registries.ITEM, "oak_planks"))
                && payload.items().contains(TestGame.key(Registries.ITEM, "birch_planks"))
                && payload.items().contains(TestGame.key(Registries.ITEM, "charcoal")),
                "explicit and tag-derived items are sent");
        Check.isTrue(helper, !payload.items().contains(TestGame.key(Registries.ITEM, "stone")),
                "the control stone is not sent");
        Check.isTrue(helper,
                payload.potions().contains(TestGame.key(Registries.POTION, "strength"))
                        && payload.potions().contains(
                                TestGame.key(Registries.POTION, "long_strength"))
                        && payload.potions().contains(
                                TestGame.key(Registries.POTION, "strong_strength")),
                "the three strength potions are sent");
        Check.isTrue(helper,
                payload.enchantments().contains(TestGame.key(Registries.ENCHANTMENT, "mending"))
                        && payload.enchantments().contains(
                                TestGame.key(Registries.ENCHANTMENT, "binding_curse"))
                        && payload.enchantments().contains(
                                TestGame.key(Registries.ENCHANTMENT, "vanishing_curse")),
                "explicit and tag-derived enchantments are sent");
        Check.isTrue(helper, !payload.enchantments().contains(
                        TestGame.key(Registries.ENCHANTMENT, "unbreaking")),
                "the control unbreaking is not sent");
        Check.isTrue(helper, !anyModIdKey(payload.items()) && !anyModIdKey(payload.potions())
                && !anyModIdKey(payload.enchantments()), "unknown entries are not sent");
        Check.isTrue(helper, Blacklist.effective() == live, "effective() is the server's snapshot");
        helper.succeed();
    }

    /**
     * A player on an embedded connection, placed through the real join path (which fires the
     * loaders' join sync inside it), has no channel: nothing is sent and nothing throws. The
     * fits line rules out the size refusal, so false can only come from the channel guard.
     */
    static void netJoinWithoutChannel(GameTestHelper helper) {
        BlacklistSyncPayload live = Sync.payloadOf(TestGame.live());
        Check.isTrue(helper, Sync.fits(live), "the fixture's payload fits both limits");
        TestPlayers.withSurvival(helper, player -> {
            Check.isTrue(helper, !Sync.send(player),
                    "Sync.send sent to a player without the channel");
            Check.isTrue(helper, !Services.PLATFORM.sendToPlayer(player, live),
                    "Platform.sendToPlayer sent to a player without the channel");
        });
        helper.succeed();
    }

    /**
     * MAX_ENTRIES holds on the sender and the receiver, so a client never allocates for a
     * forged count; MAX_BYTES is reached below MAX_ENTRIES; fits agrees with both limits.
     */
    static void netPayloadLimits(GameTestHelper helper) {
        ResourceKey<Item> stone = TestGame.key(Registries.ITEM, "stone");
        List<ResourceKey<Potion>> noPotions = List.of();
        List<ResourceKey<Enchantment>> noEnchantments = List.of();

        BlacklistSyncPayload tooMany = new BlacklistSyncPayload(
                Collections.nCopies(BlacklistSyncPayload.MAX_ENTRIES + 1, stone), noPotions,
                noEnchantments);
        ByteBuf out = Unpooled.buffer();
        try {
            boolean refused = false;
            try {
                BlacklistSyncPayload.STREAM_CODEC.encode(out, tooMany);
            } catch (EncoderException e) {
                refused = true;
            }
            Check.isTrue(helper, refused,
                    "the codec encoded " + (BlacklistSyncPayload.MAX_ENTRIES + 1) + " items");
        } finally {
            out.release();
        }

        ByteBuf forged = Unpooled.buffer();
        try {
            VarInt.write(forged, BlacklistSyncPayload.MAX_ENTRIES + 1);
            boolean refused = false;
            try {
                BlacklistSyncPayload.STREAM_CODEC.decode(forged);
            } catch (DecoderException e) {
                refused = true;
            }
            Check.isTrue(helper, refused, "the codec accepted a count above MAX_ENTRIES");
        } finally {
            forged.release();
        }

        BlacklistSyncPayload tooLarge = new BlacklistSyncPayload(
                Collections.nCopies(50_000, stone), noPotions, noEnchantments);
        Check.isTrue(helper, Sync.estimatedBytes(tooLarge) > BlacklistSyncPayload.MAX_BYTES,
                "50,000 entries of minecraft:stone are estimated at "
                        + Sync.estimatedBytes(tooLarge));
        Check.isTrue(helper, !Sync.fits(tooMany), "fits() accepts too many entries");
        Check.isTrue(helper, !Sync.fits(tooLarge), "fits() accepts too many bytes");
        Check.isTrue(helper, Sync.fits(Sync.payloadOf(TestGame.live())),
                "fits() refuses the fixture");
        helper.succeed();
    }

    /**
     * On a physical server no client receiver is installed and the NeoForge handler's target
     * does nothing; in any JVM with a running server, effective() is the server's snapshot. A
     * GameTest inside a singleplayer world skips the first half: its client installed the slot.
     */
    static void netClientSlotInert(GameTestHelper helper) {
        MinecraftServer server = TestGame.server(helper);
        if (!server.isSingleplayer()) {
            Check.isTrue(helper, !ClientSyncSlot.installed(),
                    "a client receiver is installed on a physical server");
            // Does nothing until installed; must not throw without client classes.
            ClientSyncSlot.deliver(Sync.payloadOf(TestGame.live()));
        }
        Check.isTrue(helper, Blacklist.effective() == Blacklist.server(),
                "effective() is not the server's snapshot while a server runs");
        helper.succeed();
    }

    /** Encodes into a fresh heap buffer and decodes it back; the buffer is released. */
    private static BlacklistSyncPayload roundTrip(GameTestHelper helper, BlacklistSyncPayload sent,
            int expectedMinBytes) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            BlacklistSyncPayload.STREAM_CODEC.encode(buffer, sent);
            int written = buffer.readableBytes();
            Check.isTrue(helper, written >= expectedMinBytes, "encoded " + written + " bytes");
            if (!sent.items().isEmpty() || !sent.potions().isEmpty()
                    || !sent.enchantments().isEmpty()) {
                Check.isTrue(helper, Sync.estimatedBytes(sent) >= written, "estimate "
                        + Sync.estimatedBytes(sent) + " is below the encoded size " + written);
            }
            BlacklistSyncPayload received = BlacklistSyncPayload.STREAM_CODEC.decode(buffer);
            Check.equal(helper, buffer.readableBytes(), 0, "bytes left after decode");
            return received;
        } finally {
            buffer.release();
        }
    }

    /** Whether the names of the keys are strictly increasing by IdText.PATH_FIRST. */
    private static boolean pathFirstStrict(List<? extends ResourceKey<?>> keys) {
        for (int i = 1; i < keys.size(); i++) {
            if (IdText.PATH_FIRST.compare(Keys.name(keys.get(i - 1)), Keys.name(keys.get(i)))
                    >= 0) {
                return false;
            }
        }
        return true;
    }

    /** Whether any key's name starts with "mod_id:" (the fixture's unknown entries). */
    private static boolean anyModIdKey(List<? extends ResourceKey<?>> keys) {
        for (ResourceKey<?> key : keys) {
            if (Keys.name(key).startsWith("mod_id:")) {
                return true;
            }
        }
        return false;
    }
}
