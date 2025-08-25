package net.sievert.loot_blacklist.mixin;

import net.minecraft.loot.LootPool;
import net.minecraft.loot.entry.LootPoolEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Accessor mixin for {@link LootPool}.
 * Exposes the private 'entries' field for blacklist code.
 */
@Mixin(LootPool.class)
public interface LootPoolAccessor {
    /**
     * Accessor for the 'entries' field.
     */
    @Accessor("entries")
    List<LootPoolEntry> getEntries();
}
