package com.wildcreatures;

import com.wildcreatures.entity.AbstractDragon;
import com.wildcreatures.entity.BlackDragon;
import com.wildcreatures.entity.FrostDragon;
import com.wildcreatures.entity.LightningDragon;
import com.wildcreatures.entity.RedDragon;
import com.wildcreatures.entity.SeaDragon;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
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

    public static final RegistryObject<EntityType<RedDragon>> RED_DRAGON = dragon("red_dragon", RedDragon::new, true);
    public static final RegistryObject<EntityType<FrostDragon>> FROST_DRAGON = dragon("frost_dragon", FrostDragon::new, false);
    public static final RegistryObject<EntityType<LightningDragon>> LIGHTNING_DRAGON = dragon("lightning_dragon", LightningDragon::new, false);
    public static final RegistryObject<EntityType<SeaDragon>> SEA_DRAGON = dragon("sea_dragon", SeaDragon::new, false);
    /** Friendly, so it counts as a creature rather than a monster. */
    public static final RegistryObject<EntityType<BlackDragon>> BLACK_DRAGON = dragon("black_dragon", BlackDragon::new, false, MobCategory.CREATURE);

    /** Registers one kind of dragon. They all share the same body size; fire-proof ones don't burn in fire or lava. */
    private static <T extends AbstractDragon> RegistryObject<EntityType<T>> dragon(String name, EntityType.EntityFactory<T> factory, boolean fireProof) {
        return dragon(name, factory, fireProof, MobCategory.MONSTER);
    }

    private static <T extends AbstractDragon> RegistryObject<EntityType<T>> dragon(String name, EntityType.EntityFactory<T> factory, boolean fireProof,
                                                                                   MobCategory category) {
        return ENTITIES.register(name, () -> {
            EntityType.Builder<T> builder = EntityType.Builder.of(factory, category)
                    .sized(3.1F, 3.1F)
                    .clientTrackingRange(10);
            if (fireProof) builder.fireImmune();
            return builder.build(name);
        });
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(RED_DRAGON.get(), AbstractDragon.createAttributes().build());
        event.put(FROST_DRAGON.get(), AbstractDragon.createAttributes().build());
        event.put(LIGHTNING_DRAGON.get(), AbstractDragon.createAttributes().build());
        event.put(SEA_DRAGON.get(), AbstractDragon.createAttributes().build());
        event.put(BLACK_DRAGON.get(), AbstractDragon.createAttributes().build());
    }

    @SubscribeEvent
    public static void spawnRules(SpawnPlacementRegisterEvent event) {
        event.register(RED_DRAGON.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                AbstractDragon.spawnRules(CreatureConfig.RED), SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(FROST_DRAGON.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                AbstractDragon.spawnRules(CreatureConfig.FROST), SpawnPlacementRegisterEvent.Operation.REPLACE);
        // much more likely to turn up during a thunderstorm
        event.register(LIGHTNING_DRAGON.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                AbstractDragon.spawnRules(CreatureConfig.LIGHTNING, 4), SpawnPlacementRegisterEvent.Operation.REPLACE);
        // its natural spawns come from world/SeaDragonSpawner; these rules cover mob spawners and the like
        event.register(SEA_DRAGON.get(), SpawnPlacements.Type.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SeaDragon.spawnRules(), SpawnPlacementRegisterEvent.Operation.REPLACE);
        // its natural spawns come from world/BlackDragonSpawner; these rules cover mob spawners and the like
        event.register(BLACK_DRAGON.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mob::checkMobSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }
}
