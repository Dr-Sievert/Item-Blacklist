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

import java.util.ArrayList;
import java.util.List;
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
            for (JsonElement child : element.getAsJsonArray()) {
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

        for (Map.Entry<String, JsonElement> entry :
                new ArrayList<>(object.entrySet())) {
            JsonElement child =
                    entry.getValue();

            if (("entries".equals(entry.getKey())
                    || "children".equals(entry.getKey()))
                    && child.isJsonArray()) {
                item_blacklist$filterEntries(
                        lootTable,
                        child.getAsJsonArray()
                );

                continue;
            }

            if ("functions".equals(entry.getKey())
                    && child.isJsonArray()) {
                item_blacklist$filterFunctions(
                        lootTable,
                        child.getAsJsonArray()
                );

                continue;
            }

            item_blacklist$filterElement(
                    lootTable,
                    child
            );
        }
    }

    @Unique
    private static void item_blacklist$filterEntries(
            ResourceLocation lootTable,
            JsonArray entries
    ) {
        List<JsonElement> filtered =
                new ArrayList<>();

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

            if (item_blacklist$hasOwnBlacklistedPotionFunction(entry)) {
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
    private static void item_blacklist$filterFunctions(
            ResourceLocation lootTable,
            JsonArray functions
    ) {
        List<JsonElement> filtered =
                new ArrayList<>();

        for (JsonElement element : functions) {
            if (!element.isJsonObject()) {
                filtered.add(element);
                continue;
            }

            JsonObject function =
                    element.getAsJsonObject();

            if (item_blacklist$isBlacklistedPotionFunction(function)) {
                continue;
            }

            if (item_blacklist$isFunction(
                    function,
                    "minecraft:set_enchantments"
            )) {
                item_blacklist$filterSetEnchantments(
                        function
                );

                JsonElement enchantments =
                        function.get("enchantments");

                if (enchantments != null
                        && enchantments.isJsonObject()
                        && enchantments.getAsJsonObject().isEmpty()) {
                    continue;
                }
            }

            item_blacklist$filterElement(
                    lootTable,
                    function
            );

            filtered.add(function);
        }

        functions.asList().clear();

        for (JsonElement element : filtered) {
            functions.add(element);
        }
    }

    @Unique
    private static void item_blacklist$filterSetEnchantments(
            JsonObject function
    ) {
        JsonElement enchantmentsElement =
                function.get("enchantments");

        if (enchantmentsElement == null
                || !enchantmentsElement.isJsonObject()) {
            return;
        }

        JsonObject enchantments =
                enchantmentsElement.getAsJsonObject();

        List<String> removals =
                new ArrayList<>();

        for (Map.Entry<String, JsonElement> entry :
                enchantments.entrySet()) {
            ResourceLocation enchantmentId =
                    ResourceLocation.tryParse(
                            entry.getKey()
                    );

            if (enchantmentId != null
                    && BlacklistManager.isBlacklistedEnchantment(
                    enchantmentId
            )) {
                removals.add(
                        entry.getKey()
                );
            }
        }

        for (String key : removals) {
            enchantments.remove(key);
        }
    }

    @Unique
    private static boolean item_blacklist$hasOwnBlacklistedPotionFunction(
            JsonObject entry
    ) {
        JsonElement functionsElement =
                entry.get("functions");

        if (functionsElement == null
                || !functionsElement.isJsonArray()) {
            return false;
        }

        for (JsonElement element :
                functionsElement.getAsJsonArray()) {
            if (element.isJsonObject()
                    && item_blacklist$isBlacklistedPotionFunction(
                    element.getAsJsonObject()
            )) {
                return true;
            }
        }

        return false;
    }

    @Unique
    private static boolean item_blacklist$isBlacklistedPotionFunction(
            JsonObject function
    ) {
        if (!item_blacklist$isFunction(
                function,
                "minecraft:set_potion"
        )) {
            return false;
        }

        ResourceLocation potionId =
                item_blacklist$getResourceLocation(
                        function,
                        "id"
                );

        if (potionId == null) {
            potionId =
                    item_blacklist$getResourceLocation(
                            function,
                            "potion"
                    );
        }

        return potionId != null
                && BlacklistManager.isBlacklistedPotion(
                potionId
        );
    }

    @Unique
    private static boolean item_blacklist$isFunction(
            JsonObject object,
            String expected
    ) {
        JsonElement functionElement =
                object.get("function");

        if (functionElement == null
                || !functionElement.isJsonPrimitive()) {
            return false;
        }

        return item_blacklist$matchesId(
                functionElement.getAsString(),
                expected
        );
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
                || !item_blacklist$matchesId(
                typeElement.getAsString(),
                expectedType
        )) {
            return null;
        }

        return ResourceLocation.tryParse(
                nameElement.getAsString()
        );
    }

    @Unique
    private static ResourceLocation item_blacklist$getResourceLocation(
            JsonObject object,
            String field
    ) {
        JsonElement element =
                object.get(field);

        if (element == null
                || !element.isJsonPrimitive()) {
            return null;
        }

        return ResourceLocation.tryParse(
                element.getAsString()
        );
    }

    @Unique
    private static boolean item_blacklist$matchesId(
            String actual,
            String expected
    ) {
        if (expected.equals(actual)) {
            return true;
        }

        int separator =
                expected.indexOf(':');

        return separator >= 0
                && expected.substring(separator + 1)
                .equals(actual);
    }
}
