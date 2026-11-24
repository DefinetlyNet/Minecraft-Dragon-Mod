package com.wildcreatures.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Dragon Essence: a very rare drop from Red Dragons and dragon nest chests. Shimmers like an enchanted item. */
public class DragonEssenceItem extends Item {
    public DragonEssenceItem(Properties props) {
        super(props);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wildcreatures.dragon_essence.desc").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }
}
