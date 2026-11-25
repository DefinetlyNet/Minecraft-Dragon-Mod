package com.wildcreatures.world;

import com.wildcreatures.CreatureConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Builds a nest: a rough bowl about 15 blocks across with a woven rim, a lined floor with old bones lying about,
 * branches poking out of the rim, the loot chest in the middle and sometimes an egg next to it.
 * What it's made of depends on its {@link NestStyle}.
 */
public class DragonNestPiece extends StructurePiece {
    private static final int R = 7;
    private final int cx, cy, cz;
    private final long seed;
    private final NestStyle style;

    public DragonNestPiece(BlockPos center, long seed, NestStyle style) {
        this(center.getX(), center.getY(), center.getZ(), seed, style);
    }

    public DragonNestPiece(CompoundTag tag) {
        this(tag.getInt("CX"), tag.getInt("CY"), tag.getInt("CZ"), tag.getLong("Seed"), NestStyle.byName(tag.getString("Style")));
    }

    private DragonNestPiece(int cx, int cy, int cz, long seed, NestStyle style) {
        super(ModWorldgen.DRAGON_NEST_PIECE.get(), 0, new BoundingBox(cx - R - 2, cy - 10, cz - R - 2, cx + R + 2, cy + 9, cz + R + 2));
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.seed = seed;
        this.style = style;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putInt("CX", cx);
        tag.putInt("CY", cy);
        tag.putInt("CZ", cz);
        tag.putLong("Seed", seed);
        tag.putString("Style", style.getSerializedName());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource ignored,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        // the same random numbers for every chunk the nest touches, so the halves line up
        RandomSource r = RandomSource.create(seed);
        double[] wobble = new double[16];
        for (int i = 0; i < wobble.length; i++) wobble[i] = r.nextDouble() * 0.9;

        for (int dx = -R - 1; dx <= R + 1; dx++) {
            for (int dz = -R - 1; dz <= R + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int angleIdx = (int) ((Math.atan2(dz, dx) + Math.PI) / (2 * Math.PI) * 16) & 15;
                double rim = R - 0.5 + wobble[angleIdx] - 0.45;
                int x = cx + dx, z = cz + dz;
                long h = (x * 341873128712L + z * 132897987541L) ^ seed;
                RandomSource cell = RandomSource.create(h);
                if (d > rim + 1.2) continue;

                // clear the space above (trees, bushes, the hillside)
                for (int y = cy; y <= cy + style.headroom(); y++) set(level, box, x, y, z, Blocks.AIR.defaultBlockState());
                // solid ground under the whole nest
                for (int y = cy - 1; y >= cy - style.footingDepth(); y--) {
                    BlockState below = level.getBlockState(new BlockPos(x, y, z));
                    if (below.isSolid() && y < cy - 1) break;
                    set(level, box, x, y, z, y == cy - 1 ? style.lining(cell) : style.footing());
                }

                if (d > rim - 1.6 && d <= rim + 1.2) {        // the woven rim, highest in the middle of its width
                    int height = d > rim + 0.4 ? 1 + cell.nextInt(2) : 2 + cell.nextInt(2);
                    for (int y = 0; y < height; y++) {
                        Direction.Axis axis = cell.nextBoolean() ? (Math.abs(dx) > Math.abs(dz) ? Direction.Axis.Z : Direction.Axis.X) : Direction.Axis.Y;
                        set(level, box, x, cy + y, z, style.rim(cell, axis));
                    }
                } else if (d <= rim - 1.6 && d > 1.5) {          // floor of the bowl
                    BlockState clutter = style.floorClutter(cell);
                    if (clutter != null) set(level, box, x, cy, z, clutter);
                }
            }
        }
        // a few branches sticking out over the rim
        for (int i = 0; i < 7; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            Direction out = Direction.fromYRot(Math.toDegrees(a) - 90);
            int bx = cx + (int) Math.round(Math.cos(a) * (R + 0.3)), bz = cz + (int) Math.round(Math.sin(a) * (R + 0.3));
            int by = cy + 2;
            for (int k = 0; k < 2 + r.nextInt(2); k++) {
                set(level, box, bx, by, bz, style.branch(out.getAxis()));
                bx += out.getStepX();
                bz += out.getStepZ();
            }
        }
        // a lightning rod standing on the Lightning Dragon's rim
        if (style == NestStyle.LIGHTNING) {
            double a = r.nextDouble() * Math.PI * 2;
            int rx = cx + (int) Math.round(Math.cos(a) * (R - 1)), rz = cz + (int) Math.round(Math.sin(a) * (R - 1));
            if (box.isInside(new BlockPos(rx, cy, rz))) {
                for (int y = cy + 4; y >= cy; y--) {
                    if (!level.getBlockState(new BlockPos(rx, y, rz)).isAir()) {
                        set(level, box, rx, y + 1, rz, Blocks.LIGHTNING_ROD.defaultBlockState());
                        break;
                    }
                }
            }
        }
        // sometimes an egg, lying next to the chest (decided before the chest, which uses up random numbers only in its own chunk)
        boolean egg = r.nextInt(100) < CreatureConfig.NEST_EGG_CHANCE.get();
        int[][] spots = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 1}, {-1, 2}, {-2, -1}, {1, -2}};
        int[] spot = spots[r.nextInt(spots.length)];
        if (egg) set(level, box, cx + spot[0], cy, cz + spot[1], style.egg().defaultBlockState());
        // the treasure
        BlockPos chest = new BlockPos(cx, cy, cz);
        if (box.isInside(chest)) {
            createChest(level, box, r, chest, style.lootTable(),
                    Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(r)));
        }
    }

    private static void set(WorldGenLevel level, BoundingBox box, int x, int y, int z, BlockState state) {
        BlockPos pos = new BlockPos(x, y, z);
        if (box.isInside(pos)) level.setBlock(pos, state, 2);
    }
}
