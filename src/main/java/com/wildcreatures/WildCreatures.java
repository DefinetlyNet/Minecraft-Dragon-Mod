package com.wildcreatures;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Wild Creatures: a creature mod built to grow. Each new mob gets its own entity class in
 * the entity package, a registry line in ModEntities, a renderer in the client package and
 * a section in CreatureConfig.
 */
@Mod(WildCreatures.MOD_ID)
public class WildCreatures {
    public static final String MOD_ID = "wildcreatures";

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("creatures", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.wildcreatures.creatures"))
            .icon(() -> new ItemStack(ModItems.RED_DRAGON_SPAWN_EGG.get()))
            .displayItems((params, out) -> {
                out.accept(ModItems.RED_DRAGON_SPAWN_EGG.get());
                out.accept(ModItems.RED_DRAGON_SCALE.get());
                out.accept(ModItems.DRAGON_SCALE_HELMET.get());
                out.accept(ModItems.DRAGON_SCALE_CHESTPLATE.get());
                out.accept(ModItems.DRAGON_SCALE_LEGGINGS.get());
                out.accept(ModItems.DRAGON_SCALE_BOOTS.get());
            })
            .build());

    public WildCreatures() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        TABS.register(modBus);
        modBus.addListener(this::addToVanillaTabs);
        // Every number lives in config/wildcreatures-common.toml after the first launch.
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CreatureConfig.SPEC, MOD_ID + "-common.toml");
    }

    private void addToVanillaTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(ModItems.RED_DRAGON_SPAWN_EGG);
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) event.accept(ModItems.RED_DRAGON_SCALE);
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.DRAGON_SCALE_HELMET);
            event.accept(ModItems.DRAGON_SCALE_CHESTPLATE);
            event.accept(ModItems.DRAGON_SCALE_LEGGINGS);
            event.accept(ModItems.DRAGON_SCALE_BOOTS);
        }
    }
}
