package net.sievert.item_blacklist.mixin;

import java.util.List;
import net.minecraft.world.item.alchemy.PotionBrewing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads and replaces the two mix lists of a PotionBrewing, which the game keeps private and
 * final and reads live on every brewing check. Both fields have one name and type on every
 * release and loader, so the accessor sits in src/main. BrewingFilter is the only caller
 * outside the brewing scenarios. The lists are handled as {@code List<?>}: their element type,
 * PotionBrewing.Mix, is not public.
 */
@Mixin(PotionBrewing.class)
public interface PotionBrewingAccessor {
    /** The potion mixes (potion to potion). */
    @Accessor("potionMixes")
    List<?> item_blacklist$potionMixes();

    /** Replaces the potion mixes; @Mutable, since the field stays final. */
    @Mutable
    @Accessor("potionMixes")
    void item_blacklist$setPotionMixes(List<?> mixes);

    /** The container mixes (bottle item to bottle item). */
    @Accessor("containerMixes")
    List<?> item_blacklist$containerMixes();

    /** Replaces the container mixes; @Mutable, since the field stays final. */
    @Mutable
    @Accessor("containerMixes")
    void item_blacklist$setContainerMixes(List<?> mixes);
}
