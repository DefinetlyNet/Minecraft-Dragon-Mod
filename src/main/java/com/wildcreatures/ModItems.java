package com.wildcreatures;

import com.wildcreatures.item.DragonScaleArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    private ModItems() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, WildCreatures.MOD_ID);

    public static final RegistryObject<Item> RED_DRAGON_SPAWN_EGG = ITEMS.register("red_dragon_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.RED_DRAGON, 0x9E1B12, 0xF2B33D, new Item.Properties()));

    public static final RegistryObject<Item> RED_DRAGON_SCALE = ITEMS.register("red_dragon_scale",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON).fireResistant()));

    public static final RegistryObject<Item> DRAGON_SCALE_HELMET = armor("dragon_scale_helmet", ArmorItem.Type.HELMET);
    public static final RegistryObject<Item> DRAGON_SCALE_CHESTPLATE = armor("dragon_scale_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final RegistryObject<Item> DRAGON_SCALE_LEGGINGS = armor("dragon_scale_leggings", ArmorItem.Type.LEGGINGS);
    public static final RegistryObject<Item> DRAGON_SCALE_BOOTS = armor("dragon_scale_boots", ArmorItem.Type.BOOTS);

    private static RegistryObject<Item> armor(String name, ArmorItem.Type type) {
        return ITEMS.register(name, () -> new DragonScaleArmorItem(type, new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    }
}
