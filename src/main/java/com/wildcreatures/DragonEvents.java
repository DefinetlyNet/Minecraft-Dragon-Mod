package com.wildcreatures;

import com.wildcreatures.entity.AbstractDragon;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Iron and snow golems attack every monster, dragons included. They leave friendly dragons alone unless the dragon hurt them first. */
@Mod.EventBusSubscriber(modid = WildCreatures.MOD_ID)
public final class DragonEvents {
    private DragonEvents() {}

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof AbstractDragon dragon && dragon.isFriendly()
                && event.getEntity() instanceof AbstractGolem golem && golem.getLastHurtByMob() != dragon) {
            event.setCanceled(true);
        }
    }
}
