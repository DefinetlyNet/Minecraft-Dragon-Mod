package com.wildcreatures.world;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * A dragon nest: a ring of burnt wood with a loot chest in the middle. It can appear in any
 * biome listed in data/wildcreatures/tags/worldgen/biome/has_dragon_nest.json (every biome the
 * Red Dragon lives in). How rare it is: data/wildcreatures/worldgen/structure_set/dragon_nest.json.
 */
public class DragonNestStructure extends Structure {
    public static final Codec<DragonNestStructure> CODEC = simpleCodec(DragonNestStructure::new);

    public DragonNestStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        int x = ctx.chunkPos().getMiddleBlockX(), z = ctx.chunkPos().getMiddleBlockZ();
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int[] o : new int[][]{{0, 0}, {7, 7}, {-7, 7}, {7, -7}, {-7, -7}}) {
            int h = ctx.chunkGenerator().getFirstOccupiedHeight(x + o[0], z + o[1], Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        if (max - min > 6) return Optional.empty();                 // only on fairly flat ground
        int y = ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
        if (y <= ctx.chunkGenerator().getSeaLevel() || y - min > 2) return Optional.empty();   // not in water
        BlockPos center = new BlockPos(x, y, z);
        return Optional.of(new GenerationStub(center, b -> b.addPiece(new DragonNestPiece(center, ctx.random().nextLong()))));
    }

    @Override
    public StructureType<?> type() {
        return ModWorldgen.DRAGON_NEST.get();
    }
}
