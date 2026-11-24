package com.wildcreatures.item;

import com.wildcreatures.CreatureConfig;
import com.wildcreatures.WildCreatures;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import javax.annotation.Nullable;
import java.util.List;
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

    /** How many Dragon Scale pieces this creature has on. */
    public static int piecesWorn(LivingEntity entity) {
        int pieces = 0;
        for (ItemStack stack : entity.getArmorSlots()) {
            if (stack.getItem() instanceof DragonScaleArmorItem) pieces++;
        }
        return pieces;
    }

    /** True when the creature wears enough Dragon Scale armor (the full set by default) to be immune to fire and lava. */
    public static boolean givesFireImmunity(LivingEntity entity) {
        int needed = CreatureConfig.ARMOR_FIRE_PIECES.get();
        return needed > 0 && piecesWorn(entity) >= needed;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wildcreatures.dragon_scale_armor.desc").withStyle(ChatFormatting.GOLD));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(com.wildcreatures.client.DragonArmorModels.EXTENSIONS);
    }
}
