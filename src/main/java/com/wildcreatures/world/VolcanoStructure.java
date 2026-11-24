package com.wildcreatures.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * The big volcano of the Ashen Wastes. How often one appears is set in
 * data/wildcreatures/worldgen/structure_set/volcano.json; it only starts inside the biome.
 */
public class VolcanoStructure extends Structure {
    public static final Codec<VolcanoStructure> CODEC = simpleCodec(VolcanoStructure::new);

    public VolcanoStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        int x = ctx.chunkPos().getMiddleBlockX(), z = ctx.chunkPos().getMiddleBlockZ();
        int y = ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
        if (y < ctx.chunkGenerator().getSeaLevel() - 2) return Optional.empty();
        // the whole volcano must fit inside the Ashen Wastes, so it never spills into other biomes
        for (int ring : new int[]{24, 44})
            for (int i = 0; i < 12; i++) {
                double a = i * Math.PI / 6;
                int bx = x + (int) (Math.cos(a) * ring), bz = z + (int) (Math.sin(a) * ring);
                if (!ctx.biomeSource().getNoiseBiome(QuartPos.fromBlock(bx), QuartPos.fromBlock(y), QuartPos.fromBlock(bz),
                        ctx.randomState().sampler()).is(ModWorldgen.ASHEN_WASTES)) return Optional.empty();
            }
        BlockPos center = new BlockPos(x, y, z);
        return Optional.of(new GenerationStub(center, builder ->
                builder.addPiece(new VolcanoPiece(center, ctx.random().nextLong(), ctx.random()))));
    }

    @Override
    public StructureType<?> type() {
        return ModWorldgen.VOLCANO.get();
    }
}
