package net.sievert.item_blacklist.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import net.sievert.item_blacklist.util.ItemBlacklistLogs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.sievert.item_blacklist.util.ItemBlacklistLogTags.ITEM;

@Mixin(Inventory.class)
public abstract class InventoryMixin {

    @Shadow
    @Final
    public Player player;

    @Inject(
            method = "add(ILnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void item_blacklist$removeBlacklistedItem(
            int slot,
            ItemStack stack,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (this.player.level().isClientSide
                || this.player.isCreative()
                || !BlacklistManager.isBlacklisted(stack)) {
            return;
        }

        this.player.displayClientMessage(
                Component.empty()
                        .append(stack.getHoverName())
                        .append(" is disabled by blacklist.")
                        .withStyle(ChatFormatting.RED),
                true
        );

        ItemBlacklistLogs.debug(
                ITEM,
                "Removed blacklisted item from player inventory: player={}, item={}",
                this.player.getDisplayName().getString(),
                stack.getItem()
        );

        stack.setCount(0);
        cir.setReturnValue(true);
    }
}