package net.sievert.item_blacklist.gametest;

import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.itemuse.ItemUse;
import net.sievert.item_blacklist.itemuse.ItemUseTexts;
import net.sievert.item_blacklist.line.Interactions;
import net.sievert.item_blacklist.line.Tooltips;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The item-use scenarios (prefix use_): holding, receiving, picking up, block use, tooltips and
 * repairs, on a real survival player from TestPlayers. Each is synchronous: set up, act, assert
 * and succeed inside its call, and each holds before and after a reload, since it reads the
 * live snapshot at call time. Every expectation is the same on all releases: the repair hook
 * changes at 1.21.2 and the tooltip component at 1.21.5 inside the hooks and facades. No method
 * of InteractionResult is called (an enum up to 1.21.1, a sealed interface from 1.21.2): results
 * are compared by identity with Interactions.consume().
 */
public final class ItemUseScenarios {
    /** The centre of the 3x3x3 "empty" structure, clear of the cells the scenarios build in. */
    private static final BlockPos CENTRE = new BlockPos(1, 1, 1);

    private ItemUseScenarios() {
    }

    /** The use_ scenarios; called by ItemBlacklistGameTests.register. */
    public static void register(BiConsumer<String, Consumer<GameTestHelper>> sink) {
        sink.accept("use_add_removes", ItemUseScenarios::addRemoves);
        sink.accept("use_block_blocked", ItemUseScenarios::blockBlocked);
        sink.accept("use_creative_exempt", ItemUseScenarios::creativeExempt);
        sink.accept("use_facades_link", ItemUseScenarios::facadesLink);
        sink.accept("use_pickup_removes", ItemUseScenarios::pickupRemoves);
        sink.accept("use_purge_built", ItemUseScenarios::purgeBuilt);
        sink.accept("use_repair_refused", ItemUseScenarios::repairRefused);
        sink.accept("use_spectator_exempt", ItemUseScenarios::spectatorExempt);
        sink.accept("use_tick_removes", ItemUseScenarios::tickRemoves);
        sink.accept("use_tooltip_line", ItemUseScenarios::tooltipLine);
    }

    /**
     * The inventory tick deletes planks in a main slot, on the head, in the offhand and on the
     * cursor, and a sword with mending (the enchantment reason); stone stays. From 1.21.5
     * Inventory.tick itself only ticks the main slots, so this also proves the hook's own scan.
     */
    static void tickRemoves(GameTestHelper helper) {
        TestPlayers.withSurvival(helper, player -> {
            Inventory inventory = player.getInventory();
            inventory.setItem(0, new ItemStack(Items.OAK_PLANKS, 5));
            inventory.setItem(1, new ItemStack(Items.STONE, 3));
            inventory.setItem(2, TestGame.enchanted(helper, Items.IRON_SWORD, "mending"));
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.OAK_PLANKS));
            player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.OAK_PLANKS));
            player.containerMenu.setCarried(new ItemStack(Items.OAK_PLANKS, 2));
            inventory.tick();
            Check.isTrue(helper, inventory.getItem(0).isEmpty(),
                    "planks in slot 0 must be deleted");
            Check.isTrue(helper, player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),
                    "planks on the head must be deleted");
            Check.isTrue(helper, player.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty(),
                    "planks in the offhand must be deleted");
            Check.isTrue(helper, player.containerMenu.getCarried().isEmpty(),
                    "planks on the cursor must be deleted");
            Check.isTrue(helper, inventory.getItem(2).isEmpty(),
                    "a sword with mending must be deleted");
            Check.equal(helper, count(player, Items.STONE), 3, "stone count after the tick");
        });
        helper.succeed();
    }

    /**
     * A creative player keeps planks at the tick and at add: creative is read from the game
     * mode, the call that replaces Player.isCreative() (renamed on Fabric at 1.21.5).
     */
    static void creativeExempt(GameTestHelper helper) {
        TestPlayers.withSurvival(helper, player -> {
            player.setGameMode(GameType.CREATIVE);
            Inventory inventory = player.getInventory();
            inventory.setItem(0, new ItemStack(Items.OAK_PLANKS, 5));
            inventory.tick();
            Check.equal(helper, count(player, Items.OAK_PLANKS), 5,
                    "planks kept by a creative player at the tick");
            ItemStack received = new ItemStack(Items.OAK_PLANKS, 3);
            Check.isTrue(helper, inventory.add(received),
                    "add must accept planks for a creative player");
            Check.equal(helper, count(player, Items.OAK_PLANKS), 8,
                    "planks kept by a creative player at add");
        });
        helper.succeed();
    }

    /**
     * add empties a blacklisted stack and still answers true, as the old mod did; stone is
     * added. add(ItemStack) delegates to the hooked add(int, ItemStack) on every release.
     */
    static void addRemoves(GameTestHelper helper) {
        TestPlayers.withSurvival(helper, player -> {
            Inventory inventory = player.getInventory();
            ItemStack planks = new ItemStack(Items.OAK_PLANKS, 4);
            Check.isTrue(helper, inventory.add(planks), "add must report success for planks");
            Check.isTrue(helper, planks.isEmpty(), "the added planks stack must be emptied");
            Check.equal(helper, count(player, Items.OAK_PLANKS), 0, "planks held after add");
            ItemStack stone = new ItemStack(Items.STONE, 2);
            Check.isTrue(helper, inventory.add(stone), "add must accept stone");
            Check.equal(helper, count(player, Items.STONE), 2, "stone held after add");
        });
        helper.succeed();
    }

    /**
     * The real pickup path, ItemEntity.playerTouch: a planks item entity is taken and gone
     * without reaching the inventory; a stone one is picked up. isAlive, not isRemoved, which is
     * declared by another interface from 1.21.5.
     */
    static void pickupRemoves(GameTestHelper helper) {
        TestPlayers.withSurvival(helper, player -> {
            BlockPos at = helper.absolutePos(CENTRE);
            ItemEntity planks = new ItemEntity(helper.getLevel(), at.getX() + 0.5, at.getY(),
                    at.getZ() + 0.5, new ItemStack(Items.OAK_PLANKS, 3));
            planks.setNoPickUpDelay();
            helper.getLevel().addFreshEntity(planks);
            planks.playerTouch(player);
            Check.isTrue(helper, !planks.isAlive(), "a touched planks item entity must be gone");
            Check.equal(helper, count(player, Items.OAK_PLANKS), 0, "planks held after pickup");
            ItemEntity stone = new ItemEntity(helper.getLevel(), at.getX() + 0.5, at.getY(),
                    at.getZ() + 0.5, new ItemStack(Items.STONE, 3));
            stone.setNoPickUpDelay();
            helper.getLevel().addFreshEntity(stone);
            stone.playerTouch(player);
            Check.equal(helper, count(player, Items.STONE), 3, "stone held after pickup");
        });
        helper.succeed();
    }

    /**
     * A right click with stone on planks gives Interactions.consume() and places nothing; the
     * same click on stone places stone. Placing is synchronous inside useItemOn. The player
     * stands on the centre column, clear of both target cells, so the control's placement is
     * not refused for a collision; slot 0 is a new player's main hand.
     */
    static void blockBlocked(GameTestHelper helper) {
        BlockPos planks = new BlockPos(0, 1, 0);
        BlockPos stone = new BlockPos(2, 1, 2);
        helper.setBlock(planks, Blocks.OAK_PLANKS);
        helper.setBlock(planks.above(), Blocks.AIR);
        helper.setBlock(stone, Blocks.STONE);
        helper.setBlock(stone.above(), Blocks.AIR);
        TestPlayers.withSurvival(helper, player -> {
            standAtCentre(helper, player);
            ItemStack held = new ItemStack(Items.STONE, 4);
            player.getInventory().setItem(0, held);
            InteractionResult onPlanks = clickTop(helper, player, planks, held);
            Check.isTrue(helper, onPlanks == Interactions.consume(),
                    "a click on planks must give Interactions.consume()");
            Check.isTrue(helper, helper.getBlockState(planks.above()).isAir(),
                    "nothing may be placed on planks");
            Check.equal(helper, held.getCount(), 4, "held stone after the refused click");
            clickTop(helper, player, stone, held);
            Check.isTrue(helper, helper.getBlockState(stone.above()).is(Blocks.STONE),
                    "stone must be placed on stone");
        });
        helper.succeed();
    }

    /**
     * A spectator's click on planks is not refused: vanilla answers PASS for a block without a
     * menu, so the result is not the facade's CONSUME exactly when the hook let it through.
     */
    static void spectatorExempt(GameTestHelper helper) {
        BlockPos planks = new BlockPos(0, 1, 0);
        helper.setBlock(planks, Blocks.OAK_PLANKS);
        TestPlayers.withSurvival(helper, player -> {
            standAtCentre(helper, player);
            player.setGameMode(GameType.SPECTATOR);
            InteractionResult result = clickTop(helper, player, planks, ItemStack.EMPTY);
            Check.isTrue(helper, result != Interactions.consume(),
                    "a spectator's click on planks must not be refused");
        });
        helper.succeed();
    }

    /**
     * The last tooltip line of planks and of a mending book is the mod's (the item and the
     * enchantment reason); stone has none. On a server the translatable resolves through the
     * server's language, which lacks the key, so the English fallback shows.
     */
    static void tooltipLine(GameTestHelper helper) {
        Item.TooltipContext context = Item.TooltipContext.of(helper.getLevel());
        List<Component> planks =
                new ItemStack(Items.OAK_PLANKS).getTooltipLines(context, null, TooltipFlag.NORMAL);
        Check.equal(helper, lastLine(planks), ItemUseTexts.TOOLTIP_FALLBACK,
                "last tooltip line of planks");
        List<Component> book = TestGame.book(helper, "mending")
                .getTooltipLines(context, null, TooltipFlag.NORMAL);
        Check.equal(helper, lastLine(book), ItemUseTexts.TOOLTIP_FALLBACK,
                "last tooltip line of a mending book");
        List<Component> stone =
                new ItemStack(Items.STONE).getTooltipLines(context, null, TooltipFlag.NORMAL);
        for (Component line : stone) {
            Check.isTrue(helper, !ItemUseTexts.TOOLTIP_FALLBACK.equals(line.getString()),
                    "stone must have no blacklist line");
        }
        helper.succeed();
    }

    /**
     * An elytra and phantom membrane (a direct repair set, not a tag) give no anvil result; an
     * iron sword and an iron ingot give one. The refusal is AnvilMenuRepairMixinUntil1_21_2's on
     * 1.21.1 and ItemStackRepairMixinSince1_21_2's from 1.21.2: one expectation, two hooks.
     * Slots 0 and 1 are the inputs, 2 the result.
     */
    static void repairRefused(GameTestHelper helper) {
        TestPlayers.withSurvival(helper, player -> {
            AnvilMenu refused = new AnvilMenu(0, player.getInventory());
            ItemStack elytra = new ItemStack(Items.ELYTRA);
            elytra.setDamageValue(100);
            refused.getSlot(0).set(elytra);
            refused.getSlot(1).set(new ItemStack(Items.PHANTOM_MEMBRANE));
            refused.createResult();
            Check.isTrue(helper, refused.getSlot(2).getItem().isEmpty(),
                    "elytra + phantom membrane must give no result");
            AnvilMenu allowed = new AnvilMenu(0, player.getInventory());
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            sword.setDamageValue(100);
            allowed.getSlot(0).set(sword);
            allowed.getSlot(1).set(new ItemStack(Items.IRON_INGOT));
            allowed.createResult();
            Check.isTrue(helper, !allowed.getSlot(2).getItem().isEmpty(),
                    "iron sword + iron ingot must give a result");
        });
        helper.succeed();
    }

    /**
     * Calls every member of the two facades, so every Fabric 1.21.x boot links the backend the
     * release picks. The true branch of Tooltips.hidden is left to a hand check: the component
     * and its command syntax differ across the line.
     */
    static void facadesLink(GameTestHelper helper) {
        InteractionResult consume = Interactions.consume();
        Check.isTrue(helper, consume != null && consume == Interactions.consume(),
                "Interactions.consume() must be one constant");
        Check.isTrue(helper, !Tooltips.hidden(new ItemStack(Items.STONE)),
                "a plain stone stack must not hide its tooltip");
        helper.succeed();
    }

    /**
     * The purge on a built snapshot: the empty one deletes nothing, a stick-only one deletes the
     * sticks and keeps stone. stick is a control of the fixture, so the live hooks never touch
     * it and only the snapshot handed in decides.
     */
    static void purgeBuilt(GameTestHelper helper) {
        BlacklistSnapshot sticks =
                TestGame.snapshot(b -> b.item(TestGame.key(Registries.ITEM, "stick")));
        TestPlayers.withSurvival(helper, player -> {
            Inventory inventory = player.getInventory();
            inventory.setItem(0, new ItemStack(Items.STICK, 7));
            inventory.setItem(1, new ItemStack(Items.STONE, 3));
            Check.equal(helper, ItemUse.purge(BlacklistSnapshot.EMPTY, player), 0,
                    "stacks purged with the empty snapshot");
            Check.equal(helper, ItemUse.purge(sticks, player), 1,
                    "stacks purged with a stick-only snapshot");
            Check.equal(helper, count(player, Items.STICK), 0, "sticks held after the purge");
            Check.equal(helper, count(player, Items.STONE), 3, "stone held after the purge");
        });
        helper.succeed();
    }

    /** How many of the item the player holds: every inventory slot and the cursor. */
    private static int count(ServerPlayer player, Item item) {
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        ItemStack carried = player.containerMenu.getCarried();
        return carried.is(item) ? total + carried.getCount() : total;
    }

    /** Stands the player on the structure's centre column. */
    private static void standAtCentre(GameTestHelper helper, ServerPlayer player) {
        BlockPos centre = helper.absolutePos(CENTRE);
        player.setPos(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5);
    }

    /**
     * A right click from above on the block at the relative position, as the game's packet
     * listener makes it; the result, compared by identity only.
     */
    private static InteractionResult clickTop(GameTestHelper helper, ServerPlayer player,
            BlockPos relative, ItemStack held) {
        BlockPos absolute = helper.absolutePos(relative);
        return player.gameMode.useItemOn(player, helper.getLevel(), held,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
    }

    /** The text of the last line, "" for an empty tooltip. */
    private static String lastLine(List<Component> lines) {
        return lines.isEmpty() ? "" : lines.get(lines.size() - 1).getString();
    }
}
