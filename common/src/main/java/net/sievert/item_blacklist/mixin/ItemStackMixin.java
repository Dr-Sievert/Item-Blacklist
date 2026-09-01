package net.sievert.item_blacklist.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

import static net.sievert.item_blacklist.util.ItemBlacklistLogTags.ITEM;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

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
                ITEM,
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