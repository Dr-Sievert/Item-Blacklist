package net.sievert.item_blacklist.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.sievert.item_blacklist.blacklist.BlacklistLogReport;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Optional;

@Mixin(LootDataType.class)
public abstract class LootDataTypeMixin<T> {

    @Shadow
    public abstract ResourceKey<Registry<T>> registryKey();

    @Inject(
            method = "deserialize",
            at = @At("HEAD")
    )
    private <V> void item_blacklist$filterLootTable(
            ResourceLocation resourceLocation,
            DynamicOps<V> ops,
            V value,
            CallbackInfoReturnable<Optional<T>> cir
    ) {
        if (!this.registryKey().equals(Registries.LOOT_TABLE)
                || !(value instanceof JsonElement json)) {
            return;
        }

        item_blacklist$filterElement(
                resourceLocation,
                json
        );
    }

    @Unique
    private static void item_blacklist$filterElement(
            ResourceLocation lootTable,
            JsonElement element
    ) {
        if (element.isJsonArray()) {
            JsonArray array =
                    element.getAsJsonArray();

            for (JsonElement child : array) {
                item_blacklist$filterElement(
                        lootTable,
                        child
                );
            }

            return;
        }

        if (!element.isJsonObject()) {
            return;
        }

        JsonObject object =
                element.getAsJsonObject();

        JsonElement entriesElement =
                object.get("entries");

        if (entriesElement != null
                && entriesElement.isJsonArray()) {
            item_blacklist$filterEntries(
                    lootTable,
                    entriesElement.getAsJsonArray()
            );
        }

        for (Map.Entry<String, JsonElement> entry :
                object.entrySet()) {
            if ("entries".equals(entry.getKey())) {
                continue;
            }

            item_blacklist$filterElement(
                    lootTable,
                    entry.getValue()
            );
        }
    }

    @Unique
    private static void item_blacklist$filterEntries(
            ResourceLocation lootTable,
            JsonArray entries
    ) {
        JsonArray filtered =
                new JsonArray();

        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                filtered.add(element);
                continue;
            }

            JsonObject entry =
                    element.getAsJsonObject();

            ResourceLocation tagId =
                    item_blacklist$getEntryId(
                            entry,
                            "minecraft:tag"
                    );

            if (tagId != null
                    && BlacklistManager.isBlacklistedTag(tagId)) {
                BlacklistLogReport.recordLootTagRemoval(
                        tagId,
                        lootTable
                );

                continue;
            }

            ResourceLocation itemId =
                    item_blacklist$getEntryId(
                            entry,
                            "minecraft:item"
                    );

            if (itemId != null
                    && BlacklistManager.isBlacklisted(itemId)) {
                BlacklistLogReport.recordLootRemoval(
                        itemId,
                        lootTable
                );

                continue;
            }

            item_blacklist$filterElement(
                    lootTable,
                    entry
            );

            filtered.add(entry);
        }

        entries.asList().clear();

        if (filtered.isEmpty()) {
            JsonObject emptyEntry =
                    new JsonObject();

            emptyEntry.addProperty(
                    "type",
                    "minecraft:empty"
            );

            entries.add(emptyEntry);
            return;
        }

        for (JsonElement element : filtered) {
            entries.add(element);
        }
    }

    @Unique
    private static ResourceLocation item_blacklist$getEntryId(
            JsonObject object,
            String expectedType
    ) {
        JsonElement typeElement =
                object.get("type");

        JsonElement nameElement =
                object.get("name");

        if (typeElement == null
                || nameElement == null
                || !typeElement.isJsonPrimitive()
                || !nameElement.isJsonPrimitive()
                || !expectedType.equals(
                typeElement.getAsString()
        )) {
            return null;
        }

        return ResourceLocation.tryParse(
                nameElement.getAsString()
        );
    }
}