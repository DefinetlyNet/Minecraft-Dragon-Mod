package com.wildcreatures.world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.HashSet;
import java.util.Set;

/** A small lava pool sunk into flat ground, ringed with magma and blackstone. */
public class LavaPoolFeature extends Feature<NoneFeatureConfiguration> {
    public LavaPoolFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource r = ctx.random();
        BlockPos surface = ctx.origin().below();               // origin is the air just above the ground
        int y = surface.getY();
        double rx = 1.6 + r.nextDouble() * 1.8, rz = 1.6 + r.nextDouble() * 1.8;

        Set<BlockPos> pool = new HashSet<>();
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++) {
                double v = (dx * dx) / (rx * rx) + (dz * dz) / (rz * rz) + (r.nextDouble() - 0.5) * 0.25;
                if (v <= 1) pool.add(surface.offset(dx, 0, dz));
            }
        if (pool.size() < 5) return false;

        // only on flat, solid ground so the lava stays in the pool
        Set<BlockPos> rim = new HashSet<>();
        for (BlockPos c : pool)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos n = c.offset(dx, 0, dz);
                    if (!pool.contains(n)) rim.add(n);
                }
        for (BlockPos c : pool) if (!solid(level, c) || !solid(level, c.below())) return false;
        for (BlockPos c : rim) if (!solid(level, c) || !solid(level, c.below())) return false;

        for (BlockPos c : pool) {
            boolean deep = r.nextInt(3) != 0;
            setBlock(level, c, Blocks.LAVA.defaultBlockState());
            if (deep) {
                setBlock(level, c.below(), Blocks.LAVA.defaultBlockState());
                if (!solid(level, c.below(2))) setBlock(level, c.below(2), Blocks.MAGMA_BLOCK.defaultBlockState());
            }
            for (int up = 1; up <= 3; up++) {
                BlockState above = level.getBlockState(c.above(up));
                if (!above.isAir() && above.canBeReplaced()) setBlock(level, c.above(up), Blocks.AIR.defaultBlockState());
            }
        }
        for (BlockPos c : rim) {
            int roll = r.nextInt(10);
            if (roll < 4) setBlock(level, c, Blocks.MAGMA_BLOCK.defaultBlockState());
            else if (roll < 6) setBlock(level, c, Blocks.BLACKSTONE.defaultBlockState());
        }
        return true;
    }

    private static boolean solid(WorldGenLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.isSolid() && s.getFluidState().isEmpty() && !s.is(net.minecraft.tags.BlockTags.LOGS);
    }
}
