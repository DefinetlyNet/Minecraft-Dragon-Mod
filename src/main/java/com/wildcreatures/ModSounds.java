package com.wildcreatures;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The dragons' sounds. Every kind shares the red_dragon ones except its breath. The .ogg files are made by tools/make_sounds.py. */
public final class ModSounds {
    private ModSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, WildCreatures.MOD_ID);

    public static final RegistryObject<SoundEvent> DRAGON_ROAR = register("red_dragon.roar");
    public static final RegistryObject<SoundEvent> DRAGON_HURT = register("red_dragon.hurt");
    public static final RegistryObject<SoundEvent> DRAGON_DEATH = register("red_dragon.death");
    public static final RegistryObject<SoundEvent> DRAGON_BITE = register("red_dragon.bite");
    public static final RegistryObject<SoundEvent> DRAGON_BREATH = register("red_dragon.breath");
    public static final RegistryObject<SoundEvent> DRAGON_FLAP = register("red_dragon.flap");
    public static final RegistryObject<SoundEvent> DRAGON_STEP = register("red_dragon.step");
    public static final RegistryObject<SoundEvent> FROST_DRAGON_BREATH = register("frost_dragon.breath");
    public static final RegistryObject<SoundEvent> LIGHTNING_DRAGON_CALL = register("lightning_dragon.call");
    public static final RegistryObject<SoundEvent> LIGHTNING_DRAGON_SPARK = register("lightning_dragon.spark");
    public static final RegistryObject<SoundEvent> SEA_DRAGON_BLAST = register("sea_dragon.blast");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(WildCreatures.MOD_ID, name)));
    }
}
