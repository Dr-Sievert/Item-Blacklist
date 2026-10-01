package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 1.21.1: the tag map each registry binds passes Blacklist.onTagLoad, and the item tags bind
 * first, so the block pass of the same load reads the blacklist the item pass published. From
 * 1.21.2 tags load through TagLoader instead (TagLoaderMixinSince1_21_2). Both methods run on
 * the server thread inside reloadResources; at the world load no server state exists yet and
 * onTagLoad hands the map back untouched.
 */
@Mixin(ReloadableServerResources.class)
public abstract class TagBindMixinUntil1_21_2 {
    @ModifyArg(method = "updateRegistryTags(Lnet/minecraft/core/RegistryAccess;"
            + "Lnet/minecraft/tags/TagManager$LoadResult;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/core/Registry;bindTags(Ljava/util/Map;)V"))
    private static <T> Map<TagKey<T>, List<Holder<T>>> item_blacklist$filterTags(
            Map<TagKey<T>, List<Holder<T>>> tags,
            @Local(argsOnly = true) TagManager.LoadResult<T> result) {
        // The registry comes from the load result: Registry.key() breaks Fabric at 1.21.2.
        return Blacklist.onTagLoad(result.key(), tags);
    }

    @ModifyExpressionValue(method = "updateRegistryTags()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/tags/TagManager;getResult()Ljava/util/List;"))
    private List<TagManager.LoadResult<?>> item_blacklist$itemsFirst(
            List<TagManager.LoadResult<?>> results) {
        List<TagManager.LoadResult<?>> sorted = new ArrayList<>(results);
        // A stable sort: the item registry first, the others in the game's order.
        sorted.sort(Comparator.comparingInt(r -> r.key().equals(Registries.ITEM) ? 0 : 1));
        return sorted;
    }
}
