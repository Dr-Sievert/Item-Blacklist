package net.sievert.loot_blacklist.mixin;

import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.entry.LootPoolEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(LootPoolEntry.class)
public interface LootPoolEntryAccessor {
    @Accessor("conditions")
    List<LootCondition> getConditions();
}
