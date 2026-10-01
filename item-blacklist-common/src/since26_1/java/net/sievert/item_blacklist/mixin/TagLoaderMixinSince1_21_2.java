package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * From 1.21.2: each registry's pending tags pass Blacklist.onTagLoad before the registry
 * prepares them, and the item registry loads first, so the block pass of the same load reads
 * the blacklist the item pass published. The same source serves the 26.x line as a twin of the
 * same name in src/since26_1, under the one gate since1_21_2. On a reload both methods run on
 * the server thread; the world load prepares tags before any server state exists (on 26.x on
 * worker threads), and onTagLoad hands those maps back untouched.
 */
@Mixin(TagLoader.class)
public abstract class TagLoaderMixinSince1_21_2 {
    @ModifyArg(method = "loadPendingTags(Lnet/minecraft/server/packs/resources/ResourceManager;"
            + "Lnet/minecraft/core/Registry;)Ljava/util/Optional;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Registry;prepareTagReload("
                    + "Lnet/minecraft/tags/TagLoader$LoadResult;)"
                    + "Lnet/minecraft/core/Registry$PendingTags;"))
    private static <T> TagLoader.LoadResult<T> item_blacklist$filterTags(
            TagLoader.LoadResult<T> result) {
        Map<TagKey<T>, List<Holder<T>>> filtered =
                Blacklist.onTagLoad(result.key(), result.tags());
        return filtered == result.tags()
                ? result
                : new TagLoader.LoadResult<>(result.key(), filtered);
    }

    @ModifyExpressionValue(method = "loadTagsForExistingRegistries("
            + "Lnet/minecraft/server/packs/resources/ResourceManager;"
            + "Lnet/minecraft/core/RegistryAccess;)Ljava/util/List;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/core/RegistryAccess;registries()"
                    + "Ljava/util/stream/Stream;"))
    private static Stream<RegistryAccess.RegistryEntry<?>> item_blacklist$itemsFirst(
            Stream<RegistryAccess.RegistryEntry<?>> entries) {
        // A stable sort: the item registry first, the others in the game's order.
        return entries.sorted(
                Comparator.comparingInt(e -> e.key().equals(Registries.ITEM) ? 0 : 1));
    }
}
