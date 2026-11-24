package com.wildcreatures.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A small charred shrub. Breaking it can drop burnt sticks and sometimes a burnt oak sapling. */
public class BurntBushBlock extends BushBlock {
    private static final VoxelShape SHAPE = box(2, 0, 2, 14, 13, 14);

    public BurntBushBlock(Properties props) {
        super(props);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }
}
