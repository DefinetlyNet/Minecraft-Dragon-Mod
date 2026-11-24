package com.wildcreatures.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wildcreatures.WildCreatures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the Dragon Shield: the vanilla shield shape plus a raised dragon-eye boss and two small
 * bone horns on top, with glowing parts (the eye and ember cracks) drawn full-bright.
 */
public class DragonShieldRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(WildCreatures.MOD_ID, "dragon_shield"), "main");
    private static final ResourceLocation TEXTURE = new ResourceLocation(WildCreatures.MOD_ID, "textures/entity/dragon_shield.png");
    private static final ResourceLocation GLOW = new ResourceLocation(WildCreatures.MOD_ID, "textures/entity/dragon_shield_glow.png");
    private static DragonShieldRenderer instance;
    private ModelPart model;

    public static DragonShieldRenderer get() {
        if (instance == null) instance = new DragonShieldRenderer();
        return instance;
    }

    private DragonShieldRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("plate", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -11.0F, -2.0F, 12.0F, 22.0F, 1.0F), PartPose.ZERO);
        root.addOrReplaceChild("handle", CubeListBuilder.create().texOffs(26, 0).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 6.0F, 6.0F), PartPose.ZERO);
        root.addOrReplaceChild("boss", CubeListBuilder.create().texOffs(0, 24).addBox(-3.0F, -4.0F, -3.0F, 6.0F, 8.0F, 1.0F), PartPose.ZERO);
        // two curved horns on top: a thick bone base with a thinner dark tip that bends further out and back
        PartDefinition hornLeft = root.addOrReplaceChild("horn_left", CubeListBuilder.create().texOffs(16, 24).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(-4.5F, -10.5F, -1.5F, 0.0F, 0.0F, -0.3F));
        hornLeft.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(16, 28).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 1.5F, 1.0F),
                PartPose.offsetAndRotation(0.0F, -1.9F, 0.0F, -0.25F, 0.0F, -0.45F));
        PartDefinition hornRight = root.addOrReplaceChild("horn_right", CubeListBuilder.create().texOffs(24, 24).addBox(-1.0F, -2.0F, -1.0F, 2.0F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(4.5F, -10.5F, -1.5F, 0.0F, 0.0F, 0.3F));
        hornRight.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(24, 28).addBox(-0.5F, -1.5F, -0.5F, 1.0F, 1.5F, 1.0F),
                PartPose.offsetAndRotation(0.0F, -1.9F, 0.0F, -0.25F, 0.0F, 0.45F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        model = null;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (model == null) model = Minecraft.getInstance().getEntityModels().bakeLayer(LAYER);
        pose.pushPose();
        pose.scale(1.0F, -1.0F, -1.0F);
        VertexConsumer body = ItemRenderer.getFoilBufferDirect(buffers, RenderType.entityCutoutNoCull(TEXTURE), true, stack.hasFoil());
        model.render(pose, body, light, overlay);
        model.render(pose, buffers.getBuffer(RenderType.eyes(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}
