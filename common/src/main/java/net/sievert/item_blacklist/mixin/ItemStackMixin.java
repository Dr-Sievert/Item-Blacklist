package net.sievert.item_blacklist.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import net.sievert.item_blacklist.util.ItemBlacklistLogs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

import static net.sievert.item_blacklist.util.ItemBlacklistLogTags.ITEM;
import static net.sievert.item_blacklist.util.ItemBlacklistLogTags.POTION;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(
            method = "getTooltipLines",
            at = @At("RETURN"),
            cancellable = true
    )
    private void item_blacklist$appendBlacklistTooltip(
            Item.TooltipContext tooltipContext,
            Player player,
            TooltipFlag tooltipFlag,
            CallbackInfoReturnable<List<Component>> cir
    ) {
        ItemStack stack =
                (ItemStack) (Object) this;

        if (stack.has(DataComponents.HIDE_TOOLTIP)
                || !BlacklistManager.isBlacklisted(stack)) {
            return;
        }

        List<Component> tooltip =
                new ArrayList<>(cir.getReturnValue());

        tooltip.add(
                Component.literal("Disabled by blacklist.")
                        .withStyle(ChatFormatting.RED)
        );

        cir.setReturnValue(tooltip);
    }

    @Inject(
            method = "inventoryTick",
            at = @At("HEAD"),
            cancellable = true
    )
    private void item_blacklist$removeBlacklistedItem(
            Level level,
            Entity entity,
            int inventorySlot,
            boolean isCurrentItem,
            CallbackInfo ci
    ) {
        if (level.isClientSide()
                || !(entity instanceof Player player)
                || player.isCreative()) {
            return;
        }

        ItemStack stack =
                (ItemStack) (Object) this;

        if (!BlacklistManager.isBlacklisted(stack)) {
            return;
        }

        player.displayClientMessage(
                Component.empty()
                        .append(stack.getHoverName())
                        .append(" is disabled by blacklist.")
                        .withStyle(ChatFormatting.RED),
                true
        );

        ItemBlacklistLogs.debug(
                stack.has(DataComponents.POTION_CONTENTS)
                        ? POTION
                        : ITEM,
                "Removed blacklisted item during inventory tick: player={}, item={}",
                player.getDisplayName().getString(),
                stack.getItem()
        );

        stack.setCount(0);
        ci.cancel();
    }

    @Inject(
            method = "useOn",
            at = @At("HEAD"),
            cancellable = true
    )
    private void item_blacklist$preventUseOnBlacklistedBlock(
            UseOnContext context,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        Player player =
                context.getPlayer();

        if (player == null || player.isCreative()) {
            return;
        }

        Level level =
                context.getLevel();

        BlockPos pos =
                context.getClickedPos();

        BlockState state =
                level.getBlockState(pos);

        if (!BlacklistManager.isBlacklistedBlock(
                level,
                pos,
                state
        )) {
            return;
        }

        Block block =
                state.getBlock();

        if (!level.isClientSide()) {
            ItemBlacklistLogs.debug(
                    ITEM,
                    "Blocked interaction with blacklisted block: player={}, block={}, pos={}",
                    player.getDisplayName().getString(),
                    BuiltInRegistries.BLOCK.getKey(block),
                    pos
            );
        }

        player.displayClientMessage(
                Component.empty()
                        .append(block.getName())
                        .append(" is disabled by blacklist.")
                        .withStyle(ChatFormatting.RED),
                true
        );

        cir.setReturnValue(
                InteractionResult.CONSUME
        );
    }
}