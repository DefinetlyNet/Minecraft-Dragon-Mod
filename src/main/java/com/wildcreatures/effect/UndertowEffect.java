package com.wildcreatures.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Undertow, from the Sea Dragon's water blast: while you're in water it drags you down, so getting back up
 * takes hard swimming (holding jump only slows the sinking). It does nothing on land.
 * Players move themselves, so for a player the pull runs in their own game, the same way Levitation works.
 */
public class UndertowEffect extends MobEffect {
    /** Extra downward speed every tick in water (swimming up only gives back 0.04). Higher levels pull harder. */
    private static final double PULL = 0.05;
    /** It never drags anything down faster than this, in blocks per tick. */
    private static final double MAX_SINK = 0.3;

    public UndertowEffect() { super(MobEffectCategory.HARMFUL, 0x1D4E73); }

    @Override public boolean isDurationEffectTick(int duration, int amplifier) { return true; }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.isInWater()) return;
        boolean client = entity.level().isClientSide;
        boolean movesHere = entity instanceof Player player ? client && player.isLocalPlayer() : !client;
        if (movesHere) {
            Vec3 v = entity.getDeltaMovement();
            if (v.y > -MAX_SINK) entity.setDeltaMovement(v.x, Math.max(v.y - PULL * (amplifier + 1), -MAX_SINK), v.z);
        }
        // a trail of bubbles streaming up off whatever is being dragged under
        if (entity.level() instanceof ServerLevel server && entity.tickCount % 3 == 0) {
            server.sendParticles(ParticleTypes.BUBBLE, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(), 2,
                    entity.getBbWidth() * 0.4, entity.getBbHeight() * 0.3, entity.getBbWidth() * 0.4, 0.0);
        }
    }
}
