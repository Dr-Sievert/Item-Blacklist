package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.filters.LoaderFilterRules;
import net.sievert.item_blacklist.report.Recorder;
import it.unimi.dsi.fastutil.objects.Object2IntSortedMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.FuelValues;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * From 1.21.2: blacklisted items leave every fuel table as it is built: the server's at its
 * construction and at every reload, a client's at connect and at every tag update, and
 * NeoForge's build from its furnace_fuels data map. build() hands this map to the new
 * FuelValues without copying it, so HEAD is the last point where a removal reaches the table,
 * the recipe book's fuel list included. The hook reads the effective snapshot, since a client
 * builds its own table, and records nothing, since a client must not write into a server's
 * report. It judges the item only; NeoForge's burn-time listener also judges the stack. The
 * same source serves the 26.x line as a twin of the same name in src/since26_1, under the one
 * gate since1_21_2.
 */
@Mixin(FuelValues.Builder.class)
public abstract class FuelValuesBuilderMixinSince1_21_2 {
    @Shadow
    @Final
    private Object2IntSortedMap<Item> values;

    @Inject(method = "build()Lnet/minecraft/world/level/block/entity/FuelValues;",
            at = @At("HEAD"))
    private void item_blacklist$stripBlacklisted(CallbackInfoReturnable<FuelValues> cir) {
        BlacklistSnapshot snapshot = Blacklist.effective();
        if (snapshot.isEmpty()) {
            return;
        }
        LoaderFilterRules.stripItems(this.values, snapshot, LoaderFilterRules.FUEL,
                Recorder.NONE);
    }
}
