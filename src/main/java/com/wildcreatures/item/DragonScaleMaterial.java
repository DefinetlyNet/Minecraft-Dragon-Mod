package com.wildcreatures.item;

import com.wildcreatures.ModItems;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

/** Armor made from Red Dragon Scales: a little tougher than netherite. Numbers are guesses. */
public enum DragonScaleMaterial implements ArmorMaterial {
    INSTANCE;

    // durability multiplier 40 (netherite is 37); defense: boots, leggings, chestplate, helmet
    private static final int[] BASE_DURABILITY = {13, 15, 16, 11};
    private static final int[] DEFENSE = {4, 7, 9, 4};

    private static int index(ArmorItem.Type type) {
        return switch (type) {
            case BOOTS -> 0;
            case LEGGINGS -> 1;
            case CHESTPLATE -> 2;
            case HELMET -> 3;
        };
    }

    @Override public int getDurabilityForType(ArmorItem.Type type) { return BASE_DURABILITY[index(type)] * 40; }
    @Override public int getDefenseForType(ArmorItem.Type type) { return DEFENSE[index(type)]; }
    @Override public int getEnchantmentValue() { return 18; }
    @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_NETHERITE; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.of(ModItems.RED_DRAGON_SCALE.get()); }
    @Override public String getName() { return "wildcreatures:dragon_scale"; }
    @Override public float getToughness() { return 3.5F; }
    @Override public float getKnockbackResistance() { return 0.1F; }
}
