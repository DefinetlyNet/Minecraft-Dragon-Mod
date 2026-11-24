package com.wildcreatures.block;

import com.wildcreatures.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WoodType;

/** Burnt oak signs and hanging signs: vanilla sign blocks that use our own sign block entities. */
public final class BurntSigns {
    private BurntSigns() {}

    public static class SignEntity extends SignBlockEntity {
        public SignEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.SIGN.get(), pos, state); }
        @Override public BlockEntityType<?> getType() { return ModBlockEntities.SIGN.get(); }
    }

    public static class HangingSignEntity extends HangingSignBlockEntity {
        public HangingSignEntity(BlockPos pos, BlockState state) { super(pos, state); }
        @Override public BlockEntityType<?> getType() { return ModBlockEntities.HANGING_SIGN.get(); }
    }

    public static class Standing extends StandingSignBlock {
        public Standing(Properties p, WoodType t) { super(p, t); }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SignEntity(pos, state); }
    }

    public static class Wall extends WallSignBlock {
        public Wall(Properties p, WoodType t) { super(p, t); }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SignEntity(pos, state); }
    }

    public static class Ceiling extends CeilingHangingSignBlock {
        public Ceiling(Properties p, WoodType t) { super(p, t); }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new HangingSignEntity(pos, state); }
    }

    public static class WallHanging extends WallHangingSignBlock {
        public WallHanging(Properties p, WoodType t) { super(p, t); }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new HangingSignEntity(pos, state); }
    }
}
