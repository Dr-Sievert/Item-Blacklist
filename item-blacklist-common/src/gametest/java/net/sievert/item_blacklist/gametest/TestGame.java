package net.sievert.item_blacklist.gametest;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.Lookups;
import net.sievert.item_blacklist.id.IdText;
import net.sievert.item_blacklist.line.Keys;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The scenarios' access to the game, one text for every release: each member names only what
 * every release has in the same form, so a scenario that goes through here compiles once and
 * links everywhere. Set-up goes through lookups by key, commands and built snapshots, never
 * through a constant or a call that changes inside a line (Potions.X, EntityType.X, the id
 * class, vanilla's mock player).
 */
public final class TestGame {
    private TestGame() {
    }

    /** The test's server: helper.getLevel().getServer(). */
    public static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    /** The running server's snapshot, as the fixture made it: Blacklist.server(). */
    public static BlacklistSnapshot live() {
        return Blacklist.server();
    }

    /** A snapshot built for one scenario: another blacklist than the fixture's needs no config. */
    public static BlacklistSnapshot snapshot(Consumer<BlacklistSnapshot.Builder> content) {
        BlacklistSnapshot.Builder builder = BlacklistSnapshot.builder();
        content.accept(builder);
        return builder.build();
    }

    /** A key in namespace "minecraft": Keys.of(registry, "minecraft", path). */
    public static <T> ResourceKey<T> key(ResourceKey<? extends Registry<T>> registry, String path) {
        return Keys.of(registry, IdText.DEFAULT_NAMESPACE, path);
    }

    /** The holder from the level's registries; fails the scenario naming the key when absent. */
    public static <T> Holder.Reference<T> holder(GameTestHelper helper,
            ResourceKey<? extends Registry<T>> registry, String path) {
        ResourceKey<T> key = key(registry, path);
        Optional<Holder.Reference<T>> found =
                Lookups.holder(helper.getLevel().registryAccess(), registry, key);
        if (found.isEmpty()) {
            Check.fail(helper, "No " + Keys.name(key) + " in " + Keys.name(registry));
            throw new IllegalStateException("unreachable");
        }
        return found.get();
    }

    /** A stack of the item holding the potion "minecraft:<potion>". */
    public static ItemStack potionStack(GameTestHelper helper, Item item, String potion) {
        return PotionContents.createItemStack(item, holder(helper, Registries.POTION, potion));
    }

    /** The item with the enchantments "minecraft:<path>", each at level 1, in ENCHANTMENTS. */
    public static ItemStack enchanted(GameTestHelper helper, Item item, String... enchantments) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.ENCHANTMENTS, enchantments(helper, enchantments));
        return stack;
    }

    /** An enchanted book storing the enchantments "minecraft:<path>", each at level 1. */
    public static ItemStack book(GameTestHelper helper, String... storedEnchantments) {
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        stack.set(DataComponents.STORED_ENCHANTMENTS, enchantments(helper, storedEnchantments));
        return stack;
    }

    /**
     * Runs a command as the server, in the test's level at its origin, without output; the
     * command's result. A syntax error fails the scenario.
     */
    public static int run(GameTestHelper helper, String command) {
        MinecraftServer server = server(helper);
        CommandSourceStack source = server.createCommandSourceStack()
                .withLevel(helper.getLevel())
                .withPosition(helper.absoluteVec(Vec3.ZERO))
                .withSuppressedOutput();
        try {
            return server.getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            Check.fail(helper, "Command failed: " + command + ": " + e.getMessage());
            return 0;
        }
    }

    /**
     * "summon entity ~0.5 ~1 ~0.5 [nbt]" at the test's origin; the one entity near the test
     * that was not there before. Found by its UUID, since Entity.getTags() is renamed on 26.x.
     */
    public static Entity summon(GameTestHelper helper, String entity, String nbt) {
        AABB box = helper.getBounds().inflate(8);
        Set<UUID> before = uuids(helper, box);
        run(helper, "summon " + entity + " ~0.5 ~1 ~0.5" + (nbt.isEmpty() ? "" : " " + nbt));
        List<Entity> added = helper.getLevel().getEntities((Entity) null, box,
                candidate -> !before.contains(candidate.getUUID()));
        if (added.size() != 1) {
            Check.fail(helper, "Expected one new " + entity + ", found " + added.size());
            throw new IllegalStateException("unreachable");
        }
        return added.get(0);
    }

    /**
     * "loot spawn ~0.5 ~1 ~0.5 loot table", the table by its full id "namespace:path"; the
     * stacks of the item entities that call spawned, which are then discarded.
     */
    public static List<ItemStack> lootSpawn(GameTestHelper helper, String table) {
        AABB box = helper.getBounds().inflate(8);
        Set<UUID> before = uuids(helper, box);
        run(helper, "loot spawn ~0.5 ~1 ~0.5 loot " + table);
        List<ItemStack> stacks = new ArrayList<>();
        for (Entity entity : helper.getLevel().getEntities((Entity) null, box,
                candidate -> candidate instanceof ItemEntity
                        && !before.contains(candidate.getUUID()))) {
            stacks.add(((ItemEntity) entity).getItem().copy());
            entity.discard();
        }
        return stacks;
    }

    /**
     * The loaded table of this full id, encoded as the game encodes it; the encoding of the
     * empty table for an absent one. Never throws: an id that does not parse or a table that
     * does not encode gives an empty JSON object.
     */
    public static JsonElement lootJson(GameTestHelper helper, String table) {
        IdText id = IdText.parseLenient(table)
                .orElse(new IdText(IdText.DEFAULT_NAMESPACE, "empty"));
        ResourceKey<LootTable> key = Keys.of(Registries.LOOT_TABLE, id.namespace(), id.path());
        MinecraftServer server = server(helper);
        LootTable loaded = server.reloadableRegistries().getLootTable(key);
        HolderLookup.Provider provider = server.registryAccess();
        return LootTable.DIRECT_CODEC
                .encodeStart(provider.createSerializationContext(JsonOps.INSTANCE), loaded)
                .result()
                .orElseGet(JsonObject::new);
    }

    /** The block entity at a position relative to the test, or null. */
    public static BlockEntity blockEntity(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getBlockEntity(helper.absolutePos(pos));
    }

    /** Each named enchantment in namespace "minecraft" at level 1. */
    private static ItemEnchantments enchantments(GameTestHelper helper, String... paths) {
        ItemEnchantments.Mutable enchantments =
                new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (String path : paths) {
            enchantments.set(holder(helper, Registries.ENCHANTMENT, path), 1);
        }
        return enchantments.toImmutable();
    }

    /** The UUIDs of the entities in the box, before a command adds one. */
    private static Set<UUID> uuids(GameTestHelper helper, AABB box) {
        Set<UUID> uuids = new HashSet<>();
        for (Entity entity : helper.getLevel().getEntities((Entity) null, box, candidate -> true)) {
            uuids.add(entity.getUUID());
        }
        return uuids;
    }
}
