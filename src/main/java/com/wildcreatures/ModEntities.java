package com.wildcreatures;

import com.wildcreatures.entity.RedDragon;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = WildCreatures.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, WildCreatures.MOD_ID);

    public static final RegistryObject<EntityType<RedDragon>> RED_DRAGON = ENTITIES.register("red_dragon",
            () -> EntityType.Builder.of(RedDragon::new, MobCategory.MONSTER)
                    .sized(3.1F, 3.1F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .build("red_dragon"));

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(RED_DRAGON.get(), RedDragon.createAttributes().build());
    }

    @SubscribeEvent
    public static void spawnRules(SpawnPlacementRegisterEvent event) {
        event.register(RED_DRAGON.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                RedDragon::checkDragonSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }
}
