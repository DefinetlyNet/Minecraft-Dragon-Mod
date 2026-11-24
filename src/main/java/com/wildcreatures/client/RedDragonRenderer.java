package com.wildcreatures.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wildcreatures.WildCreatures;
import com.wildcreatures.entity.RedDragon;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class RedDragonRenderer extends MobRenderer<RedDragon, RedDragonModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(WildCreatures.MOD_ID, "red_dragon"), "main");
    private static final ResourceLocation TEXTURE = new ResourceLocation(WildCreatures.MOD_ID, "textures/entity/red_dragon.png");
    private static final RenderType EYES = RenderType.eyes(new ResourceLocation(WildCreatures.MOD_ID, "textures/entity/red_dragon_eyes.png"));

    public RedDragonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new RedDragonModel(ctx.bakeLayer(LAYER)), 2.7F);
        addLayer(new EyesLayer<>(this) {
            @Override public RenderType renderType() { return EYES; }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(RedDragon dragon) { return TEXTURE; }

    @Override
    protected void scale(RedDragon dragon, PoseStack pose, float partialTick) {
        float s = RedDragon.MODEL_SCALE * dragon.getSizeScale();
        pose.scale(s, s, s);
        shadowRadius = 2.7F * dragon.getSizeScale();
    }

    @Override
    protected void setupRotations(RedDragon dragon, PoseStack pose, float age, float bodyYaw, float partialTick) {
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
