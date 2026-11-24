package com.wildcreatures.world;

import com.wildcreatures.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Builds the volcano column by column: a cone with a wavy outline, a lava-filled crater at the
 * top, blackstone and basalt slopes, glowing magma streaks running down the sides, and ashen
 * soil blending into the ground at the foot. All shapes come from the stored seed, so every
 * chunk of a volcano agrees with its neighbours.
 */
public class VolcanoPiece extends StructurePiece {
    private final int cx, cz, baseY, radius, height;
    private final long seed;
    // derived from the seed
    private final double[] outlinePhase = new double[4];
    private final double[] streaks;
    private final double p1, p2, p3, p4;

    public VolcanoPiece(BlockPos center, long seed, RandomSource r) {
        this(center.getX(), center.getY(), center.getZ(), 46 + r.nextInt(12), 50 + r.nextInt(14), seed);
    }

    public VolcanoPiece(CompoundTag tag) {
        this(tag.getInt("CX"), tag.getInt("BaseY"), tag.getInt("CZ"), tag.getInt("Radius"), tag.getInt("Height"), tag.getLong("Seed"));
    }

    private VolcanoPiece(int cx, int baseY, int cz, int radius, int height, long seed) {
        super(ModWorldgen.VOLCANO_PIECE.get(), 0, box(cx, baseY, cz, radius, height));
        this.cx = cx;
        this.cz = cz;
        this.baseY = baseY;
        this.radius = radius;
        this.height = height;
        this.seed = seed;
        RandomSource r = RandomSource.create(seed);
        for (int i = 0; i < outlinePhase.length; i++) outlinePhase[i] = r.nextDouble() * Math.PI * 2;
        streaks = new double[6 + r.nextInt(4)];
        for (int i = 0; i < streaks.length; i++) streaks[i] = r.nextDouble() * Math.PI * 2;
        p1 = r.nextDouble() * 100; p2 = r.nextDouble() * 100; p3 = r.nextDouble() * 100; p4 = r.nextDouble() * 100;
    }

    private static BoundingBox box(int cx, int baseY, int cz, int radius, int height) {
        int reach = (int) Math.ceil(radius * 1.15) + 1;
        return new BoundingBox(cx - reach, baseY - 20, cz - reach, cx + reach, baseY + height + 4, cz + reach);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putInt("CX", cx);
        tag.putInt("CZ", cz);
        tag.putInt("BaseY", baseY);
        tag.putInt("Radius", radius);
        tag.putInt("Height", height);
        tag.putLong("Seed", seed);
    }

    /** Distance from the centre to the foot of the volcano in this direction (wavy, not a perfect circle). */
    private double edge(double angle) {
        double w = 0.07 * Math.sin(2 * angle + outlinePhase[0]) + 0.05 * Math.sin(3 * angle + outlinePhase[1])
                + 0.03 * Math.sin(5 * angle + outlinePhase[2]) + 0.02 * Math.sin(7 * angle + outlinePhase[3]);
        return radius * (1 + w);
    }

    private double profile(double t) {               // 1 at the centre, 0 at the foot; steep top, gentle base
        return Math.pow(1 - t, 1.7);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        double craterR = radius * 0.17;
        double minEdge = radius * 0.83;
        int rimLow = baseY + (int) (height * profile(craterR / minEdge));
        int lavaLevel = rimLow - 2;

        for (int x = chunkBox.minX(); x <= chunkBox.maxX(); x++) {
            for (int z = chunkBox.minZ(); z <= chunkBox.maxZ(); z++) {
                double dx = x - cx, dz = z - cz;
                double d = Math.sqrt(dx * dx + dz * dz);
                double angle = Math.atan2(dz, dx);
                double edge = edge(angle);
                if (d >= edge) continue;
                double t = d / edge;
                int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;

                if (d < craterR) {                     // the crater: a bowl of lava
                    int floor = lavaLevel - 3 - (int) ((craterR - d) * 0.5);
                    for (int y = ground + 1; y <= floor; y++)
                        place(level, chunkBox, x, y, z, y >= floor - 1 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
                    for (int y = floor + 1; y <= lavaLevel; y++) place(level, chunkBox, x, y, z, Blocks.LAVA.defaultBlockState());
                    for (int y = lavaLevel + 1; y <= baseY + height + 2; y++) place(level, chunkBox, x, y, z, Blocks.AIR.defaultBlockState());
                    continue;
                }

                double bumps = (Math.sin(x * 0.23 + p1) * Math.cos(z * 0.21 + p2) + 0.5 * Math.sin(x * 0.51 + z * 0.43 + p3)) * 1.5;
                int top = baseY + (int) Math.round(height * profile(t) + bumps * Math.min(1, t * 2.5));
                // just inside the rim, keep the crater wall solid and above the lava
                if (d < craterR + 3) top = Math.max(top, lavaLevel + 2);
                if (top <= ground) continue;

                boolean streak = false;
                if (t > 0.17 && t < 0.85) {
                    for (double a : streaks) {
                        double diff = Math.abs(Math.atan2(Math.sin(angle - a), Math.cos(angle - a)));
                        double wiggle = 0.6 * Math.sin(d * 0.25 + a * 3);
                        if (Math.abs(diff * d - wiggle) < 0.9 + (1 - t)) { streak = true; break; }
                    }
                }
                double patch = Math.sin(x * 0.17 + p4) * Math.sin(z * 0.19 + p1) + 0.35 * Math.sin((x + z) * 0.41 + p2);

                for (int y = ground + 1; y <= top; y++) {
                    int depth = top - y;
                    BlockState s;
                    if (depth == 0) s = surface(t, streak, patch, random);
                    else if (depth < 3) s = streak && depth == 1 ? Blocks.MAGMA_BLOCK.defaultBlockState()
                            : t > 0.8 ? ModBlocks.ASHEN_SOIL.get().defaultBlockState() : Blocks.BASALT.defaultBlockState();
                    else s = Blocks.BLACKSTONE.defaultBlockState();
                    place(level, chunkBox, x, y, z, s);
                }
                // clear anything (trees, plants) standing on the new slope
                for (int y = top + 1; y <= top + 18; y++) {
                    BlockState above = level.getBlockState(new BlockPos(x, y, z));
                    if (above.isAir()) break;
                    if (above.is(ModBlocks.BURNT_OAK_LOG.get()) || above.canBeReplaced())
                        place(level, chunkBox, x, y, z, Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private BlockState surface(double t, boolean streak, double patch, RandomSource random) {
        if (streak) return Blocks.MAGMA_BLOCK.defaultBlockState();
        if (t > 0.82) return patch > 0.2 ? Blocks.BASALT.defaultBlockState() : ModBlocks.ASHEN_SOIL.get().defaultBlockState();
        if (t > 0.55) return patch > 0.45 ? ModBlocks.ASHEN_SOIL.get().defaultBlockState()
                : patch > -0.3 ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
        if (t < 0.28 && random.nextInt(9) == 0) return Blocks.MAGMA_BLOCK.defaultBlockState();
        return patch > 0.1 ? Blocks.BLACKSTONE.defaultBlockState() : patch > -0.5 ? Blocks.BASALT.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState();
    }

    private static void place(WorldGenLevel level, BoundingBox chunkBox, int x, int y, int z, BlockState state) {
        BlockPos pos = new BlockPos(x, y, z);
        if (chunkBox.isInside(pos)) level.setBlock(pos, state, 2);
    }
}
