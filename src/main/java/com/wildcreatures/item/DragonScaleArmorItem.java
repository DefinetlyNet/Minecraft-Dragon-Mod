package com.wildcreatures.item;

import com.wildcreatures.WildCreatures;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/** Dragon Scale armor. Worn, it uses the detailed 3D models in client/DragonArmorLayers (horned helmet etc.). */
public class DragonScaleArmorItem extends ArmorItem {
    public DragonScaleArmorItem(Type type, Properties props) {
        super(DragonScaleMaterial.INSTANCE, type, props);
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        int layer = slot == EquipmentSlot.LEGS ? 2 : 1;
        return WildCreatures.MOD_ID + ":textures/models/armor/dragon_scale_layer_" + layer + ".png";
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(com.wildcreatures.client.DragonArmorModels.EXTENSIONS);
    }
}
