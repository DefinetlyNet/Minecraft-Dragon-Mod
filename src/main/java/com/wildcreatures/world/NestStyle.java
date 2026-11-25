package com.wildcreatures.world;

import com.mojang.serialization.Codec;
import com.wildcreatures.ModBlocks;
import com.wildcreatures.WildCreatures;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * What a dragon nest is built from, per kind of dragon. The Red Dragon's nest in the Ashen Wastes is woven from burnt oak;
 * the others sit up in the mountains (so they cope with steeper ground): the Frost Dragon's is spruce and ice packed with snow,
 * the Lightning Dragon's is dark oak and stone with a lightning rod on its rim, and the Black Dragon's is basalt, blackstone
 * and obsidian, with amethyst growing on its floor.
 */
public enum NestStyle implements StringRepresentable {
    RED, FROST, LIGHTNING, BLACK;

    public static final Codec<NestStyle> CODEC = StringRepresentable.fromEnum(NestStyle::values);

    @Override
    public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }

    public static NestStyle byName(String name) {
        for (NestStyle style : values()) if (style.getSerializedName().equals(name)) return style;
        return RED;
    }

    /** Mountain nests can sit on bumpier ground, with deeper footings and more room cleared above. */
    public boolean mountain() { return this != RED; }

    /** How much higher the highest corner of the site can be than the lowest. */
    public int maxBump() { return mountain() ? 10 : 6; }

    /** How far down the nest builds footing to reach solid ground. */
    public int footingDepth() { return mountain() ? 10 : 5; }

    /** How far up it clears the space over the nest. */
    public int headroom() { return mountain() ? 8 : 5; }

    public ResourceLocation lootTable() {
        return new ResourceLocation(WildCreatures.MOD_ID, this == RED ? "chests/dragon_nest" : "chests/" + getSerializedName() + "_dragon_nest");
    }

    public Block egg() {
        return switch (this) {
            case RED -> ModBlocks.RED_DRAGON_EGG.get();
            case FROST -> ModBlocks.FROST_DRAGON_EGG.get();
            case LIGHTNING -> ModBlocks.LIGHTNING_DRAGON_EGG.get();
            case BLACK -> ModBlocks.BLACK_DRAGON_EGG.get();
        };
    }

    /** One block of the woven rim. Logs lie along {@code axis} or stand up. */
    public BlockState rim(RandomSource r, Direction.Axis axis) {
        int roll = r.nextInt(20);
        return switch (this) {
            case RED -> log(ModBlocks.BURNT_OAK_WOOD.get(), axis);
            case FROST -> roll < 12 ? log(Blocks.SPRUCE_WOOD, axis) : roll < 18 ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.SNOW_BLOCK.defaultBlockState();
            case LIGHTNING -> roll < 14 ? log(Blocks.DARK_OAK_WOOD, axis) : roll < 16 ? Blocks.COBBLESTONE.defaultBlockState()
                    : roll < 18 ? Blocks.ANDESITE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
            case BLACK -> roll < 9 ? log(Blocks.BASALT, axis) : roll < 15 ? Blocks.BLACKSTONE.defaultBlockState()
                    : roll < 18 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.CRYING_OBSIDIAN.defaultBlockState();
        };
    }

    /** A branch sticking out over the rim. */
    public BlockState branch(Direction.Axis axis) {
        return switch (this) {
            case RED -> log(ModBlocks.BURNT_OAK_WOOD.get(), axis);
            case FROST -> log(Blocks.SPRUCE_WOOD, axis);
            case LIGHTNING -> log(Blocks.DARK_OAK_WOOD, axis);
            case BLACK -> log(Blocks.BASALT, axis);
        };
    }

    /** The floor of the bowl. */
    public BlockState lining(RandomSource r) {
        int roll = r.nextInt(10);
        return switch (this) {
            case RED -> roll < 5 ? ModBlocks.ASHEN_SOIL.get().defaultBlockState() : roll < 8 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.BASALT.defaultBlockState();
            case FROST -> roll < 5 ? Blocks.SNOW_BLOCK.defaultBlockState() : roll < 8 ? Blocks.PACKED_ICE.defaultBlockState() : Blocks.BLUE_ICE.defaultBlockState();
            case LIGHTNING -> roll < 4 ? Blocks.GRAVEL.defaultBlockState() : roll < 7 ? Blocks.COARSE_DIRT.defaultBlockState()
                    : roll < 9 ? Blocks.TUFF.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
            case BLACK -> roll < 4 ? Blocks.BLACKSTONE.defaultBlockState() : roll < 8 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState();
        };
    }

    /** What the nest stands on where the ground falls away under it. */
    public BlockState footing() {
        return switch (this) {
            case RED -> Blocks.BLACKSTONE.defaultBlockState();
            case FROST -> Blocks.SNOW_BLOCK.defaultBlockState();
            case LIGHTNING -> Blocks.STONE.defaultBlockState();
            case BLACK -> Blocks.DEEPSLATE.defaultBlockState();
        };
    }

    /** Odds and ends lying on the floor of the bowl (null for bare floor). */
    @Nullable
    public BlockState floorClutter(RandomSource r) {
        int roll = r.nextInt(14);
        if (roll == 0) return Blocks.BONE_BLOCK.defaultBlockState();
        return switch (this) {
            case RED -> roll == 1 ? ModBlocks.BURNT_BUSH.get().defaultBlockState() : null;
            case FROST -> roll <= 3 ? Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + r.nextInt(2)) : null;
            case LIGHTNING -> null;
            case BLACK -> roll <= 2 ? Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP) : null;
        };
    }

    private static BlockState log(Block block, Direction.Axis axis) {
        return block.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }
}
