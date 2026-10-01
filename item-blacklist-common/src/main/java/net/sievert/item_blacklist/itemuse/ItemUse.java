package net.sievert.item_blacklist.itemuse;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.Lookups;
import net.sievert.item_blacklist.blacklist.ServerState;
import net.sievert.item_blacklist.blacklist.StackRules;
import net.sievert.item_blacklist.line.Keys;
import net.sievert.item_blacklist.log.Log;
import net.sievert.item_blacklist.log.LogTag;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the item-use hooks do once the stack rule said "blacklisted": the one place that
 * deletes a stack, tells the player and writes the DEBUG line, so the three mixins stay a fetch,
 * a rule and an apply. Per-use hooks never record into the report; with "Detailed Log" they
 * log at DEBUG only. Names nothing that moves inside a line: creative is read from the game
 * mode (Player.isCreative() is renamed on Fabric at 1.21.5) and messages go through
 * sendSystemMessage (displayClientMessage is gone on 26.x).
 */
public final class ItemUse {
    private ItemUse() {
    }

    /** Whether the player is in creative, read without Player.isCreative(). */
    public static boolean creative(ServerPlayer player) {
        return player.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
    }

    /**
     * Whether the player is exempt from the block rule: creative players may use anything, and
     * spectators interact with nothing a blacklist protects (they only open menus).
     */
    public static boolean exemptFromBlocks(ServerPlayer player) {
        GameType mode = player.gameMode.getGameModeForPlayer();
        return mode == GameType.CREATIVE || mode == GameType.SPECTATOR;
    }

    /**
     * Deletes the stack in place when the stack rule blacklists it: an action-bar line, a DEBUG
     * line with the reason's tag, count 0. False, and nothing done, when the rule says NONE, so
     * an empty stack is never touched (ItemStack.EMPTY included).
     */
    public static boolean remove(BlacklistSnapshot snapshot, ServerPlayer player, ItemStack stack,
            String where) {
        StackRules.Reason reason = StackRules.reason(snapshot, stack);
        if (reason == StackRules.Reason.NONE) {
            return false;
        }
        // Read before setCount(0): an empty stack reads as air.
        Item item = stack.getItem();
        Component name = stack.getHoverName();
        stack.setCount(0);
        player.sendSystemMessage(Component.translatableWithFallback(ItemUseTexts.REMOVED_KEY,
                ItemUseTexts.REMOVED_FALLBACK, name).withStyle(ChatFormatting.RED), true);
        if (detailed()) {
            Log.debug(tag(reason), () -> "Removed blacklisted " + Lookups.itemName(item, "?")
                    + " (" + reason + ") from " + player.getScoreboardName() + " at " + where);
        }
        return true;
    }

    /**
     * Every slot of the inventory (main, armour, offhand, and from 1.21.5 the equipment slots
     * Inventory maps) and the cursor stack; the number of stacks deleted. getItem returns the
     * stored stack itself on every release, so emptying it in place empties the slot.
     */
    public static int purge(BlacklistSnapshot snapshot, ServerPlayer player) {
        int removed = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && remove(snapshot, player, stack, "inventory tick")) {
                removed++;
            }
        }
        if (remove(snapshot, player, player.containerMenu.getCarried(), "cursor")) {
            removed++;
        }
        return removed;
    }

    /** The action-bar line and the DEBUG line for a refused click on a blacklisted block. */
    public static void refuseBlock(ServerPlayer player, BlockState state, BlockPos pos) {
        Block block = state.getBlock();
        player.sendSystemMessage(Component.translatableWithFallback(ItemUseTexts.BLOCK_KEY,
                ItemUseTexts.BLOCK_FALLBACK, block.getName()).withStyle(ChatFormatting.RED), true);
        if (detailed()) {
            Log.debug(LogTag.ITEM, () -> "Refused " + player.getScoreboardName()
                    + " the use of blacklisted block " + blockName(block) + " at " + pos.getX()
                    + ", " + pos.getY() + ", " + pos.getZ());
        }
    }

    /** The red last tooltip line of a blacklisted stack. */
    public static Component tooltipLine() {
        return Component.translatableWithFallback(ItemUseTexts.TOOLTIP_KEY,
                ItemUseTexts.TOOLTIP_FALLBACK).withStyle(ChatFormatting.RED);
    }

    /** The log tag of a removal: the reason's, not POTION whenever a potion component exists. */
    static LogTag tag(StackRules.Reason reason) {
        return switch (reason) {
            case ENCHANTMENT -> LogTag.ENCHANTMENT;
            case POTION -> LogTag.POTION;
            default -> LogTag.ITEM;
        };
    }

    /** "Detailed Log" of the running server's config: per-use DEBUG lines only with it. */
    private static boolean detailed() {
        ServerState state = Blacklist.serverState();
        return state != null && state.config().detailedLog();
    }

    /** The block's id through Keys, "?" for an unregistered one. */
    private static String blockName(Block block) {
        return BuiltInRegistries.BLOCK.getResourceKey(block).map(key -> Keys.name(key))
                .orElse("?");
    }
}
