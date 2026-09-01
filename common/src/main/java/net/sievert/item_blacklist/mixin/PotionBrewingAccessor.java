package net.sievert.item_blacklist.mixin;

import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(PotionBrewing.class)
public interface PotionBrewingAccessor {

    @Accessor("potionMixes")
    List<?> item_blacklist$getPotionMixes();

    @Mutable
    @Accessor("potionMixes")
    void item_blacklist$setPotionMixes(
            List<?> potionMixes
    );

    @Accessor("containerMixes")
    List<?> item_blacklist$getContainerMixes();

    @Mutable
    @Accessor("containerMixes")
    void item_blacklist$setContainerMixes(
            List<?> containerMixes
    );
}