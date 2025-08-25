package net.sievert.item_blacklist.mixin;

import net.minecraft.loot.entry.CombinedEntry;
import net.minecraft.loot.entry.LootPoolEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Accessor mixin for {@link CombinedEntry}.
 * Exposes the private 'children' field for blacklist code.
 */
@Mixin(CombinedEntry.class)
public interface CombinedEntryAccessor {
    /**
     * Accessor for the 'children' field.
     */
    @Accessor("children")
    List<LootPoolEntry> getChildren();
}
