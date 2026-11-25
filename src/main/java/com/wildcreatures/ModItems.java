package com.wildcreatures;

import com.wildcreatures.item.DragonEssenceItem;
import com.wildcreatures.item.DragonScaleArmorItem;
import com.wildcreatures.item.DragonShieldItem;
import com.wildcreatures.item.DragonSwordItem;
import com.wildcreatures.item.DragonTier;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.SignItem;
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

    public static final RegistryObject<Item> FROST_DRAGON_SPAWN_EGG = ITEMS.register("frost_dragon_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.FROST_DRAGON, 0xA9D6EE, 0x2F5E9E, new Item.Properties()));
    public static final RegistryObject<Item> FROST_DRAGON_SCALE = ITEMS.register("frost_dragon_scale",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> LIGHTNING_DRAGON_SPAWN_EGG = ITEMS.register("lightning_dragon_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.LIGHTNING_DRAGON, 0x1B2C6E, 0xEDBE4E, new Item.Properties()));
    public static final RegistryObject<Item> LIGHTNING_DRAGON_SCALE = ITEMS.register("lightning_dragon_scale",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> SEA_DRAGON_SPAWN_EGG = ITEMS.register("sea_dragon_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.SEA_DRAGON, 0x0E4A5C, 0x7FE0D0, new Item.Properties()));
    public static final RegistryObject<Item> SEA_DRAGON_SCALE = ITEMS.register("sea_dragon_scale",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> BURNT_STICK = ITEMS.register("burnt_stick", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> BURNT_OAK_DOOR = ITEMS.register("burnt_oak_door",
            () -> new DoubleHighBlockItem(ModBlocks.BURNT_OAK_DOOR.get(), new Item.Properties()));
    public static final RegistryObject<Item> BURNT_OAK_SIGN = ITEMS.register("burnt_oak_sign",
            () -> new SignItem(new Item.Properties().stacksTo(16), ModBlocks.BURNT_OAK_SIGN.get(), ModBlocks.BURNT_OAK_WALL_SIGN.get()));
    public static final RegistryObject<Item> BURNT_OAK_HANGING_SIGN = ITEMS.register("burnt_oak_hanging_sign",
            () -> new HangingSignItem(ModBlocks.BURNT_OAK_HANGING_SIGN.get(), ModBlocks.BURNT_OAK_WALL_HANGING_SIGN.get(), new Item.Properties().stacksTo(16)));

    public static final RegistryObject<Item> DRAGON_ESSENCE = ITEMS.register("dragon_essence",
            () -> new DragonEssenceItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant().stacksTo(16)));
    public static final RegistryObject<Item> DRAGON_SWORD = ITEMS.register("dragon_sword",
            () -> new DragonSwordItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    public static final RegistryObject<Item> DRAGON_PICKAXE = ITEMS.register("dragon_pickaxe",
            () -> new PickaxeItem(DragonTier.DRAGON, 1, -2.8F, new Item.Properties().rarity(Rarity.RARE).fireResistant()));
    public static final RegistryObject<Item> DRAGON_AXE = ITEMS.register("dragon_axe",
            () -> new AxeItem(DragonTier.DRAGON, 5.0F, -3.0F, new Item.Properties().rarity(Rarity.RARE).fireResistant()));
    public static final RegistryObject<Item> DRAGON_SHOVEL = ITEMS.register("dragon_shovel",
            () -> new ShovelItem(DragonTier.DRAGON, 1.5F, -3.0F, new Item.Properties().rarity(Rarity.RARE).fireResistant()));
    public static final RegistryObject<Item> DRAGON_SHIELD = ITEMS.register("dragon_shield",
            () -> new DragonShieldItem(new Item.Properties().durability(1200).rarity(Rarity.EPIC).fireResistant()));

    public static final RegistryObject<Item> DRAGON_SCALE_HELMET = armor("dragon_scale_helmet", ArmorItem.Type.HELMET);
    public static final RegistryObject<Item> DRAGON_SCALE_CHESTPLATE = armor("dragon_scale_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final RegistryObject<Item> DRAGON_SCALE_LEGGINGS = armor("dragon_scale_leggings", ArmorItem.Type.LEGGINGS);
    public static final RegistryObject<Item> DRAGON_SCALE_BOOTS = armor("dragon_scale_boots", ArmorItem.Type.BOOTS);

    private static RegistryObject<Item> armor(String name, ArmorItem.Type type) {
        return ITEMS.register(name, () -> new DragonScaleArmorItem(type, new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
    }
}
