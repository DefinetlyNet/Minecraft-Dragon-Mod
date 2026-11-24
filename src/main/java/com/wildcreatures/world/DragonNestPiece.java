package com.wildcreatures.world;

import com.wildcreatures.ModBlocks;
import com.wildcreatures.WildCreatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Builds the nest: a rough bowl of burnt oak wood about 15 blocks across, lined with ash,
 * coarse dirt and old bones, with branches poking out of the rim and the loot chest in the middle.
 */
public class DragonNestPiece extends StructurePiece {
    public static final ResourceLocation LOOT = new ResourceLocation(WildCreatures.MOD_ID, "chests/dragon_nest");
    private static final int R = 7;
    private final int cx, cy, cz;
    private final long seed;

    public DragonNestPiece(BlockPos center, long seed) {
        this(center.getX(), center.getY(), center.getZ(), seed);
    }

    public DragonNestPiece(CompoundTag tag) {
        this(tag.getInt("CX"), tag.getInt("CY"), tag.getInt("CZ"), tag.getLong("Seed"));
    }

    private DragonNestPiece(int cx, int cy, int cz, long seed) {
        super(ModWorldgen.DRAGON_NEST_PIECE.get(), 0, new BoundingBox(cx - R - 2, cy - 5, cz - R - 2, cx + R + 2, cy + 6, cz + R + 2));
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.seed = seed;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putInt("CX", cx);
        tag.putInt("CY", cy);
        tag.putInt("CZ", cz);
        tag.putLong("Seed", seed);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource ignored,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        // the same random numbers for every chunk the nest touches, so the halves line up
        RandomSource r = RandomSource.create(seed);
        BlockState wood = ModBlocks.BURNT_OAK_WOOD.get().defaultBlockState();
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

                // clear the space above (trees, bushes)
                for (int y = cy; y <= cy + 5; y++) set(level, box, x, y, z, Blocks.AIR.defaultBlockState());
                // solid ground under the whole nest
                for (int y = cy - 1; y >= cy - 5; y--) {
                    BlockState below = level.getBlockState(new BlockPos(x, y, z));
                    if (below.isSolid() && y < cy - 1) break;
                    set(level, box, x, y, z, y == cy - 1 ? lining(cell) : Blocks.BLACKSTONE.defaultBlockState());
                }

                if (d > rim - 1.6 && d <= rim + 1.2) {        // the woven rim, highest in the middle of its width
                    int height = d > rim + 0.4 ? 1 + cell.nextInt(2) : 2 + cell.nextInt(2);
                    for (int y = 0; y < height; y++) {
                        Direction.Axis axis = cell.nextBoolean() ? (Math.abs(dx) > Math.abs(dz) ? Direction.Axis.Z : Direction.Axis.X) : Direction.Axis.Y;
                        set(level, box, x, cy + y, z, wood.setValue(RotatedPillarBlock.AXIS, axis));
                    }
                } else if (d <= rim - 1.6 && d > 1.5) {          // floor of the bowl
                    int roll = cell.nextInt(14);
                    if (roll == 0) set(level, box, x, cy, z, Blocks.BONE_BLOCK.defaultBlockState());
                    else if (roll == 1) set(level, box, x, cy, z, ModBlocks.BURNT_BUSH.get().defaultBlockState());
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
                set(level, box, bx, by, bz, wood.setValue(RotatedPillarBlock.AXIS, out.getAxis()));
                bx += out.getStepX();
                bz += out.getStepZ();
            }
        }
        // the treasure
        BlockPos chest = new BlockPos(cx, cy, cz);
        if (box.isInside(chest)) {
            createChest(level, box, r, chest, LOOT,
                    Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(r)));
        }
    }

    private static BlockState lining(RandomSource r) {
        int roll = r.nextInt(10);
        if (roll < 5) return ModBlocks.ASHEN_SOIL.get().defaultBlockState();
        if (roll < 8) return Blocks.COARSE_DIRT.defaultBlockState();
        return Blocks.BASALT.defaultBlockState();
    }

    private static void set(WorldGenLevel level, BoundingBox box, int x, int y, int z, BlockState state) {
        BlockPos pos = new BlockPos(x, y, z);
        if (box.isInside(pos)) level.setBlock(pos, state, 2);
    }
}
