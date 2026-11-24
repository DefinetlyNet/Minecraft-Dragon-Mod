package com.wildcreatures.block;

import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

import java.util.function.Supplier;

/** A log that turns into its stripped version when right-clicked with an axe. */
public class StrippableLogBlock extends RotatedPillarBlock {
    private final Supplier<? extends Block> stripped;

    public StrippableLogBlock(Properties props, Supplier<? extends Block> stripped) {
        super(props);
        this.stripped = stripped;
    }

    @Override
    public BlockState getToolModifiedState(BlockState state, UseOnContext context, ToolAction action, boolean simulate) {
        if (action == ToolActions.AXE_STRIP) return stripped.get().defaultBlockState().setValue(AXIS, state.getValue(AXIS));
        return super.getToolModifiedState(state, context, action, simulate);
    }
}
