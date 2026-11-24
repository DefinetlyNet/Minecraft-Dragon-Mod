package com.wildcreatures;

import com.wildcreatures.world.AshenRegion;
import com.wildcreatures.world.AshenSurface;
import com.wildcreatures.world.ModWorldgen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import terrablender.api.Regions;
import terrablender.api.SurfaceRuleManager;
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
                out.accept(ModItems.FROST_DRAGON_SPAWN_EGG.get());
                out.accept(ModItems.FROST_DRAGON_SCALE.get());
                out.accept(ModItems.LIGHTNING_DRAGON_SPAWN_EGG.get());
                out.accept(ModItems.LIGHTNING_DRAGON_SCALE.get());
                out.accept(ModItems.DRAGON_ESSENCE.get());
                out.accept(ModItems.DRAGON_SWORD.get());
                out.accept(ModItems.DRAGON_PICKAXE.get());
                out.accept(ModItems.DRAGON_AXE.get());
                out.accept(ModItems.DRAGON_SHOVEL.get());
                out.accept(ModItems.DRAGON_SHIELD.get());
                out.accept(ModItems.DRAGON_SCALE_HELMET.get());
                out.accept(ModItems.DRAGON_SCALE_CHESTPLATE.get());
                out.accept(ModItems.DRAGON_SCALE_LEGGINGS.get());
                out.accept(ModItems.DRAGON_SCALE_BOOTS.get());
                ModItems.ITEMS.getEntries().stream().map(i -> i.get())
                        .filter(i -> i instanceof net.minecraft.world.item.BlockItem || i == ModItems.BURNT_STICK.get())
                        .forEach(out::accept);
            })
            .build());

    public WildCreatures() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModWorldgen.FEATURES.register(modBus);
        ModWorldgen.STRUCTURE_TYPES.register(modBus);
        ModWorldgen.STRUCTURE_PIECES.register(modBus);
        modBus.addListener(this::commonSetup);
        ModSounds.SOUNDS.register(modBus);
        TABS.register(modBus);
        modBus.addListener(this::addToVanillaTabs);
        // Every number lives in config/wildcreatures-common.toml after the first launch.
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CreatureConfig.SPEC, MOD_ID + "-common.toml");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Ashen Wastes: our share of the Overworld (TerraBlender region) and its ground rules
            int weight = CreatureConfig.ASHEN_WASTES_WEIGHT.get();
            if (weight > 0) Regions.register(new AshenRegion(new ResourceLocation(MOD_ID, "overworld"), weight));
            SurfaceRuleManager.addSurfaceRules(SurfaceRuleManager.RuleCategory.OVERWORLD, MOD_ID, AshenSurface.rules());
        });
    }

    private void addToVanillaTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.RED_DRAGON_SPAWN_EGG);
            event.accept(ModItems.FROST_DRAGON_SPAWN_EGG);
            event.accept(ModItems.LIGHTNING_DRAGON_SPAWN_EGG);
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.RED_DRAGON_SCALE);
            event.accept(ModItems.FROST_DRAGON_SCALE);
            event.accept(ModItems.LIGHTNING_DRAGON_SCALE);
        }
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ModBlocks.BURNT_OAK_LOG);
            event.accept(ModBlocks.BURNT_OAK_WOOD);
            event.accept(ModBlocks.STRIPPED_BURNT_OAK_LOG);
            event.accept(ModBlocks.STRIPPED_BURNT_OAK_WOOD);
            event.accept(ModBlocks.BURNT_OAK_PLANKS);
            event.accept(ModBlocks.BURNT_OAK_STAIRS);
            event.accept(ModBlocks.BURNT_OAK_SLAB);
            event.accept(ModBlocks.BURNT_OAK_FENCE);
            event.accept(ModBlocks.BURNT_OAK_FENCE_GATE);
            event.accept(ModItems.BURNT_OAK_DOOR);
            event.accept(ModBlocks.BURNT_OAK_TRAPDOOR);
            event.accept(ModBlocks.BURNT_OAK_PRESSURE_PLATE);
            event.accept(ModBlocks.BURNT_OAK_BUTTON);
        }
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.BURNT_OAK_SIGN);
            event.accept(ModItems.BURNT_OAK_HANGING_SIGN);
        }
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(ModBlocks.ASHEN_SOIL);
            event.accept(ModBlocks.BURNT_OAK_SAPLING);
            event.accept(ModBlocks.BURNT_BUSH);
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.BURNT_STICK);
            event.accept(ModItems.DRAGON_ESSENCE);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.DRAGON_PICKAXE);
            event.accept(ModItems.DRAGON_AXE);
            event.accept(ModItems.DRAGON_SHOVEL);
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.DRAGON_SWORD);
            event.accept(ModItems.DRAGON_AXE);
            event.accept(ModItems.DRAGON_SHIELD);
            event.accept(ModItems.DRAGON_SCALE_HELMET);
            event.accept(ModItems.DRAGON_SCALE_CHESTPLATE);
            event.accept(ModItems.DRAGON_SCALE_LEGGINGS);
            event.accept(ModItems.DRAGON_SCALE_BOOTS);
        }
    }
}
