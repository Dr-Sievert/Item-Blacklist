package net.sievert.item_blacklist.neoforge.mixin;

import net.minecraft.world.item.alchemy.PotionBrewing;
import net.neoforged.neoforge.common.brewing.BrewingRecipeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PotionBrewing.class)
public interface PotionBrewingNeoForgeAccessor {

    @SuppressWarnings("UnstableApiUsage")
    @Mutable
    @Accessor("registry")
    void item_blacklist$setRegistry(
            BrewingRecipeRegistry registry
    );
}