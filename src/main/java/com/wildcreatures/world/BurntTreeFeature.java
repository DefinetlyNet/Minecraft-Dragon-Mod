package com.wildcreatures.world;

import com.wildcreatures.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * A big dead burnt oak: a tall, slightly leaning trunk with root flares and bare branches
 * that reach out and up. No leaves.
 */
public class BurntTreeFeature extends Feature<NoneFeatureConfiguration> {
    public BurntTreeFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        BlockPos origin = ctx.origin();
        RandomSource r = ctx.random();
        BlockState ground = level.getBlockState(origin.below());
        if (!ground.is(BlockTags.DIRT) && !ground.is(BlockTags.BASE_STONE_OVERWORLD)) return false;

        // keep clear of dragon nests (they are built before trees grow; their floor has coarse dirt,
        // which the Ashen Wastes ground never has)
        for (BlockPos near : BlockPos.betweenClosed(origin.offset(-9, -2, -9), origin.offset(9, 0, 9))) {
            if (level.getBlockState(near).is(net.minecraft.world.level.block.Blocks.COARSE_DIRT)) return false;
        }

        int height = 9 + r.nextInt(7);                       // an oak is 4-6 tall; these are 9-15
        for (int y = 0; y <= height; y++) if (!TreeFeature.validTreePos(level, origin.above(y))) return false;

        // trunk, leaning a little near the top
        Direction lean = Direction.Plane.HORIZONTAL.getRandomDirection(r);
        List<BlockPos> trunk = new ArrayList<>();
        BlockPos.MutableBlockPos p = origin.mutable();
        for (int y = 0; y < height; y++) {
            if (y > height * 0.55 && r.nextInt(4) == 0 && TreeFeature.validTreePos(level, p.relative(lean))) {
                log(level, p, Direction.Axis.Y);              // keep the trunk joined when it shifts over
                p.move(lean);
            }
            log(level, p, Direction.Axis.Y);
            trunk.add(p.immutable());
            p.move(Direction.UP);
        }

        // root flares around the base
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (r.nextFloat() < 0.25F) continue;
            BlockPos root = origin.relative(d);
            int up = r.nextInt(3);
            for (int y = 0; y <= up; y++) log(level, root.above(y), Direction.Axis.Y);
            fillDown(level, root.below());
            if (r.nextBoolean()) {
                BlockPos far = root.relative(d);
                log(level, far, d.getAxis());
                fillDown(level, far.below());
            }
        }

        // side branches from the upper half of the trunk
        int branches = 3 + r.nextInt(3);
        Direction last = null;
        for (int i = 0; i < branches; i++) {
            int idx = (int) (height * 0.4) + r.nextInt(Math.max(1, height - 2 - (int) (height * 0.4)));
            Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(r);
            if (d == last) d = d.getClockWise();
            last = d;
            branch(level, trunk.get(idx), d, 3 + r.nextInt(4), r, true);
        }
        // the crown forks into two bare limbs
        BlockPos top = trunk.get(trunk.size() - 1);
        Direction fork = Direction.Plane.HORIZONTAL.getRandomDirection(r);
        branch(level, top, fork, 2 + r.nextInt(3), r, true);
        branch(level, top, fork.getOpposite(), 2 + r.nextInt(3), r, true);
        return true;
    }

    /** A branch that steps sideways and climbs as it goes, sometimes splitting at the end. */
    private void branch(WorldGenLevel level, BlockPos start, Direction d, int length, RandomSource r, boolean canSplit) {
        BlockPos pos = start;
        for (int i = 0; i < length; i++) {
            BlockPos next = pos.relative(d);
            if (!TreeFeature.validTreePos(level, next)) return;
            log(level, next, d.getAxis());
            pos = next;
            if (i > 0 && r.nextInt(3) != 0) {
                BlockPos up = pos.above();
                if (!TreeFeature.validTreePos(level, up)) return;
                log(level, up, Direction.Axis.Y);
                pos = up;
            }
        }
        if (canSplit && r.nextInt(3) == 0) {
            Direction side = r.nextBoolean() ? d.getClockWise() : d.getCounterClockWise();
            branch(level, pos, side, 1 + r.nextInt(2), r, false);
        }
    }

    private void fillDown(WorldGenLevel level, BlockPos pos) {
        for (int i = 0; i < 3 && TreeFeature.validTreePos(level, pos); i++) {
            log(level, pos, Direction.Axis.Y);
            pos = pos.below();
        }
    }

    private void log(WorldGenLevel level, BlockPos pos, Direction.Axis axis) {
        // upright pieces are logs; sideways pieces are bark-covered wood so no cut ends show
        var block = axis == Direction.Axis.Y ? ModBlocks.BURNT_OAK_LOG.get() : ModBlocks.BURNT_OAK_WOOD.get();
        BlockState here = level.getBlockState(pos);
        if (here.is(ModBlocks.BURNT_OAK_LOG.get()) || here.is(ModBlocks.BURNT_OAK_WOOD.get())) return;
        if (TreeFeature.validTreePos(level, pos))
            setBlock(level, pos, block.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis));
    }
}
