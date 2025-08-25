package net.sievert.loot_blacklist.mixin;

import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Accessor mixin for {@link LootTable}.
 * Exposes the private 'pools' field so blacklist code
 * can read and replace loot pools.
 */
@Mixin(LootTable.class)
public interface LootTableAccessor {
    /**
     * Accessor for the 'pools' field.
     */
    @Accessor("pools")
    List<LootPool> getPools();

    /**
     * Mutator for the 'pools' field.
     */
    @Mutable
    @Accessor("pools")
    void setPools(List<LootPool> pools);
}
