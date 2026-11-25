package com.wildcreatures.block;

import com.wildcreatures.ModBlockEntities;
import com.wildcreatures.ModEntities;
import com.wildcreatures.ModSounds;
import com.wildcreatures.entity.AbstractDragon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * A dragon egg, found in dragon nests. It starts to hatch once a player places it (or right-clicks it where it lies), and
 * after hatchMinutes a baby dragon climbs out that belongs to that player. It cracks twice on the way. Each kind hatches
 * twice as fast in the spot it likes ({@link Kind#likes}). The hatching itself is in {@link DragonEggBlockEntity}.
 * <p>
 * Taking an egg nobody has claimed yet makes the wild dragons of its kind nearby come after you.
 */
public class DragonEggBlock extends BaseEntityBlock {
    /** 0 whole, 1 slightly cracked, 2 very cracked (the same property as the sniffer egg). */
    public static final IntegerProperty HATCH = BlockStateProperties.HATCH;
    /** The same shape as the vanilla dragon egg. */
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);

    /** The kinds of egg: which dragon hatches out, and where it likes to be kept. */
    public enum Kind {
        RED, FROST, LIGHTNING, BLACK;

        public String id() { return name().toLowerCase(Locale.ROOT); }

        public EntityType<? extends AbstractDragon> dragon() {
            return switch (this) {
                case RED -> ModEntities.RED_DRAGON.get();
                case FROST -> ModEntities.FROST_DRAGON.get();
                case LIGHTNING -> ModEntities.LIGHTNING_DRAGON.get();
                case BLACK -> ModEntities.BLACK_DRAGON.get();
            };
        }

        /**
         * Whether the egg is somewhere it likes, where it hatches twice as fast: Red eggs near fire, lava or magma, Frost eggs
         * next to snow or ice, Lightning eggs next to a lightning rod or out in a thunderstorm, Black eggs in the dark.
         */
        public boolean likes(Level level, BlockPos pos) {
            return switch (this) {
                case RED -> near(level, pos, 2, s -> s.is(BlockTags.FIRE) || CampfireBlock.isLitCampfire(s) || s.is(Blocks.MAGMA_BLOCK)
                        || s.getFluidState().is(FluidTags.LAVA));
                case FROST -> near(level, pos, 2, s -> s.is(BlockTags.ICE) || s.is(BlockTags.SNOW));
                case LIGHTNING -> level.isThundering() && level.canSeeSky(pos.above()) || near(level, pos, 3, s -> s.is(Blocks.LIGHTNING_ROD));
                case BLACK -> level.getMaxLocalRawBrightness(pos) <= 4;
            };
        }

        /** The little puffs it gives off while it's somewhere it likes. */
        public ParticleOptions particle() {
            return switch (this) {
                case RED -> ParticleTypes.SMALL_FLAME;
                case FROST -> ParticleTypes.SNOWFLAKE;
                case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
                case BLACK -> ParticleTypes.REVERSE_PORTAL;
            };
        }

        /** Any block within {@code r} blocks sideways (and one up or down) that matches. */
        private static boolean near(Level level, BlockPos pos, int r, Predicate<BlockState> test) {
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -1, -r), pos.offset(r, 1, r))) {
                if (test.test(level.getBlockState(p))) return true;
            }
            return false;
        }
    }

    private final Kind kind;

    public DragonEggBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(HATCH, 0));
    }

    public Kind kind() { return kind; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HATCH);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return SHAPE; }

    /** Drawn with its block model (a block entity block is invisible unless it says so). */
    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Nullable @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new DragonEggBlockEntity(pos, state); }

    @Nullable @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.DRAGON_EGG.get(), DragonEggBlockEntity::serverTick);
    }

    /** Whoever places the egg gets the dragon. An egg that was partly hatched before it was picked up shows its cracks again. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(placer instanceof Player player) || !(level.getBlockEntity(pos) instanceof DragonEggBlockEntity egg)) return;
        egg.claim(player);
        int stage = egg.stage();
        if (stage != state.getValue(HATCH)) level.setBlock(pos, state.setValue(HATCH, stage), 2);
        player.displayClientMessage(egg.status(), true);
    }

    /** Right-click: claims an egg nobody has yet (it starts to hatch right there), or says how long yours has to go. */
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DragonEggBlockEntity egg)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (egg.owner() == null) {
            egg.claim(player);
            angerParents(level, pos, player);
            player.displayClientMessage(Component.translatable("message.wildcreatures.egg_claimed", egg.status()), true);
        } else if (egg.owner().equals(player.getUUID())) {
            player.displayClientMessage(egg.status(), true);
        } else {
            player.displayClientMessage(Component.translatable("message.wildcreatures.egg_not_yours"), true);
        }
        return InteractionResult.CONSUME;
    }

    /** Taking an egg nobody has claimed yet (one still lying in its nest) makes the wild dragons of its kind nearby come after you. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof DragonEggBlockEntity egg && egg.owner() == null) angerParents(level, pos, player);
        super.playerWillDestroy(level, pos, state, player);
    }

    private void angerParents(Level level, BlockPos pos, Player player) {
        EntityType<?> type = kind.dragon();
        List<AbstractDragon> parents = level.getEntitiesOfClass(AbstractDragon.class, new AABB(pos).inflate(48.0),
                d -> d.getType() == type && !d.isTame() && d.isAlive() && !d.isBaby());
        for (AbstractDragon dragon : parents) dragon.setTarget(player);
        if (!parents.isEmpty() && player.canBeSeenAsEnemy()) {
            AbstractDragon first = parents.get(0);
            level.playSound(null, first.getX(), first.getY(), first.getZ(), ModSounds.DRAGON_ROAR.get(), SoundSource.HOSTILE, 4.0F, first.getVoicePitch());
            player.displayClientMessage(Component.translatable("message.wildcreatures.egg_parent_angry", type.getDescription()), true);
        }
    }
}
