package com.wildcreatures.item;

import com.wildcreatures.CreatureConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** The Dragon Sword: a big lava blade. Every hit sets the target on fire. */
public class DragonSwordItem extends SwordItem {
    public DragonSwordItem(Properties props) {
        super(DragonTier.DRAGON, 4, -2.6F, props);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        int seconds = CreatureConfig.SWORD_BURN_SECONDS.get();
        if (seconds > 0) target.setSecondsOnFire(seconds);
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wildcreatures.dragon_sword.desc").withStyle(ChatFormatting.GOLD));
    }
}
