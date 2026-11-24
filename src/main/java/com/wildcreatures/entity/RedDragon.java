package com.wildcreatures.entity;

import com.wildcreatures.CreatureConfig;
import com.wildcreatures.DragonStats;
import com.wildcreatures.ModItems;
import com.wildcreatures.WildCreatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;

/**
 * A big, hostile red dragon from the Ashen Wastes. Walks on the ground, takes off when its target is far away,
 * high up or out of reach, bites hard up close and breathes a stream of fire from range.
 */
public class RedDragon extends AbstractDragon {
    public static final ResourceKey<DamageType> DRAGON_FIRE =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(WildCreatures.MOD_ID, "dragon_fire"));

    public RedDragon(EntityType<? extends RedDragon> type, Level level) {
        super(type, level);
        // fire can't hurt it, so it walks straight through flames and doesn't mind lava much
        setPathfindingMalus(BlockPathTypes.DANGER_FIRE, 0.0F);
        setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, 0.0F);
        setPathfindingMalus(BlockPathTypes.LAVA, 8.0F);
    }

    @Override public DragonStats stats() { return CreatureConfig.RED; }

    @Override protected ResourceKey<DamageType> breathDamageType() { return DRAGON_FIRE; }

    @Override protected Item scaleItem() { return ModItems.RED_DRAGON_SCALE.get(); }

    @Override
    protected void onBreathHit(LivingEntity target, boolean hurt) {
        int burn = stats().effectSeconds.get();
        if (burn > 0) target.setSecondsOnFire(burn);
    }

    /** Sets fire on the first spot with ground under it. */
    @Override
    protected void affectBlock(BlockPos pos) {
        for (int i = 0; i < 3 && level().isEmptyBlock(pos.below()); i++) pos = pos.below();
        if (!level().isEmptyBlock(pos)) pos = pos.above();
        if (level().isEmptyBlock(pos) && BaseFireBlock.canBePlacedAt(level(), pos, Direction.UP)) {
            BlockState fire = BaseFireBlock.getState(level(), pos);
            level().setBlock(pos, fire, 11);
        }
    }

    @Override
    protected void breathParticles(Vec3 mouth, Vec3 dir, Vec3 aim) {
        for (int i = 0; i < 22; i++) {
            double speed = 0.7 + random.nextDouble() * 0.6;
            double spread = 0.1;
            Vec3 v = dir.add(random.nextGaussian() * spread, random.nextGaussian() * spread, random.nextGaussian() * spread).normalize().scale(speed);
            level().addParticle(i % 11 == 0 ? ParticleTypes.SMOKE : (i % 3 == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME),
                    mouth.x, mouth.y, mouth.z, v.x, v.y, v.z);
        }
        // flames bursting out where the breath lands
        for (int i = 0; i < 4; i++) {
            level().addParticle(ParticleTypes.FLAME, aim.x + random.nextGaussian() * 0.8, aim.y + random.nextDouble(), aim.z + random.nextGaussian() * 0.8,
                    random.nextGaussian() * 0.06, 0.05 + random.nextDouble() * 0.1, random.nextGaussian() * 0.06);
        }
        if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.LAVA, mouth.x, mouth.y, mouth.z, 0, 0, 0);
    }
}
