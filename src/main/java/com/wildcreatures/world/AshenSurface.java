package com.wildcreatures.world;

import com.wildcreatures.ModBlocks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.Noises;

/** The ground of the Ashen Wastes: ashen soil on top with patches of basalt and blackstone. */
public final class AshenSurface {
    private AshenSurface() {}

    public static SurfaceRules.RuleSource rules() {
        SurfaceRules.RuleSource ash = SurfaceRules.state(ModBlocks.ASHEN_SOIL.get().defaultBlockState());
        SurfaceRules.RuleSource basalt = SurfaceRules.state(Blocks.BASALT.defaultBlockState());
        SurfaceRules.RuleSource blackstone = SurfaceRules.state(Blocks.BLACKSTONE.defaultBlockState());
        SurfaceRules.RuleSource top = SurfaceRules.sequence(
                SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.SURFACE, 0.45, 0.6), basalt),
                SurfaceRules.ifTrue(SurfaceRules.noiseCondition(Noises.SURFACE, -0.62, -0.5), blackstone),
                ash);
        return SurfaceRules.ifTrue(SurfaceRules.isBiome(ModWorldgen.ASHEN_WASTES),
                SurfaceRules.ifTrue(SurfaceRules.abovePreliminarySurface(), SurfaceRules.sequence(
                        SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, top),
                        SurfaceRules.ifTrue(SurfaceRules.UNDER_FLOOR, ash))));
    }
}
