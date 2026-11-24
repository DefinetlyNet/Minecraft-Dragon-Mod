package com.wildcreatures;

import com.wildcreatures.block.BurntBushBlock;
import com.wildcreatures.block.BurntOakGrower;
import com.wildcreatures.block.BurntSigns;
import com.wildcreatures.block.StrippableLogBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.PushReaction;
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

    public static final BlockSetType BURNT_OAK_SET = BlockSetType.register(new BlockSetType("wildcreatures:burnt_oak"));
    public static final WoodType BURNT_OAK_TYPE = WoodType.register(new WoodType("wildcreatures:burnt_oak", BURNT_OAK_SET));

    public static final RegistryObject<Block> BURNT_OAK_FENCE = register("burnt_oak_fence",
            () -> new FenceBlock(wood(MapColor.COLOR_BROWN).strength(2.0F, 3.0F)));
    public static final RegistryObject<Block> BURNT_OAK_FENCE_GATE = register("burnt_oak_fence_gate",
            () -> new FenceGateBlock(wood(MapColor.COLOR_BROWN).strength(2.0F, 3.0F), BURNT_OAK_TYPE));
    public static final RegistryObject<Block> BURNT_OAK_DOOR = registerNoItem("burnt_oak_door",
            () -> new DoorBlock(wood(MapColor.COLOR_BROWN).strength(3.0F).noOcclusion(), BURNT_OAK_SET));
    public static final RegistryObject<Block> BURNT_OAK_TRAPDOOR = register("burnt_oak_trapdoor",
            () -> new TrapDoorBlock(wood(MapColor.COLOR_BROWN).strength(3.0F).noOcclusion().isValidSpawn((s, l, p, e) -> false), BURNT_OAK_SET));
    public static final RegistryObject<Block> BURNT_OAK_BUTTON = register("burnt_oak_button",
            () -> new ButtonBlock(BlockBehaviour.Properties.of().noCollission().strength(0.5F).pushReaction(PushReaction.DESTROY), BURNT_OAK_SET, 30, true));
    public static final RegistryObject<Block> BURNT_OAK_PRESSURE_PLATE = register("burnt_oak_pressure_plate",
            () -> new PressurePlateBlock(PressurePlateBlock.Sensitivity.EVERYTHING, wood(MapColor.COLOR_BROWN).noCollission().strength(0.5F)
                    .pushReaction(PushReaction.DESTROY), BURNT_OAK_SET));
    public static final RegistryObject<Block> BURNT_OAK_SIGN = registerNoItem("burnt_oak_sign",
            () -> new BurntSigns.Standing(wood(MapColor.COLOR_BROWN).noCollission().strength(1.0F), BURNT_OAK_TYPE));
    public static final RegistryObject<Block> BURNT_OAK_WALL_SIGN = registerNoItem("burnt_oak_wall_sign",
            () -> new BurntSigns.Wall(wood(MapColor.COLOR_BROWN).noCollission().strength(1.0F).lootFrom(BURNT_OAK_SIGN), BURNT_OAK_TYPE));
    public static final RegistryObject<Block> BURNT_OAK_HANGING_SIGN = registerNoItem("burnt_oak_hanging_sign",
            () -> new BurntSigns.Ceiling(wood(MapColor.COLOR_BROWN).noCollission().strength(1.0F), BURNT_OAK_TYPE));
    public static final RegistryObject<Block> BURNT_OAK_WALL_HANGING_SIGN = registerNoItem("burnt_oak_wall_hanging_sign",
            () -> new BurntSigns.WallHanging(wood(MapColor.COLOR_BROWN).noCollission().strength(1.0F).lootFrom(BURNT_OAK_HANGING_SIGN), BURNT_OAK_TYPE));
    public static final RegistryObject<Block> BURNT_OAK_SAPLING = register("burnt_oak_sapling",
            () -> new SaplingBlock(new BurntOakGrower(), BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).noCollission()
                    .randomTicks().instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY)));
    public static final RegistryObject<Block> BURNT_BUSH = register("burnt_bush",
            () -> new BurntBushBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).replaceable().noCollission()
                    .instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY)));

    private static BlockBehaviour.Properties wood(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD);
    }

    private static RegistryObject<Block> registerNoItem(String name, Supplier<Block> block) {
        return BLOCKS.register(name, block);
    }

    private static RegistryObject<Block> register(String name, Supplier<Block> block) {
        RegistryObject<Block> obj = BLOCKS.register(name, block);
        ModItems.ITEMS.register(name, () -> new BlockItem(obj.get(), new Item.Properties()));
        return obj;
    }
}
