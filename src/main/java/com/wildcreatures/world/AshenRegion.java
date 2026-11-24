package com.wildcreatures.world;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import terrablender.api.Region;
import terrablender.api.RegionType;

import java.util.function.Consumer;

/**
 * Tells TerraBlender where the Ashen Wastes go: in our share of the world, the hot dry
 * savanna, desert and badlands spots become Ashen Wastes. The weight (how big our share is) is in
 * the config.
 */
public class AshenRegion extends Region {
    public AshenRegion(ResourceLocation name, int weight) {
        super(name, RegionType.OVERWORLD, weight);
    }

    @Override
    public void addBiomes(Registry<Biome> registry, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
        addModifiedVanillaOverworldBiomes(mapper, builder -> {
            builder.replaceBiome(Biomes.SAVANNA, ModWorldgen.ASHEN_WASTES);
            builder.replaceBiome(Biomes.SAVANNA_PLATEAU, ModWorldgen.ASHEN_WASTES);
            builder.replaceBiome(Biomes.WINDSWEPT_SAVANNA, ModWorldgen.ASHEN_WASTES);
            builder.replaceBiome(Biomes.DESERT, ModWorldgen.ASHEN_WASTES);
            builder.replaceBiome(Biomes.BADLANDS, ModWorldgen.ASHEN_WASTES);
            builder.replaceBiome(Biomes.WOODED_BADLANDS, ModWorldgen.ASHEN_WASTES);
            builder.replaceBiome(Biomes.ERODED_BADLANDS, ModWorldgen.ASHEN_WASTES);
        });
    }
}
