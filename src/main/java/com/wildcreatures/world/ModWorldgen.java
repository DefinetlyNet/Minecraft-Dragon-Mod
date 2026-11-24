package com.wildcreatures.world;

import com.wildcreatures.WildCreatures;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * World generation code. The biome itself, and where each feature is placed, live in the
 * data files under data/wildcreatures/worldgen/ (biome/ashen_wastes.json is the main one).
 */
public final class ModWorldgen {
    private ModWorldgen() {}

    public static final ResourceKey<Biome> ASHEN_WASTES =
            ResourceKey.create(Registries.BIOME, new ResourceLocation(WildCreatures.MOD_ID, "ashen_wastes"));

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, WildCreatures.MOD_ID);
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, WildCreatures.MOD_ID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, WildCreatures.MOD_ID);

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> BURNT_TREE = FEATURES.register("burnt_tree", BurntTreeFeature::new);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> LAVA_POOL = FEATURES.register("lava_pool", LavaPoolFeature::new);

    public static final RegistryObject<StructureType<VolcanoStructure>> VOLCANO = STRUCTURE_TYPES.register("volcano", () -> () -> VolcanoStructure.CODEC);
    public static final RegistryObject<StructureType<DragonNestStructure>> DRAGON_NEST = STRUCTURE_TYPES.register("dragon_nest", () -> () -> DragonNestStructure.CODEC);
    public static final RegistryObject<StructurePieceType> DRAGON_NEST_PIECE = STRUCTURE_PIECES.register("dragon_nest",
            () -> (StructurePieceType.ContextlessType) DragonNestPiece::new);
    public static final RegistryObject<StructurePieceType> VOLCANO_PIECE = STRUCTURE_PIECES.register("volcano",
            () -> (StructurePieceType.ContextlessType) VolcanoPiece::new);
}
