package com.wildcreatures;

import com.wildcreatures.item.DragonShieldItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Game events for the dragon gear. */
@Mod.EventBusSubscriber(modid = WildCreatures.MOD_ID)
public final class GearEvents {
    private GearEvents() {}

    /** Blocking a hit with the Dragon Shield sets the attacker on fire. */
    @SubscribeEvent
    public static void onShieldBlock(ShieldBlockEvent event) {
        LivingEntity blocker = event.getEntity();
        if (!(blocker.getUseItem().getItem() instanceof DragonShieldItem)) return;
        Entity attacker = event.getDamageSource().getDirectEntity();
        // only creatures hitting you up close (not arrows or fireballs)
        if (!(attacker instanceof LivingEntity living) || attacker != event.getDamageSource().getEntity()) return;
        int seconds = CreatureConfig.SHIELD_BURN_SECONDS.get();
        if (seconds <= 0) return;
        living.setSecondsOnFire(seconds);
        if (blocker.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.FLAME, living.getX(), living.getY() + living.getBbHeight() * 0.6, living.getZ(),
                    18, living.getBbWidth() * 0.4, living.getBbHeight() * 0.3, living.getBbWidth() * 0.4, 0.03);
            level.playSound(null, blocker.getX(), blocker.getY(), blocker.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.7F, 1.2F);
        }
    }
}
