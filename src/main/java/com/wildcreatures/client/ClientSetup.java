package com.wildcreatures.client;

import com.wildcreatures.ModBlockEntities;
import com.wildcreatures.ModBlocks;
import com.wildcreatures.ModEntities;
import com.wildcreatures.ModItems;
import com.wildcreatures.entity.AbstractDragon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.HangingSignRenderer;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import com.wildcreatures.WildCreatures;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WildCreatures.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(DragonRenderer.LAYER, DragonLayer::create);
        event.registerLayerDefinition(DragonRenderer.SEA_LAYER, DragonLayer::createSea);
        event.registerLayerDefinition(DragonSaddleLayer.LAYER, DragonSaddleModel::createLayer);
        event.registerLayerDefinition(DragonArmorModels.HELMET, DragonArmorLayers::createHelmet);
        event.registerLayerDefinition(DragonShieldRenderer.LAYER, DragonShieldRenderer::createLayer);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.RED_DRAGON.get(), ctx -> new DragonRenderer<>(ctx, "red_dragon"));
        event.registerEntityRenderer(ModEntities.FROST_DRAGON.get(), ctx -> new DragonRenderer<>(ctx, "frost_dragon"));
        event.registerEntityRenderer(ModEntities.LIGHTNING_DRAGON.get(), ctx -> new DragonRenderer<>(ctx, "lightning_dragon"));
        event.registerEntityRenderer(ModEntities.SEA_DRAGON.get(), ctx -> new DragonRenderer<>(ctx, "sea_dragon", DragonRenderer.SEA_LAYER));
        event.registerEntityRenderer(ModEntities.BLACK_DRAGON.get(), ctx -> new DragonRenderer<>(ctx, "black_dragon"));
        event.registerBlockEntityRenderer(ModBlockEntities.SIGN.get(), SignRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.HANGING_SIGN.get(), HangingSignRenderer::new);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        // the dragon you ride reads the Shift key directly (the game clears a rider's Shift flag after every tick)
        AbstractDragon.holdingShift = player -> player.isLocalPlayer() ? Minecraft.getInstance().options.keyShift.isDown() : player.isShiftKeyDown();
        event.enqueueWork(() -> {
            Sheets.addWoodType(ModBlocks.BURNT_OAK_TYPE);
            // the shield model switches to its raised pose while blocking, like the vanilla shield
            ItemProperties.register(ModItems.DRAGON_SHIELD.get(), new ResourceLocation("blocking"),
                    (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
        });
    }
}
