package net.sievert.item_blacklist.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Item.class)
public abstract class ItemMixin {

    @Inject(
            method = "appendHoverText",
            at = @At("TAIL")
    )
    private void item_blacklist$appendBlacklistTooltip(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag,
            CallbackInfo ci
    ) {
        if (!BlacklistManager.isBlacklisted(stack)) {
            return;
        }

        tooltipComponents.add(
                Component.literal("Disabled by blacklist.").withStyle(ChatFormatting.RED)
        );
    }
}