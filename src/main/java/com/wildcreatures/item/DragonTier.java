package com.wildcreatures.item;

import com.wildcreatures.ModItems;
import com.wildcreatures.WildCreatures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.ForgeTier;
import net.minecraftforge.common.TierSortingRegistry;

import java.util.List;

/**
 * The dragon tool material: a step above diamond, just under netherite for mining, but more
 * durable than diamond and hits as hard as netherite. Repaired with Red Dragon Scales.
 */
public final class DragonTier {
    private DragonTier() {}

    public static final Tier DRAGON = TierSortingRegistry.registerTier(
            new ForgeTier(4, 1800, 8.5F, 4.0F, 18,
                    BlockTags.create(new ResourceLocation(WildCreatures.MOD_ID, "needs_dragon_tool")),
                    () -> Ingredient.of(ModItems.RED_DRAGON_SCALE.get())),
            new ResourceLocation(WildCreatures.MOD_ID, "dragon"), List.of(Tiers.DIAMOND), List.of(Tiers.NETHERITE));
}
