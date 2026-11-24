package com.wildcreatures;

import com.wildcreatures.block.StrippableLogBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/** Blocks of the Ashen Wastes biome. Burnt oak is already burnt, so it does not catch fire. */
public final class ModBlocks {
    private ModBlocks() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, WildCreatures.MOD_ID);

    public static final RegistryObject<Block> ASHEN_SOIL = register("ashen_soil", () -> new Block(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.6F).sound(SoundType.SAND)));

    public static final RegistryObject<Block> STRIPPED_BURNT_OAK_LOG = register("stripped_burnt_oak_log",
            () -> new RotatedPillarBlock(wood(MapColor.COLOR_BROWN)));
    public static final RegistryObject<Block> STRIPPED_BURNT_OAK_WOOD = register("stripped_burnt_oak_wood",
            () -> new RotatedPillarBlock(wood(MapColor.COLOR_BROWN)));
    public static final RegistryObject<Block> BURNT_OAK_LOG = register("burnt_oak_log",
            () -> new StrippableLogBlock(wood(MapColor.COLOR_BLACK), STRIPPED_BURNT_OAK_LOG));
    public static final RegistryObject<Block> BURNT_OAK_WOOD = register("burnt_oak_wood",
            () -> new StrippableLogBlock(wood(MapColor.COLOR_BLACK), STRIPPED_BURNT_OAK_WOOD));
    public static final RegistryObject<Block> BURNT_OAK_PLANKS = register("burnt_oak_planks",
            () -> new Block(wood(MapColor.COLOR_BROWN).strength(2.0F, 3.0F)));
    public static final RegistryObject<Block> BURNT_OAK_STAIRS = register("burnt_oak_stairs",
            () -> new StairBlock(() -> BURNT_OAK_PLANKS.get().defaultBlockState(), wood(MapColor.COLOR_BROWN).strength(2.0F, 3.0F)));
    public static final RegistryObject<Block> BURNT_OAK_SLAB = register("burnt_oak_slab",
            () -> new SlabBlock(wood(MapColor.COLOR_BROWN).strength(2.0F, 3.0F)));

    private static BlockBehaviour.Properties wood(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD);
    }

    private static RegistryObject<Block> register(String name, Supplier<Block> block) {
        RegistryObject<Block> obj = BLOCKS.register(name, block);
        ModItems.ITEMS.register(name, () -> new BlockItem(obj.get(), new Item.Properties()));
        return obj;
    }
}
