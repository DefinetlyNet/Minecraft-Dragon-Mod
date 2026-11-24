package com.wildcreatures.client;

import com.wildcreatures.WildCreatures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Gives the Dragon Scale helmet its horns. */
public final class DragonArmorModels {
    private DragonArmorModels() {}

    public static final ModelLayerLocation HELMET = new ModelLayerLocation(new ResourceLocation(WildCreatures.MOD_ID, "dragon_scale_armor"), "helmet");

    private static HumanoidModel<LivingEntity> helmet;

    public static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
            // Only the helmet needs its own model (for the horns); the rest use vanilla's armor shape.
            if (slot != EquipmentSlot.HEAD) return original;
            if (helmet == null) helmet = new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(HELMET));
            return helmet;
        }
    };
}
