package net.sievert.item_blacklist.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import net.sievert.item_blacklist.util.ItemBlacklistLogs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.sievert.item_blacklist.util.ItemBlacklistLogTags.ITEM;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(
            method = "inventoryTick",
            at = @At("HEAD")
    )
    private void item_blacklist$removeBlacklistedItem(
            Level level,
            Entity entity,
            int inventorySlot,
            boolean isCurrentItem,
            CallbackInfo ci
    ) {
        if (level.isClientSide() || !(entity instanceof Player player) || player.isCreative()) {
            return;
        }

        ItemStack stack = (ItemStack) (Object) this;

        if (!BlacklistManager.isBlacklisted(stack)) {
            return;
        }

        player.displayClientMessage(
                Component.literal("Disabled by blacklist.").withStyle(ChatFormatting.RED),
                true
        );

        ItemBlacklistLogs.debug(
                ITEM,
                "Removed blacklisted item during inventory tick: player={}, item={}",
                player.getDisplayName().getString(),
                stack.getItem()
        );

        stack.setCount(0);
    }
}