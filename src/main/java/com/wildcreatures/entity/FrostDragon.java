package com.wildcreatures.entity;

import com.wildcreatures.CreatureConfig;
import com.wildcreatures.DragonStats;
import com.wildcreatures.ModItems;
import com.wildcreatures.ModSounds;
import com.wildcreatures.WildCreatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A pale blue dragon from the snowy peaks and ice spikes. Same body and moves as the Red Dragon, but its
 * breath is freezing: it slows and freezes whatever it hits, puts out fires, turns water to ice and lava
 * to obsidian, and leaves a dusting of snow.
 */
public class FrostDragon extends AbstractDragon {
    public static final ResourceKey<DamageType> FROST_BREATH =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(WildCreatures.MOD_ID, "frost_breath"));

    public FrostDragon(EntityType<? extends FrostDragon> type, Level level) {
        super(type, level);
    }

    @Override public DragonStats stats() { return CreatureConfig.FROST; }

    @Override protected ResourceKey<DamageType> breathDamageType() { return FROST_BREATH; }

    @Override protected Item scaleItem() { return ModItems.FROST_DRAGON_SCALE.get(); }

    @Override protected SoundEvent breathSound() { return ModSounds.FROST_DRAGON_BREATH.get(); }

    /** The breath stops at the surface of water and lava, so it can freeze them. */
    @Override protected ClipContext.Fluid breathBlockedBy() { return ClipContext.Fluid.SOURCE_ONLY; }

    /** A little higher-pitched than the Red Dragon. */
    @Override public float getVoicePitch() { return 1.12F + (random.nextFloat() - 0.5F) * 0.15F; }

    @Override
    protected void onBreathHit(LivingEntity target, boolean hurt) {
        int seconds = stats().effectSeconds.get();
        if (seconds > 0) {
            // no swirls: up close they cover the screen, and the frozen screen edge already shows it
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, seconds * 20, 1, false, false, true), this);
            // the same frozen screen and shivering as standing in powder snow; leather armor keeps it off
            if (target.canFreeze()) {
                int max = target.getTicksRequiredToFreeze() + 60;
                target.setTicksFrozen(Math.min(max, target.getTicksFrozen() + 30));
            }
        }
        if (target.isOnFire()) target.clearFire();
    }

    /** Freezes water and lava, puts out fire, and lays snow on the ground. */
    @Override
    protected void affectBlock(BlockPos pos) {
        Level level = level();
        for (int i = 0; i < 3 && level.isEmptyBlock(pos) && level.isEmptyBlock(pos.below()); i++) pos = pos.below();
        if (freeze(pos) || (level.isEmptyBlock(pos) && freeze(pos.below()))) return;
        if (!level.isEmptyBlock(pos)) pos = pos.above();          // landed inside the ground: snow goes on top
        BlockState snow = Blocks.SNOW.defaultBlockState();
        if (level.isEmptyBlock(pos) && snow.canSurvive(level, pos)) level.setBlockAndUpdate(pos, snow);
    }

    private boolean freeze(BlockPos pos) {
        Level level = level();
        BlockState state = level.getBlockState(pos);
        if (state.is(BlockTags.FIRE)) {
            level.removeBlock(pos, false);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.6F);
            return true;
        }
        if (!state.getFluidState().isSource()) return false;
        if (state.is(Blocks.WATER)) {
            level.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
            return true;
        }
        if (state.is(Blocks.LAVA)) {
            level.setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
            level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 2.2F + random.nextFloat() * 0.6F);
            if (level instanceof ServerLevel server)                // a puff of white steam
                server.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 3, 0.3, 0.1, 0.3, 0.02);
            return true;
        }
        return false;
    }

    @Override
    protected void breathParticles(Vec3 mouth, Vec3 dir, Vec3 aim) {
        for (int i = 0; i < 20; i++) {
            double speed = 0.6 + random.nextDouble() * 0.6;
            double spread = 0.1;
            Vec3 v = dir.add(random.nextGaussian() * spread, random.nextGaussian() * spread, random.nextGaussian() * spread).normalize().scale(speed);
            ParticleOptions p = i % 5 == 0 ? ParticleTypes.CLOUD : i % 7 == 0 ? ParticleTypes.END_ROD : ParticleTypes.SNOWFLAKE;
            level().addParticle(p, mouth.x, mouth.y, mouth.z, v.x, v.y, v.z);
        }
        // frost bursting out where the breath lands
        for (int i = 0; i < 5; i++) {
            level().addParticle(ParticleTypes.SNOWFLAKE, aim.x + random.nextGaussian() * 0.8, aim.y + random.nextDouble(), aim.z + random.nextGaussian() * 0.8,
                    random.nextGaussian() * 0.08, 0.05 + random.nextDouble() * 0.1, random.nextGaussian() * 0.08);
        }
    }
}
