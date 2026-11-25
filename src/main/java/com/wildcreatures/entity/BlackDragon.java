package com.wildcreatures.entity;

import com.wildcreatures.CreatureConfig;
import com.wildcreatures.DragonStats;
import com.wildcreatures.ModItems;
import com.wildcreatures.ModSounds;
import com.wildcreatures.WildCreatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The rarest dragon: jet black with a violet sheen, found alone on the highest mountain peaks. It's friendly. It leaves
 * you alone unless you hurt it, and you can tame it by feeding it meat or fish, saddle it and fly it. Its breath is
 * shadow flame, a stream of black fire that withers whatever it hits (and the grass and flowers where it lands).
 */
public class BlackDragon extends AbstractDragon {
    public static final ResourceKey<DamageType> SHADOW_FLAME =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(WildCreatures.MOD_ID, "shadow_flame"));

    /** A deep violet-black haze, and bright violet embers. */
    private static final DustParticleOptions SHADOW = new DustParticleOptions(new Vector3f(0.14F, 0.04F, 0.24F), 2.2F);
    private static final DustParticleOptions EMBER = new DustParticleOptions(new Vector3f(0.62F, 0.3F, 1.0F), 1.1F);

    public BlackDragon(EntityType<? extends BlackDragon> type, Level level) {
        super(type, level);
    }

    @Override public DragonStats stats() { return CreatureConfig.BLACK; }

    @Override protected ResourceKey<DamageType> breathDamageType() { return SHADOW_FLAME; }

    @Override protected Item scaleItem() { return ModItems.BLACK_DRAGON_SCALE.get(); }

    @Override protected SoundEvent breathSound() { return ModSounds.BLACK_DRAGON_BREATH.get(); }

    @Override protected boolean tamedByFeeding() { return true; }

    @Override protected boolean huntsOnSight() { return false; }

    /** A deeper voice than the others. */
    @Override public float getVoicePitch() { return 0.84F + (random.nextFloat() - 0.5F) * 0.12F; }

    /** Its own shadow can't wither it. */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return effect.getEffect() != MobEffects.WITHER && super.canBeAffected(effect);
    }

    /** Wither II for a few seconds (wither skeletons and the Wither itself shrug that part off, but still take the damage). */
    @Override
    protected void onBreathHit(LivingEntity target, boolean hurt) {
        int seconds = stats().effectSeconds.get();
        if (hurt && seconds > 0) target.addEffect(new MobEffectInstance(MobEffects.WITHER, seconds * 20, 1), this);
    }

    /** Withers grass and flowers: plants crumble away (some leave a dead bush) and grass turns to bare dirt. */
    @Override
    protected void affectBlock(BlockPos pos) {
        Level level = level();
        for (int i = 0; i < 3 && level.isEmptyBlock(pos) && level.isEmptyBlock(pos.below()); i++) pos = pos.below();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            witherGround(pos.below());
        } else if (isPlant(state)) {
            level.destroyBlock(pos, false);
            witherGround(pos.below());
            BlockState bush = Blocks.DEAD_BUSH.defaultBlockState();
            if (random.nextInt(4) == 0 && level.isEmptyBlock(pos) && bush.canSurvive(level, pos)) level.setBlockAndUpdate(pos, bush);
        } else {
            witherGround(pos);
        }
    }

    private static boolean isPlant(BlockState state) {
        return state.is(BlockTags.SMALL_FLOWERS) || state.is(BlockTags.TALL_FLOWERS) || state.is(Blocks.GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN);
    }

    private void witherGround(BlockPos pos) {
        BlockState state = level().getBlockState(pos);
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.MOSS_BLOCK))
            level().setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
    }

    /** Client side: black flames and violet sparks pouring from its jaws, a dark haze along the stream, and black fire where it lands. */
    @Override
    protected void breathParticles(Vec3 mouth, Vec3 dir, Vec3 aim) {
        Level level = level();
        for (int i = 0; i < 20; i++) {
            double speed = 0.7 + random.nextDouble() * 0.6;
            double spread = 0.1;
            Vec3 v = dir.add(random.nextGaussian() * spread, random.nextGaussian() * spread, random.nextGaussian() * spread).normalize().scale(speed);
            if (i % 4 == 0) {
                v = v.scale(0.5);                           // these drift further, so send them out slower
                level.addParticle(ParticleTypes.DRAGON_BREATH, mouth.x, mouth.y, mouth.z, v.x, v.y, v.z);
            } else {
                level.addParticle(i % 7 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SQUID_INK, mouth.x, mouth.y, mouth.z, v.x, v.y, v.z);
            }
        }
        double length = Math.max(1.0, aim.distanceTo(mouth));
        for (int i = 0; i < 12; i++) {
            double t = random.nextDouble();
            double r = 0.25 * (0.4 + t);
            level.addParticle(i % 3 == 0 ? EMBER : SHADOW, mouth.x + dir.x * t * length + random.nextGaussian() * r,
                    mouth.y + dir.y * t * length + random.nextGaussian() * r, mouth.z + dir.z * t * length + random.nextGaussian() * r, 0, 0, 0);
        }
        // black fire licking up where it lands
        for (int i = 0; i < 6; i++) {
            level.addParticle(i % 3 == 0 ? ParticleTypes.WITCH : ParticleTypes.SQUID_INK, aim.x + random.nextGaussian() * 0.8, aim.y + random.nextDouble(),
                    aim.z + random.nextGaussian() * 0.8, random.nextGaussian() * 0.05, 0.06 + random.nextDouble() * 0.1, random.nextGaussian() * 0.05);
        }
    }
}
