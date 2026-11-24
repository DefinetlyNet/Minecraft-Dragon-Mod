package com.wildcreatures.block;

import com.wildcreatures.WildCreatures;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.grower.AbstractTreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/** A burnt oak sapling grows into the same big dead tree that generates in the Ashen Wastes. */
public class BurntOakGrower extends AbstractTreeGrower {
    private static final ResourceKey<ConfiguredFeature<?, ?>> TREE =
            ResourceKey.create(Registries.CONFIGURED_FEATURE, new ResourceLocation(WildCreatures.MOD_ID, "burnt_tree"));

    @Override
    protected ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource random, boolean hasFlowers) {
        return TREE;
    }
}
