package net.sievert.item_blacklist.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import net.sievert.item_blacklist.util.ItemBlacklistLogs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.sievert.item_blacklist.util.ItemBlacklistLogTags.ITEM;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {

    @Shadow
    public abstract Block getBlock();

    @Inject(
            method = "useWithoutItem",
            at = @At("HEAD"),
            cancellable = true
    )
    private void item_blacklist$preventUseWithoutItem(
            Level level,
            Player player,
            BlockHitResult hitResult,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (!player.isCreative() && item_blacklist$isBlacklistedBlock(level, hitResult)) {
            if (!level.isClientSide) {
                ItemBlacklistLogs.debug(
                        ITEM,
                        "Blocked interaction with blacklisted block: player={}, block={}, pos={}",
                        player.getDisplayName().getString(),
                        BuiltInRegistries.BLOCK.getKey(this.getBlock()),
                        hitResult.getBlockPos()
                );
            }

            player.displayClientMessage(
                    Component.literal("Disabled by blacklist.").withStyle(ChatFormatting.RED),
                    true
            );

            cir.setReturnValue(InteractionResult.CONSUME);
        }
    }

    @Inject(
            method = "useItemOn",
            at = @At("HEAD"),
            cancellable = true
    )
    private void item_blacklist$preventUseWithItem(
            ItemStack stack,
            Level level,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<ItemInteractionResult> cir
    ) {
        if (!player.isCreative() && item_blacklist$isBlacklistedBlock(level, hitResult)) {
            if (!level.isClientSide) {
                ItemBlacklistLogs.debug(
                        ITEM,
                        "Blocked interaction with blacklisted block: player={}, block={}, pos={}",
                        player.getDisplayName().getString(),
                        BuiltInRegistries.BLOCK.getKey(this.getBlock()),
                        hitResult.getBlockPos()
                );
            }

            player.displayClientMessage(
                    Component.empty()
                            .append(this.getBlock().getName())
                            .append(" is disabled by blacklist.")
                            .withStyle(ChatFormatting.RED),
                    true
            );

            cir.setReturnValue(ItemInteractionResult.CONSUME);
        }
    }

    @Unique
    private boolean item_blacklist$isBlacklistedBlock(
            Level level,
            BlockHitResult hitResult
    ) {
        Block block = this.getBlock();

        if (BlacklistManager.isBlacklisted(block.asItem())) {
            return true;
        }

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = level.getBlockState(pos);

        ItemStack cloneStack = block.getCloneItemStack(
                level,
                pos,
                state
        );

        return !cloneStack.isEmpty() && BlacklistManager.isBlacklisted(cloneStack);
    }
}