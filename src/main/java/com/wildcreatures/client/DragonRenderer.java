package com.wildcreatures.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wildcreatures.WildCreatures;
import com.wildcreatures.entity.AbstractDragon;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws any of the dragons. Each kind passes its texture name: textures/entity/NAME.png for the body
 * and NAME_eyes.png for the glowing eyes, and which body it uses (the winged one unless it says otherwise).
 */
public class DragonRenderer<T extends AbstractDragon> extends MobRenderer<T, DragonModel<T>> {
    /** The shared winged dragon body (boxes from DragonLayer). */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(WildCreatures.MOD_ID, "dragon"), "main");
    /** The Sea Dragon's finned body (DragonLayer.createSea). */
    public static final ModelLayerLocation SEA_LAYER = new ModelLayerLocation(new ResourceLocation(WildCreatures.MOD_ID, "sea_dragon"), "main");

    private final ResourceLocation texture;

    public DragonRenderer(EntityRendererProvider.Context ctx, String textureName) {
        this(ctx, textureName, LAYER);
    }

    public DragonRenderer(EntityRendererProvider.Context ctx, String textureName, ModelLayerLocation body) {
        super(ctx, new DragonModel<>(ctx.bakeLayer(body)), 2.7F);
        texture = new ResourceLocation(WildCreatures.MOD_ID, "textures/entity/" + textureName + ".png");
        RenderType eyes = RenderType.eyes(new ResourceLocation(WildCreatures.MOD_ID, "textures/entity/" + textureName + "_eyes.png"));
        addLayer(new EyesLayer<>(this) {
            @Override public RenderType renderType() { return eyes; }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(T dragon) { return texture; }

    @Override
    protected void scale(T dragon, PoseStack pose, float partialTick) {
        float s = AbstractDragon.MODEL_SCALE * dragon.getSizeScale();
        pose.scale(s, s, s);
        shadowRadius = 2.7F * dragon.getSizeScale();
    }

    @Override
    protected void setupRotations(T dragon, PoseStack pose, float age, float bodyYaw, float partialTick) {
        super.setupRotations(dragon, pose, age, bodyYaw, partialTick);
        // pitch the whole body a little with climbing / diving while flying (a positive X turn tips the nose up)
        float fly = dragon.getFlyAnim(partialTick);
        if (fly > 0) {
            float climb = (float) Mth.clamp(dragon.getDeltaMovement().y * 40.0, -25.0, 25.0);
            double pivot = 1.2 * dragon.getSizeScale();
            pose.translate(0, pivot, 0);
            pose.mulPose(Axis.XP.rotationDegrees(climb * fly));
            pose.translate(0, -pivot, 0);
        }
        // and while swimming, along the way it's heading: nose up to rise, nose down to dive
        float swim = dragon.getSwimAnim(partialTick);
        if (swim > 0) {
            double pivot = 1.2 * dragon.getSizeScale();
            pose.translate(0, pivot, 0);
            pose.mulPose(Axis.XP.rotationDegrees(dragon.getSwimPitch(partialTick) * swim));
            pose.translate(0, -pivot, 0);
        }
    }
}
