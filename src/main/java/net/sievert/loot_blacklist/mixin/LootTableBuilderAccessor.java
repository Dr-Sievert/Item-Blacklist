package net.sievert.loot_blacklist.mixin;

import com.google.common.collect.ImmutableList;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LootTable.Builder.class)
public interface LootTableBuilderAccessor {
    @Accessor("pools")
    ImmutableList.Builder<LootPool> getPools();

    @Accessor("pools")
    void setPools(ImmutableList.Builder<LootPool> builder);
}
