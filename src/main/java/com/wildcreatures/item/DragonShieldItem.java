package com.wildcreatures.item;

import com.wildcreatures.ModItems;
import com.wildcreatures.client.DragonShieldRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Dragon Shield. Blocks like a normal shield but lasts much longer, and any creature that hits
 * it while you block catches fire (see GearEvents). Drawn by client/DragonShieldRenderer.
 */
public class DragonShieldItem extends ShieldItem {
    public DragonShieldItem(Properties props) {
        super(props);
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return getDescriptionId();                          // vanilla shields rename themselves after banner colours
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
        return repair.is(ModItems.RED_DRAGON_SCALE.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.wildcreatures.dragon_shield.desc").withStyle(ChatFormatting.GOLD));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return DragonShieldRenderer.get();
            }
        });
    }
}
