package net.sievert.item_blacklist.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.sievert.item_blacklist.blacklist.BlacklistLogReport;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Mixin(MappedRegistry.class)
public abstract class MappedRegistryMixin<T> {

    @Shadow
    @Final
    ResourceKey<? extends Registry<T>> key;

    @ModifyVariable(
            method = "bindTags",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Map<TagKey<T>, List<Holder<T>>> item_blacklist$filterTags(
            Map<TagKey<T>, List<Holder<T>>> tags
    ) {
        if (BlacklistManager.isEmpty()) {
            return tags;
        }

        if (this.key.equals(Registries.ITEM)) {
            return item_blacklist$filterItemTags(tags);
        }

        if (this.key.equals(Registries.BLOCK)) {
            return item_blacklist$filterBlockTags(tags);
        }

        return tags;
    }

    @Unique
    private Map<TagKey<T>, List<Holder<T>>> item_blacklist$filterItemTags(
            Map<TagKey<T>, List<Holder<T>>> tags
    ) {
        return item_blacklist$filterTags(
                tags,
                true,
                holder -> {
                    if (!(holder.value() instanceof Item item)
                            || !BlacklistManager.isResolvedBlacklisted(item)) {
                        return null;
                    }

                    return BuiltInRegistries.ITEM.getKey(item);
                }
        );
    }

    @Unique
    private Map<TagKey<T>, List<Holder<T>>> item_blacklist$filterBlockTags(
            Map<TagKey<T>, List<Holder<T>>> tags
    ) {
        return item_blacklist$filterTags(
                tags,
                false,
                holder -> {
                    if (!(holder.value() instanceof Block block)) {
                        return null;
                    }

                    Item item = block.asItem();

                    if (item == Items.AIR
                            || !BlacklistManager.isResolvedBlacklisted(item)) {
                        return null;
                    }

                    return BuiltInRegistries.ITEM.getKey(item);
                }
        );
    }

    @Unique
    private Map<TagKey<T>, List<Holder<T>>> item_blacklist$filterTags(
            Map<TagKey<T>, List<Holder<T>>> tags,
            boolean clearBlacklistedTags,
            Function<Holder<T>, ResourceLocation> blacklistedItemResolver
    ) {
        Map<TagKey<T>, List<Holder<T>>> filtered =
                new LinkedHashMap<>(tags.size());

        for (Map.Entry<TagKey<T>, List<Holder<T>>> entry :
                tags.entrySet()) {
            ResourceLocation tagId =
                    entry.getKey().location();

            if (clearBlacklistedTags
                    && BlacklistManager.isBlacklistedTag(tagId)) {
                BlacklistLogReport.recordBlacklistedTag(
                        tagId
                );

                filtered.put(
                        entry.getKey(),
                        List.of()
                );

                continue;
            }

            List<Holder<T>> values =
                    new ArrayList<>(entry.getValue());

            Iterator<Holder<T>> iterator =
                    values.iterator();

            while (iterator.hasNext()) {
                Holder<T> holder =
                        iterator.next();

                ResourceLocation itemId =
                        blacklistedItemResolver.apply(holder);

                if (itemId == null) {
                    continue;
                }

                BlacklistLogReport.recordTagRemoval(
                        itemId,
                        tagId
                );

                iterator.remove();
            }

            filtered.put(
                    entry.getKey(),
                    List.copyOf(values)
            );
        }

        return filtered;
    }
}