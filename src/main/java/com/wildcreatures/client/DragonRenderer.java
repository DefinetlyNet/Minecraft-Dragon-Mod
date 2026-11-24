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
 * Draws any of the winged dragons. Each kind passes its texture name: textures/entity/NAME.png for the body
 * and NAME_eyes.png for the glowing eyes.
 */
public class DragonRenderer<T extends AbstractDragon> extends MobRenderer<T, DragonModel<T>> {
    /** The shared winged dragon body (boxes from DragonLayer). */
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(WildCreatures.MOD_ID, "dragon"), "main");

    private final ResourceLocation texture;

    public DragonRenderer(EntityRendererProvider.Context ctx, String textureName) {
        super(ctx, new DragonModel<>(ctx.bakeLayer(LAYER)), 2.7F);
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
        // pitch the whole body a little with climbing / diving while flying
        float fly = dragon.getFlyAnim(partialTick);
        if (fly > 0) {
            float climb = (float) Mth.clamp(dragon.getDeltaMovement().y * 40.0, -25.0, 25.0);
            double pivot = 1.2 * dragon.getSizeScale();
            pose.translate(0, pivot, 0);
            pose.mulPose(Axis.XP.rotationDegrees(-climb * fly));
            pose.translate(0, -pivot, 0);
        }
    }
}
