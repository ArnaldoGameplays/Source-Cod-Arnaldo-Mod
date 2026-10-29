package br.com.arnaldo;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.food.FoodProperties;

public final class ArnaldoContent {
    public static final String MOD_ID = "arnaldo";
    public static final ResourceKey<EntityType<?>> ENTITY_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("arnaldo"));
    public static final ResourceKey<Item> BERRIES_KEY = ResourceKey.create(Registries.ITEM, id("bagas_douradas"));
    public static EntityType<ArnaldoEntity> ARNALDO;
    // Keep the old registry entry readable in existing inventories; it is no longer pet food or craftable.
    public static Item GOLDEN_BERRIES;
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }
    public static EntityType<ArnaldoEntity> createEntityType() {
        return EntityType.Builder.of(ArnaldoEntity::new, MobCategory.CREATURE)
            .sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(10).updateInterval(3).build(ENTITY_KEY);
    }
    public static Item createBerries() {
        return new Item(new Item.Properties().setId(BERRIES_KEY)
            .food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.6F).build()));
    }
    private ArnaldoContent() {}
}
