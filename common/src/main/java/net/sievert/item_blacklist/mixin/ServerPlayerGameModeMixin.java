package net.sievert.item_blacklist.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import net.sievert.item_blacklist.util.ItemBlacklistLogs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.sievert.item_blacklist.util.ItemBlacklistLogTags.ITEM;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    @Inject(
            method = "useItemOn",
            at = @At("HEAD"),
            cancellable = true
    )
    private void item_blacklist$preventUseOnBlacklistedBlock(
            ServerPlayer player,
            Level level,
            ItemStack stack,
            InteractionHand hand,
            BlockHitResult hitResult,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (player.isCreative()) {
            return;
        }

        BlockPos pos =
                hitResult.getBlockPos();

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

        ItemBlacklistLogs.debug(
                ITEM,
                "Blocked interaction with blacklisted block: player={}, block={}, pos={}",
                player.getDisplayName().getString(),
                BuiltInRegistries.BLOCK.getKey(block),
                pos
        );

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