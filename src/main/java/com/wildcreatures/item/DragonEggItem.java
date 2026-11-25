package com.wildcreatures.item;

import com.wildcreatures.CreatureConfig;
import com.wildcreatures.block.DragonEggBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** A dragon egg you can carry. Its tooltip says how to hatch it, and how far along it is if it was already partly hatched. */
public class DragonEggItem extends BlockItem {
    private final DragonEggBlock.Kind kind;

    public DragonEggItem(DragonEggBlock block, Properties properties) {
        super(block, properties);
        this.kind = block.kind();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        CompoundTag data = getBlockEntityData(stack);
        if (data != null && data.getInt("Progress") > 0 && CreatureConfig.SPEC.isLoaded()) {
            double total = CreatureConfig.EGG_HATCH_MINUTES.get() * 1200.0;
            int percent = (int) Math.min(99, data.getInt("Progress") * 100 / total);
            tooltip.add(Component.translatable("tooltip.wildcreatures.dragon_egg.progress", percent).withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("tooltip.wildcreatures.dragon_egg.place").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.wildcreatures.dragon_egg." + kind.id()).withStyle(ChatFormatting.GRAY));
    }
}
