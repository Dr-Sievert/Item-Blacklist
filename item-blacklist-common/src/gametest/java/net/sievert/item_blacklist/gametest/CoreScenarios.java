package net.sievert.item_blacklist.gametest;

import net.sievert.item_blacklist.ItemBlacklistMod;
import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.Components;
import net.sievert.item_blacklist.blacklist.Lookups;
import net.sievert.item_blacklist.blacklist.Resolver;
import net.sievert.item_blacklist.blacklist.ServerState;
import net.sievert.item_blacklist.blacklist.StackRules;
import net.sievert.item_blacklist.blacklist.TagFilter;
import net.sievert.item_blacklist.config.BlacklistConfig;
import net.sievert.item_blacklist.config.ConfigFile;
import net.sievert.item_blacklist.config.ConfigParser;
import net.sievert.item_blacklist.id.IdText;
import net.sievert.item_blacklist.line.CloneStacks;
import net.sievert.item_blacklist.line.Keys;
import net.sievert.item_blacklist.platform.Services;
import net.sievert.item_blacklist.report.Recorder;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The core-state scenarios: the config the running server took, the resolved state, the start
 * sequence, the stack and block rules, the empty snapshot, the write-once facades, and the
 * lifecycle's rules (idempotent hooks, the remote field, the Gson each release ships). Each is
 * synchronous and holds before and after a reload.
 */
public final class CoreScenarios {
    /** Three cells of the shared 3x3x3 structure, data/item_blacklist_gametest/structure. */
    private static final BlockPos PLANKS_POS = new BlockPos(0, 1, 1);
    private static final BlockPos STONE_POS = new BlockPos(2, 1, 1);
    private static final BlockPos AIR_POS = new BlockPos(1, 2, 1);

    /** The entries of the fixture, per list, in the file's order. */
    private static final List<IdText> FIXTURE_ITEMS = List.of(mc("oak_planks"), mc("charcoal"),
            mc("soul_sand"), mc("soul_soil"), mc("rabbit_foot"), mc("lingering_potion"),
            mc("beetroot_seeds"), mc("phantom_membrane"), id("mod_id", "mod_item"));
    private static final List<IdText> FIXTURE_ITEM_TAGS =
            List.of(mc("planks"), id("mod_id", "mod_tag"));
    private static final List<IdText> FIXTURE_POTIONS = List.of(mc("strength"),
            mc("strong_strength"), mc("long_strength"), id("mod_id", "mod_potion"));
    private static final List<IdText> FIXTURE_ENCHANTMENTS =
            List.of(mc("mending"), id("mod_id", "mod_enchantment"));
    private static final List<IdText> FIXTURE_ENCHANTMENT_TAGS =
            List.of(mc("curse"), id("mod_id", "mod_enchantment_tag"));

    private CoreScenarios() {
    }

    /** The core_ scenarios; called by ItemBlacklistGameTests.register. */
    public static void register(BiConsumer<String, Consumer<GameTestHelper>> sink) {
        sink.accept("core_config_fixture_loaded", CoreScenarios::configFixtureLoaded);
        sink.accept("core_state_resolved", CoreScenarios::stateResolved);
        sink.accept("core_started_once", CoreScenarios::startedOnce);
        sink.accept("core_stack_rule", CoreScenarios::stackRule);
        sink.accept("core_block_rule", CoreScenarios::blockRule);
        sink.accept("core_empty_snapshot_inert", CoreScenarios::emptySnapshotInert);
        sink.accept("core_facades_link", CoreScenarios::facadesLink);
        sink.accept("core_unknown_entries_reported", CoreScenarios::unknownEntriesReported);
        sink.accept("core_air_never_blacklisted", CoreScenarios::airNeverBlacklisted);
        sink.accept("core_hooks_idempotent", CoreScenarios::hooksIdempotent);
        sink.accept("core_remote_ignored_with_server", CoreScenarios::remoteIgnoredWithServer);
        sink.accept("core_config_parse_on_game_gson", CoreScenarios::configParseOnGameGson);
    }

    /**
     * The test mod's override reached the server's start, parsed by this release's Gson, while
     * mod init still wrote the default file; ItemBlacklistMod.config() is what starting took.
     */
    static void configFixtureLoaded(GameTestHelper helper) {
        ConfigParser.Result fixture = readFixture(helper);
        Check.isTrue(helper, fixture.warnings().isEmpty(),
                "The fixture parses without a warning: " + fixture.warnings());
        ServerState state = requireState(helper);
        Check.equal(helper, state.config(), fixture.config(), "the server state's config");
        BlacklistConfig modConfig = ItemBlacklistMod.config();
        Check.isTrue(helper, modConfig != null, "ItemBlacklistMod.config() is never null");
        Check.equal(helper, modConfig, fixture.config(), "ItemBlacklistMod.config()");
        BlacklistConfig config = state.config();
        Check.isTrue(helper, config.detailedLog(), "Expected \"Detailed Log\": true");
        // Exact lists, in the file's order: an extra entry fails as a missing one does.
        Check.equal(helper, config.items(), FIXTURE_ITEMS, "the items");
        Check.equal(helper, config.itemTags(), FIXTURE_ITEM_TAGS, "the item tags");
        Check.equal(helper, config.potions(), FIXTURE_POTIONS, "the potions");
        Check.equal(helper, config.enchantments(), FIXTURE_ENCHANTMENTS, "the enchantments");
        Check.equal(helper, config.enchantmentTags(), FIXTURE_ENCHANTMENT_TAGS,
                "the enchantment tags");
        Path file = Services.PLATFORM.configDir().resolve(ConfigFile.NAME);
        Check.isTrue(helper, Files.isRegularFile(file),
                "Mod init wrote the default config at " + file);
        helper.succeed();
    }

    /**
     * The resolver's snapshot: explicit and tag-derived entries (birch planks through #planks,
     * the curses through #curse), both query forms, unknown ids dropped as keys and kept as
     * tags.
     */
    static void stateResolved(GameTestHelper helper) {
        ServerState state = requireState(helper);
        BlacklistSnapshot live = TestGame.live();
        Check.isTrue(helper, live == state.snapshot(),
                "TestGame.live() is the server state's snapshot");
        Check.isTrue(helper, !live.isEmpty(), "The live snapshot is not empty");
        for (Item item : List.of(Items.OAK_PLANKS, Items.BIRCH_PLANKS, Items.CHARCOAL)) {
            ResourceKey<Item> key = BuiltInRegistries.ITEM.getResourceKey(item).orElseThrow();
            Check.isTrue(helper, live.item(item) && live.item(key),
                    "Expected " + Keys.name(key) + " blacklisted");
        }
        ResourceKey<Item> birch = TestGame.key(Registries.ITEM, "birch_planks");
        Check.isTrue(helper,
                live.explicitItems().contains(TestGame.key(Registries.ITEM, "oak_planks")),
                "oak_planks is an explicit item");
        Check.isTrue(helper, !live.explicitItems().contains(birch),
                "birch_planks comes from #planks only");
        TagKey<Item> planks = Keys.tag(Registries.ITEM, "minecraft", "planks");
        Check.isTrue(helper, live.itemTag(planks), "#minecraft:planks is a configured tag");
        Check.isTrue(helper, live.itemTagMembers().getOrDefault(planks, Set.of()).contains(birch),
                "#minecraft:planks lists birch_planks among its members before the strip");
        Check.isTrue(helper,
                live.itemTags().contains(Keys.tag(Registries.ITEM, "mod_id", "mod_tag")),
                "#mod_id:mod_tag stays in the item tag set");
        for (String potion : List.of("strength", "strong_strength", "long_strength")) {
            Holder<Potion> holder = TestGame.holder(helper, Registries.POTION, potion);
            Check.isTrue(helper,
                    live.potion(TestGame.key(Registries.POTION, potion)) && live.potion(holder),
                    "Expected potion " + potion + " blacklisted");
        }
        for (String enchantment : List.of("mending", "binding_curse", "vanishing_curse")) {
            Holder<Enchantment> holder =
                    TestGame.holder(helper, Registries.ENCHANTMENT, enchantment);
            Check.isTrue(helper,
                    live.enchantment(TestGame.key(Registries.ENCHANTMENT, enchantment))
                            && live.enchantment(holder),
                    "Expected enchantment " + enchantment + " blacklisted");
        }
        Check.isTrue(helper,
                live.enchantmentTag(Keys.tag(Registries.ENCHANTMENT, "minecraft", "curse")),
                "#minecraft:curse is a configured tag");
        Check.isTrue(helper, live.enchantmentTags().contains(
                        Keys.tag(Registries.ENCHANTMENT, "mod_id", "mod_enchantment_tag")),
                "#mod_id:mod_enchantment_tag stays in the enchantment tag set");
        Check.isTrue(helper, !live.item(Items.STONE), "stone is not blacklisted");
        Check.isTrue(helper,
                !live.enchantment(TestGame.key(Registries.ENCHANTMENT, "unbreaking")),
                "unbreaking is not blacklisted");
        Check.isTrue(helper, !live.potion(TestGame.key(Registries.POTION, "awkward")),
                "awkward is not blacklisted");
        List<String> modIds = new ArrayList<>();
        live.items().forEach(key -> addIfModId(modIds, Keys.name(key)));
        live.potions().forEach(key -> addIfModId(modIds, Keys.name(key)));
        live.enchantments().forEach(key -> addIfModId(modIds, Keys.name(key)));
        Check.isTrue(helper, modIds.isEmpty(), "No key of namespace mod_id is blacklisted: "
                + modIds);
        helper.succeed();
    }

    /**
     * The blocking start reload ran once and succeeded, reported back through
     * MinecraftServerMixin, and the state belongs to this server; the report is open.
     */
    static void startedOnce(GameTestHelper helper) {
        ServerState state = requireState(helper);
        Check.isTrue(helper, Blacklist.hasServer(), "Blacklist.hasServer()");
        Check.isTrue(helper, state.server() == TestGame.server(helper),
                "The state belongs to this server");
        Check.isTrue(helper, state.onServerThread(), "Scenarios run on the server thread");
        Check.isTrue(helper, state.startReloadDone(), "The forced start reload ran");
        Check.isTrue(helper, state.startReloadSucceeded(), "The forced start reload succeeded");
        Check.isTrue(helper, state.reloads() >= 1,
                "Expected at least one reload, was " + state.reloads());
        Check.isTrue(helper, Blacklist.recorder() == state.report(),
                "Blacklist.recorder() is the state's report");
        Check.isTrue(helper, Blacklist.server() == state.snapshot(),
                "Blacklist.server() is the state's snapshot");
        helper.succeed();
    }

    /**
     * The stack rule: both enchantment components on every stack, the potion component on two
     * item kinds, the order of reasons, the empty stack.
     */
    static void stackRule(GameTestHelper helper) {
        BlacklistSnapshot live = TestGame.live();
        expectReason(helper, live, "oak planks", new ItemStack(Items.OAK_PLANKS),
                StackRules.Reason.ITEM);
        expectReason(helper, live, "a book with stored mending", TestGame.book(helper, "mending"),
                StackRules.Reason.ENCHANTMENT);
        expectReason(helper, live, "an iron sword with mending",
                TestGame.enchanted(helper, Items.IRON_SWORD, "mending"),
                StackRules.Reason.ENCHANTMENT);
        expectReason(helper, live, "a stick with stored mending",
                TestGame.book(helper, "mending").transmuteCopy(Items.STICK),
                StackRules.Reason.ENCHANTMENT);
        expectReason(helper, live, "a potion of strength",
                TestGame.potionStack(helper, Items.POTION, "strength"),
                StackRules.Reason.POTION);
        expectReason(helper, live, "a tipped arrow of long strength",
                TestGame.potionStack(helper, Items.TIPPED_ARROW, "long_strength"),
                StackRules.Reason.POTION);
        expectReason(helper, live, "oak planks with mending",
                TestGame.enchanted(helper, Items.OAK_PLANKS, "mending"), StackRules.Reason.ITEM);
        expectReason(helper, live, "the empty stack", ItemStack.EMPTY, StackRules.Reason.NONE);
        expectReason(helper, live, "stone", new ItemStack(Items.STONE), StackRules.Reason.NONE);
        expectReason(helper, live, "an iron sword with unbreaking",
                TestGame.enchanted(helper, Items.IRON_SWORD, "unbreaking"),
                StackRules.Reason.NONE);
        expectReason(helper, live, "a potion of awkward",
                TestGame.potionStack(helper, Items.POTION, "awkward"), StackRules.Reason.NONE);
        helper.succeed();
    }

    /** The block rule through CloneStacks in each window; air is never blacklisted. */
    static void blockRule(GameTestHelper helper) {
        BlacklistSnapshot live = TestGame.live();
        helper.setBlock(PLANKS_POS, Blocks.OAK_PLANKS);
        helper.setBlock(STONE_POS, Blocks.STONE);
        helper.setBlock(AIR_POS, Blocks.AIR);
        expectBlock(helper, live, "oak planks", PLANKS_POS, true);
        expectBlock(helper, live, "stone", STONE_POS, false);
        expectBlock(helper, live, "air", AIR_POS, false);
        expectBlock(helper, BlacklistSnapshot.EMPTY, "oak planks with the empty snapshot",
                PLANKS_POS, false);
        helper.succeed();
    }

    /**
     * An empty snapshot, EMPTY or built, answers false to every query, holds nothing, judges
     * nothing, and TagFilter hands its input back.
     */
    static void emptySnapshotInert(GameTestHelper helper) {
        ItemStack planks = new ItemStack(Items.OAK_PLANKS);
        Holder<Potion> strength = TestGame.holder(helper, Registries.POTION, "strength");
        Holder<Enchantment> mending = TestGame.holder(helper, Registries.ENCHANTMENT, "mending");
        TagKey<Item> planksTag = Keys.tag(Registries.ITEM, "minecraft", "planks");
        TagKey<Enchantment> curse = Keys.tag(Registries.ENCHANTMENT, "minecraft", "curse");
        Map<TagKey<Item>, List<Holder<Item>>> incoming = Map.of(planksTag,
                List.<Holder<Item>>of(TestGame.holder(helper, Registries.ITEM, "oak_planks")));
        Check.equal(helper, BlacklistSnapshot.EMPTY.generation(), 0L,
                "BlacklistSnapshot.EMPTY.generation()");
        for (BlacklistSnapshot empty
                : List.of(BlacklistSnapshot.EMPTY, TestGame.snapshot(builder -> { }))) {
            String name = empty == BlacklistSnapshot.EMPTY ? "EMPTY" : "a built empty snapshot";
            Check.isTrue(helper, empty.isEmpty(), name + " is empty");
            boolean any = empty.item(Items.OAK_PLANKS)
                    || empty.item(TestGame.key(Registries.ITEM, "oak_planks"))
                    || empty.itemTag(planksTag) || empty.itemTagEmptied(planksTag)
                    || empty.potion(TestGame.key(Registries.POTION, "strength"))
                    || empty.potion(strength)
                    || empty.enchantment(TestGame.key(Registries.ENCHANTMENT, "mending"))
                    || empty.enchantment(mending)
                    || empty.enchantmentTag(curse) || empty.enchantmentTagEmptied(curse)
                    || empty.itemId("minecraft:oak_planks") || empty.itemTagId("minecraft:planks")
                    || empty.potionId("minecraft:strength")
                    || empty.enchantmentId("minecraft:mending");
            Check.isTrue(helper, !any, "No query of " + name + " answers true");
            Check.isTrue(helper, empty.items().isEmpty() && empty.explicitItems().isEmpty()
                    && empty.itemTags().isEmpty() && empty.itemTagMembers().isEmpty()
                    && empty.emptiedItemTags().isEmpty() && empty.potions().isEmpty()
                    && empty.enchantments().isEmpty() && empty.explicitEnchantments().isEmpty()
                    && empty.enchantmentTags().isEmpty()
                    && empty.enchantmentTagMembers().isEmpty()
                    && empty.emptiedEnchantmentTags().isEmpty(), name + " holds nothing");
            Check.equal(helper, StackRules.reason(empty, planks), StackRules.Reason.NONE,
                    "the reason for planks with " + name);
            TagFilter.Result<Item> result =
                    TagFilter.apply(empty, Registries.ITEM, incoming, Recorder.NONE);
            Check.isTrue(helper, result.tags() == incoming,
                    "TagFilter.apply with " + name + " returns its input itself");
        }
        helper.succeed();
    }

    /**
     * Every public member of Keys, Lookups, Components, CloneStacks and IdText runs, so every
     * backend of each window links on every boot.
     */
    static void facadesLink(GameTestHelper helper) {
        MinecraftServer server = TestGame.server(helper);
        HolderLookup.Provider provider = server.registryAccess();
        // Keys: every public member
        ResourceKey<Item> stone = Keys.of(Registries.ITEM, "minecraft", "stone");
        Check.equal(helper, Keys.name(stone), "minecraft:stone",
                "Keys.name(Keys.of(ITEM, minecraft, stone))");
        Check.equal(helper, BuiltInRegistries.ITEM.getResourceKey(Items.STONE), Optional.of(stone),
                "the registry's key of stone");
        Check.equal(helper, Keys.name(Keys.of(Registries.ITEM, "core_probe")),
                "item_blacklist:core_probe", "Keys.of(ITEM, core_probe)");
        TagKey<Item> planks = Keys.tag(Registries.ITEM, "minecraft", "planks");
        Check.equal(helper, Keys.name(planks), "minecraft:planks",
                "Keys.name(Keys.tag(ITEM, minecraft, planks))");
        // P is inferred as CustomPacketPayload; the mod's own payload comes later.
        CustomPacketPayload.Type<?> probe = Keys.payloadType("core_probe");
        Check.equal(helper, Keys.name(probe), "item_blacklist:core_probe",
                "Keys.name(Keys.payloadType(core_probe))");
        // Lookups: every public member
        ResourceKey<Item> oakLog = TestGame.key(Registries.ITEM, "oak_log");
        TagKey<Item> logs = Keys.tag(Registries.ITEM, "minecraft", "logs");
        TagKey<Item> modTag = Keys.tag(Registries.ITEM, "mod_id", "mod_tag");
        Check.isTrue(helper, Lookups.of(provider, Registries.ENCHANTMENT) != null,
                "Lookups.of(ENCHANTMENT)");
        Optional<Holder.Reference<Item>> stoneHolder =
                Lookups.holder(provider, Registries.ITEM, stone);
        Check.isTrue(helper, stoneHolder.isPresent(), "Lookups.holder(ITEM, stone)");
        Check.isTrue(helper, Lookups.holder(provider, Registries.ENCHANTMENT,
                        TestGame.key(Registries.ENCHANTMENT, "mending")).isPresent(),
                "Lookups.holder(ENCHANTMENT, mending)");
        Check.isTrue(helper, Lookups.holder(provider, Registries.ITEM,
                        Keys.of(Registries.ITEM, "mod_id", "mod_item")).isEmpty(),
                "Lookups.holder(ITEM, mod_id:mod_item) is empty");
        Check.isTrue(helper, Lookups.members(provider, Registries.ITEM, logs).contains(oakLog),
                "#minecraft:logs holds oak_log");
        Check.isTrue(helper, Lookups.members(provider, Registries.ITEM, modTag).isEmpty(),
                "an undefined tag has no members");
        Check.isTrue(helper, Lookups.tagKnown(provider, Registries.ITEM, logs),
                "#minecraft:logs is known");
        Check.isTrue(helper, !Lookups.tagKnown(provider, Registries.ITEM, modTag),
                "#mod_id:mod_tag is not known");
        List<Holder<Item>> holders = List.<Holder<Item>>of(stoneHolder.get(),
                TestGame.holder(helper, Registries.ITEM, "oak_log"));
        Check.equal(helper, Lookups.keys(holders), Set.of(stone, oakLog), "Lookups.keys");
        // Components
        ResourceKey<Enchantment> mendingKey = TestGame.key(Registries.ENCHANTMENT, "mending");
        ItemEnchantments stored = Components.get(TestGame.book(helper, "mending"),
                DataComponents.STORED_ENCHANTMENTS);
        Check.isTrue(helper, stored != null && stored.keySet().stream()
                        .anyMatch(h -> h.unwrapKey().equals(Optional.of(mendingKey))),
                "Components.get(book, STORED_ENCHANTMENTS) holds mending");
        Check.isTrue(helper,
                Components.get(new ItemStack(Items.STONE), DataComponents.POTION_CONTENTS) == null,
                "Components.get(stone, POTION_CONTENTS) is null");
        // CloneStacks
        helper.setBlock(PLANKS_POS, Blocks.OAK_PLANKS);
        ItemStack clone = CloneStacks.of(helper.getLevel(), helper.absolutePos(PLANKS_POS),
                helper.getBlockState(PLANKS_POS));
        Check.isTrue(helper, clone.getItem() == Items.OAK_PLANKS,
                "The clone stack of oak planks is oak planks");
        // IdText
        Check.equal(helper, IdText.parse("minecraft:stone"), Optional.of(mc("stone")),
                "IdText.parse(minecraft:stone)");
        Check.equal(helper, IdText.parse(":stone"), Optional.of(mc("stone")),
                "IdText.parse(:stone)");
        Check.equal(helper, IdText.parse("stone"), Optional.empty(), "IdText.parse(stone)");
        Check.equal(helper, IdText.parse("..:x"), Optional.empty(), "IdText.parse(..:x)");
        Check.equal(helper, IdText.parseLenient("stone"), Optional.of(mc("stone")),
                "IdText.parseLenient(stone)");
        Check.equal(helper, mc("stone").toString(), "minecraft:stone", "IdText.toString()");
        List<String> sorted = new ArrayList<>(List.of("b:a", "a:b", "a:a"));
        sorted.sort(IdText.PATH_FIRST);
        Check.equal(helper, sorted, List.of("a:a", "b:a", "a:b"), "IdText.PATH_FIRST");
        helper.succeed();
    }

    /**
     * The three unknown entries and the two undefined tags are reported, and only they;
     * validation never edits the config; an undefined tag stays. A fresh resolve is compared by
     * its explicit sets only: the bound #planks and #curse are empty by now.
     */
    static void unknownEntriesReported(GameTestHelper helper) {
        ServerState state = requireState(helper);
        BlacklistSnapshot live = TestGame.live();
        Resolver.Result result =
                Resolver.resolve(state.config(), TestGame.server(helper).registryAccess());
        Check.equal(helper, result.unknown(), List.of("item mod_id:mod_item",
                "potion mod_id:mod_potion", "enchantment mod_id:mod_enchantment"),
                "the unknown entries");
        Check.equal(helper, result.undefinedTags(), List.of("item tag #mod_id:mod_tag",
                "enchantment tag #mod_id:mod_enchantment_tag"), "the undefined tags");
        Check.equal(helper, result.snapshot().explicitItems(), live.explicitItems(),
                "explicit items of a fresh resolve");
        Check.equal(helper, result.snapshot().potions(), live.potions(),
                "potions of a fresh resolve");
        Check.equal(helper, result.snapshot().explicitEnchantments(), live.explicitEnchantments(),
                "explicit enchantments of a fresh resolve");
        TagKey<Item> modTag = Keys.tag(Registries.ITEM, "mod_id", "mod_tag");
        Check.isTrue(helper, result.snapshot().itemTags().contains(modTag),
                "An undefined tag is reported and still kept");
        BlacklistConfig config = state.config();
        Check.isTrue(helper, config.items().contains(id("mod_id", "mod_item")),
                "The config still holds mod_id:mod_item");
        Check.isTrue(helper, config.itemTags().contains(id("mod_id", "mod_tag")),
                "The config still holds #mod_id:mod_tag");
        Check.isTrue(helper, config.potions().contains(id("mod_id", "mod_potion")),
                "The config still holds mod_id:mod_potion");
        Check.isTrue(helper, config.enchantments().contains(id("mod_id", "mod_enchantment")),
                "The config still holds mod_id:mod_enchantment");
        Check.isTrue(helper, config.enchantmentTags().contains(id("mod_id", "mod_enchantment_tag")),
                "The config still holds #mod_id:mod_enchantment_tag");
        helper.succeed();
    }

    /** Air is unknown to the resolver and dropped by the builder; empty stacks stay allowed. */
    static void airNeverBlacklisted(GameTestHelper helper) {
        BlacklistConfig config = new BlacklistConfig(false, List.of(mc("air"), mc("stone")),
                List.of(), List.of(), List.of(), List.of());
        Resolver.Result result =
                Resolver.resolve(config, TestGame.server(helper).registryAccess());
        BlacklistSnapshot snapshot = result.snapshot();
        ResourceKey<Item> air = TestGame.key(Registries.ITEM, "air");
        Check.isTrue(helper, snapshot.item(Items.STONE),
                "stone of the built config is blacklisted");
        Check.isTrue(helper, !snapshot.item(Items.AIR) && !snapshot.item(air),
                "air is never blacklisted");
        Check.equal(helper, result.unknown().size(), 1,
                "the number of unknown entries " + result.unknown());
        Check.equal(helper, StackRules.reason(snapshot, ItemStack.EMPTY), StackRules.Reason.NONE,
                "the reason for the empty stack");
        Check.equal(helper, StackRules.reason(snapshot, new ItemStack(Items.AIR)),
                StackRules.Reason.NONE, "the reason for an air stack");
        BlacklistSnapshot built = TestGame.snapshot(builder -> builder.item(air));
        Check.isTrue(helper, !built.item(Items.AIR) && !built.item(air),
                "Builder.build() drops air");
        helper.succeed();
    }

    /**
     * A second start hook changes nothing: the state, its snapshot and its reload count stay;
     * the per-server slot keeps one value per type.
     */
    static void hooksIdempotent(GameTestHelper helper) {
        MinecraftServer server = TestGame.server(helper);
        ServerState before = requireState(helper);
        int reloads = before.reloads();
        BlacklistSnapshot snapshot = before.snapshot();
        ItemBlacklistMod.onServerStarting(server);
        ItemBlacklistMod.onServerStarted(server);
        ServerState after = requireState(helper);
        Check.isTrue(helper, after == before, "A second start hook keeps the server's state");
        Check.equal(helper, after.reloads(), reloads, "reloads after a second start hook");
        Check.isTrue(helper, after.snapshot() == snapshot,
                "A second start hook keeps the snapshot");
        Check.isTrue(helper, after.startReloadDone() && after.startReloadSucceeded(),
                "The start reload stays done");
        SlotProbe first = after.slot(SlotProbe.class, SlotProbe::new);
        SlotProbe second = after.slot(SlotProbe.class, SlotProbe::new);
        Check.isTrue(helper, first != null && first == second,
                "ServerState.slot keeps one value per type");
        helper.succeed();
    }

    /**
     * A synced snapshot that reaches a JVM with a running server changes nothing its hooks see;
     * the remote field is cleared again in finally.
     */
    static void remoteIgnoredWithServer(GameTestHelper helper) {
        BlacklistSnapshot server = Blacklist.server();
        BlacklistSnapshot fake = TestGame.snapshot(
                builder -> builder.item(TestGame.key(Registries.ITEM, "stone")));
        try {
            Blacklist.acceptRemote(fake);
            Check.isTrue(helper, Blacklist.effective() == Blacklist.server(),
                    "effective() is the server's snapshot while a server runs");
            Check.isTrue(helper, !Blacklist.effective().item(Items.STONE),
                    "A synced snapshot never reaches a running server's hooks");
            Check.isTrue(helper, Blacklist.server() == server,
                    "acceptRemote leaves the server's snapshot alone");
        } finally {
            Blacklist.clearRemote();
        }
        helper.succeed();
    }

    /**
     * The parser's rules on the Gson the running release ships: a leading BOM, comments, a
     * trailing comma in an array, trimming, a duplicate, one warning per bad key or entry, and
     * a syntax error that gives the empty config.
     */
    static void configParseOnGameGson(GameTestHelper helper) {
        String text = "\uFEFF{\n"
                + "  // a line comment\n"
                + "  /* a block comment */\n"
                + "  \"Detailed Log\": \"yes\",\n"
                + "  \"Items\": [\"minecraft:stone\", \" minecraft:stone \", \"..:x\","
                + " \"#minecraft:logs\",],\n"
                + "  \"Potions\": 5,\n"
                + "  \"Enchantments\": [\"minecraft:mending\"]\n"
                + "}\n";
        ConfigParser.Result result = ConfigParser.parse(new StringReader(text));
        BlacklistConfig expected = new BlacklistConfig(false, List.of(mc("stone")),
                List.of(mc("logs")), List.of(), List.of(mc("mending")), List.of());
        Check.equal(helper, result.config(), expected, "the parsed config");
        Check.equal(helper, result.warnings().size(), 3,
                "the number of warnings (Detailed Log, ..:x, Potions) " + result.warnings());
        ConfigParser.Result dangling =
                ConfigParser.parse(new StringReader("{\"Items\": [\"minecraft:stone\"],}"));
        Check.equal(helper, dangling.config(), BlacklistConfig.EMPTY,
                "the config of a file with ',}'");
        Check.isTrue(helper, !dangling.warnings().isEmpty(), "A file with ',}' gives a warning");
        helper.succeed();
    }

    private static IdText id(String namespace, String path) {
        return new IdText(namespace, path);
    }

    private static IdText mc(String path) {
        return new IdText(IdText.DEFAULT_NAMESPACE, path);
    }

    /** The running server's state; fails the scenario when Lifecycle.starting never ran. */
    private static ServerState requireState(GameTestHelper helper) {
        ServerState state = Blacklist.serverState();
        if (state == null) {
            Check.fail(helper, "No server state: Lifecycle.starting did not run");
            throw new IllegalStateException("unreachable");
        }
        return state;
    }

    /** The fixture parsed from the test jar, as each loader's test mod parses it at init. */
    private static ConfigParser.Result readFixture(GameTestHelper helper) {
        InputStream in = CoreScenarios.class.getResourceAsStream("/" + Fixtures.CONFIG_RESOURCE);
        if (in == null) {
            Check.fail(helper, "The test jar lacks " + Fixtures.CONFIG_RESOURCE);
            throw new IllegalStateException("unreachable");
        }
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return ConfigParser.parse(reader);
        } catch (IOException e) {
            Check.fail(helper, "Cannot read " + Fixtures.CONFIG_RESOURCE + ": " + e);
            throw new IllegalStateException("unreachable", e);
        }
    }

    /** StackRules.reason and blacklisted agree with the expected reason. */
    private static void expectReason(GameTestHelper helper, BlacklistSnapshot snapshot,
            String what, ItemStack stack, StackRules.Reason expected) {
        Check.equal(helper, StackRules.reason(snapshot, stack), expected,
                "the reason for " + what);
        Check.equal(helper, StackRules.blacklisted(snapshot, stack),
                expected != StackRules.Reason.NONE, "blacklisted for " + what);
    }

    /** StackRules.blockBlacklisted for the block the helper placed at a relative position. */
    private static void expectBlock(GameTestHelper helper, BlacklistSnapshot snapshot,
            String what, BlockPos pos, boolean expected) {
        BlockState state = helper.getBlockState(pos);
        Check.equal(helper, StackRules.blockBlacklisted(snapshot, helper.getLevel(),
                helper.absolutePos(pos), state), expected, "blockBlacklisted for " + what);
    }

    /** Adds the name when its namespace is mod_id. */
    private static void addIfModId(List<String> found, String name) {
        if (name.startsWith("mod_id:")) {
            found.add(name);
        }
    }

    /** A value only core_hooks_idempotent stores in the server state's slots. */
    private static final class SlotProbe {
    }
}
