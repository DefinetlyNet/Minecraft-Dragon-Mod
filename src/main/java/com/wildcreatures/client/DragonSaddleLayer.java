package com.wildcreatures.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wildcreatures.WildCreatures;
import com.wildcreatures.entity.AbstractDragon;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/** Draws the saddle on a saddled dragon's back, moving with the body. */
public class DragonSaddleLayer<T extends AbstractDragon> extends RenderLayer<T, DragonModel<T>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(WildCreatures.MOD_ID, "dragon_saddle"), "main");
    private static final ResourceLocation TEXTURE = new ResourceLocation(WildCreatures.MOD_ID, "textures/entity/dragon_saddle.png");

    private final ModelPart saddle;

    public DragonSaddleLayer(RenderLayerParent<T, DragonModel<T>> parent, EntityModelSet models) {
        super(parent);
        saddle = models.bakeLayer(LAYER);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T dragon, float limbSwing, float limbSwingAmount,
                       float partialTick, float age, float headYaw, float headPitch) {
        if (!dragon.isSaddled() || dragon.isInvisible()) return;
        pose.pushPose();
        getParentModel().root().translateAndRotate(pose);
        getParentModel().body().translateAndRotate(pose);
        saddle.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, LivingEntityRenderer.getOverlayCoords(dragon, 0.0F));
        pose.popPose();
    }
}
