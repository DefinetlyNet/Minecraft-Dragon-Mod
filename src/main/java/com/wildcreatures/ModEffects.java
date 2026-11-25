package com.wildcreatures;

import com.wildcreatures.effect.UndertowEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Status effects the creatures give out. */
public final class ModEffects {
    private ModEffects() {}

    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, WildCreatures.MOD_ID);

    /** From the Sea Dragon's water blast: drags you down while you're in water. */
    public static final RegistryObject<MobEffect> UNDERTOW = EFFECTS.register("undertow", UndertowEffect::new);
}
