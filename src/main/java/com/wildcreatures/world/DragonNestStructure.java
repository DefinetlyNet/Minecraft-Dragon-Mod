package com.wildcreatures.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * A dragon nest: a ring of wood (or ice, or stone) with a loot chest in the middle, and sometimes a dragon egg.
 * Each kind of dragon has its own nest, set by "style" in data/wildcreatures/worldgen/structure/*_nest.json (the Red
 * Dragon's, dragon_nest.json, has none). Which biomes it can appear in is in the biome tags has_*_nest, and how rare
 * it is in worldgen/structure_set/.
 */
public class DragonNestStructure extends Structure {
    public static final Codec<DragonNestStructure> CODEC = RecordCodecBuilder.create(i -> i.group(
            settingsCodec(i),
            NestStyle.CODEC.optionalFieldOf("style", NestStyle.RED).forGetter(s -> s.style)
    ).apply(i, DragonNestStructure::new));

    private final NestStyle style;

    public DragonNestStructure(StructureSettings settings, NestStyle style) {
        super(settings);
        this.style = style;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        int x = ctx.chunkPos().getMiddleBlockX(), z = ctx.chunkPos().getMiddleBlockZ();
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int[] o : new int[][]{{0, 0}, {7, 7}, {-7, 7}, {7, -7}, {-7, -7}}) {
            int h = height(ctx, x + o[0], z + o[1], Heightmap.Types.OCEAN_FLOOR_WG);
            min = Math.min(min, h);
            max = Math.max(max, h);
        }
        if (max - min > style.maxBump()) return Optional.empty();          // only on fairly flat ground (mountain nests cope with more)
        int y = height(ctx, x, z, Heightmap.Types.WORLD_SURFACE_WG);
        if (y <= ctx.chunkGenerator().getSeaLevel()) return Optional.empty();
        // not in water: on flat ground nothing may stand above the lowest corner; on a slope, check right under the middle
        int below = style.mountain() ? height(ctx, x, z, Heightmap.Types.OCEAN_FLOOR_WG) : min;
        if (y - below > 2) return Optional.empty();
        BlockPos center = new BlockPos(x, y, z);
        return Optional.of(new GenerationStub(center, b -> b.addPiece(new DragonNestPiece(center, ctx.random().nextLong(), style))));
    }

    private static int height(GenerationContext ctx, int x, int z, Heightmap.Types type) {
        return ctx.chunkGenerator().getFirstOccupiedHeight(x, z, type, ctx.heightAccessor(), ctx.randomState());
    }

    @Override
    public StructureType<?> type() {
        return ModWorldgen.DRAGON_NEST.get();
    }
}
