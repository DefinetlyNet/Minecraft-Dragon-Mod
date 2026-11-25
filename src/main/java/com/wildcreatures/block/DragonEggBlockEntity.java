package com.wildcreatures.block;

import com.wildcreatures.CreatureConfig;
import com.wildcreatures.ModBlockEntities;
import com.wildcreatures.entity.AbstractDragon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The hatching side of a dragon egg: who it belongs to and how far along it is. An egg nobody has claimed (one still
 * lying in its nest) just waits. A claimed one counts up to hatchMinutes, twice as fast in the spot its kind likes,
 * cracks at a third and two thirds of the way, and then hatches into a baby dragon for its owner.
 */
public class DragonEggBlockEntity extends BlockEntity {
    @Nullable private UUID owner;
    /** Ticks of hatching done (a tick in the spot it likes counts double). Saved, and kept on the item if the egg is picked up. */
    private int progress;
    private boolean liked;
    private int checkIn;

    public DragonEggBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRAGON_EGG.get(), pos, state);
    }

    @Nullable
    public UUID owner() { return owner; }

    /** The egg is now this player's, and starts (or carries on) hatching. */
    public void claim(Player player) {
        owner = player.getUUID();
        setChanged();
    }

    private static int hatchTicks() {
        return Math.max(1, (int) Math.round(CreatureConfig.EGG_HATCH_MINUTES.get() * 1200.0));
    }

    /** How cracked it should look: 0, 1 or 2. */
    public int stage() {
        return Math.min(2, (int) ((long) progress * 3 / hatchTicks()));
    }

    /** "It will hatch in about 6 minutes", or "It likes it here, so ...", or "It's about to hatch!". */
    public Component status() {
        boolean likes = level != null && getBlockState().getBlock() instanceof DragonEggBlock egg && egg.kind().likes(level, worldPosition);
        int ticksLeft = (hatchTicks() - progress) / (likes ? 2 : 1);
        if (ticksLeft < 1800) return Component.translatable("message.wildcreatures.egg_soon");
        int minutes = Math.round(ticksLeft / 1200.0F);
        return Component.translatable(likes ? "message.wildcreatures.egg_time_liked" : "message.wildcreatures.egg_time", minutes);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DragonEggBlockEntity egg) {
        egg.tick(level, pos, state);
    }

    private void tick(Level level, BlockPos pos, BlockState state) {
        if (owner == null || !(state.getBlock() instanceof DragonEggBlock block)) return;
        if (--checkIn <= 0) {
            checkIn = 20;
            liked = block.kind().likes(level, pos);
            if (liked && level instanceof ServerLevel server) {
                server.sendParticles(block.kind().particle(), pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 3, 0.3, 0.35, 0.3, 0.01);
            }
            setChanged();
        }
        progress += liked ? 2 : 1;
        if (progress >= hatchTicks()) {
            hatch(level, pos, state, block);
            return;
        }
        int stage = stage();
        if (stage > state.getValue(DragonEggBlock.HATCH)) {
            level.setBlock(pos, state.setValue(DragonEggBlock.HATCH, stage), 2);
            level.playSound(null, pos, SoundEvents.SNIFFER_EGG_CRACK, SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.3F);
            if (level instanceof ServerLevel server) {
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        14, 0.25, 0.3, 0.25, 0.05);
            }
        }
    }

    /** Out it comes: the shell breaks, and a baby dragon that belongs to the egg's owner is standing there. */
    private void hatch(Level level, BlockPos pos, BlockState state, DragonEggBlock block) {
        UUID who = owner;
        level.playSound(null, pos, SoundEvents.SNIFFER_EGG_HATCH, SoundSource.BLOCKS, 1.0F, 0.9F);
        level.destroyBlock(pos, false);
        AbstractDragon baby = block.kind().dragon().create(level);
        if (baby == null) return;
        baby.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
        baby.hatchFor(who);
        level.addFreshEntity(baby);
        baby.playAmbientSound();
        baby.showHearts();
        Player player = who != null ? level.getPlayerByUUID(who) : null;
        if (player != null) player.sendSystemMessage(Component.translatable("message.wildcreatures.egg_hatched", baby.getType().getDescription()));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        if (progress > 0) tag.putInt("Progress", progress);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        progress = Math.max(0, tag.getInt("Progress"));
    }
}
