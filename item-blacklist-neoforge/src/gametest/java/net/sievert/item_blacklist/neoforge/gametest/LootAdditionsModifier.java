package net.sievert.item_blacklist.neoforge.gametest;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;

/**
 * A global loot modifier that appends oak planks and stone straight to a finished roll's list
 * when its conditions hold, the way another mod's modifier would: NeoForge applies it after
 * getRandomItemsRaw, so only the roll filter's RETURN hook can refuse the planks. It implements
 * the interface, not LootModifier, whose constructor changes at 26.1; one text for all 13.
 */
public final class LootAdditionsModifier implements IGlobalLootModifier {
    /** The codec NeoForge dispatches the data file's "type" to. */
    static final MapCodec<LootAdditionsModifier> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(IGlobalLootModifier.LOOT_CONDITIONS_CODEC
                            .fieldOf("conditions").forGetter(modifier -> modifier.conditions))
                    .apply(instance, LootAdditionsModifier::new));

    private final LootItemCondition[] conditions;

    private LootAdditionsModifier(LootItemCondition[] conditions) {
        this.conditions = conditions;
    }

    /** The additions, when every condition holds; the list unchanged otherwise. */
    @Override
    public ObjectArrayList<ItemStack> apply(ObjectArrayList<ItemStack> loot,
            LootContext context) {
        for (LootItemCondition condition : conditions) {
            if (!condition.test(context)) {
                return loot;
            }
        }
        loot.add(new ItemStack(Items.OAK_PLANKS));
        loot.add(new ItemStack(Items.STONE));
        return loot;
    }

    /** The registered codec, as NeoForge asks every modifier. */
    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    /**
     * NeoForge's default priority. Abstract in IGlobalLootModifier on 26.x, absent on 1.21.x,
     * so it carries no @Override and one text compiles on both lines.
     */
    public int priority() {
        return 1000;
    }
}
