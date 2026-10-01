package net.sievert.item_blacklist.fabric.blacklist;

import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.ComposterBlock;
import net.sievert.item_blacklist.blacklist.BlacklistManager;

import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Map;

public final class FabricCompostingFilter {

    private static final Map<ItemLike, Float> REMOVED_COMPOSTABLES =
            new LinkedHashMap<>();

    private FabricCompostingFilter() {}

    public static void filter() {
        restoreRemoved();

        Iterator<Object2FloatMap.Entry<ItemLike>> iterator =
                ComposterBlock.COMPOSTABLES
                        .object2FloatEntrySet()
                        .iterator();

        while (iterator.hasNext()) {
            Object2FloatMap.Entry<ItemLike> entry =
                    iterator.next();

            ItemLike itemLike =
                    entry.getKey();

            if (!BlacklistManager.isBlacklisted(
                    itemLike.asItem()
            )) {
                continue;
            }

            REMOVED_COMPOSTABLES.put(
                    itemLike,
                    entry.getFloatValue()
            );

            iterator.remove();
        }
    }

    private static void restoreRemoved() {
        ComposterBlock.COMPOSTABLES.putAll(REMOVED_COMPOSTABLES);

        REMOVED_COMPOSTABLES.clear();
    }
}