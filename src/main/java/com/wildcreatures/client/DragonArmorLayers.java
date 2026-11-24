package com.wildcreatures.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/**
 * The Dragon Scale helmet: a normal helmet (same box and texture layout as vanilla armor)
 * plus two small curved horns. Horn textures sit in the unused hat area of dragon_scale_layer_1.png.
 */
public final class DragonArmorLayers {
    private DragonArmorLayers() {}

    public static LayerDefinition createHelmet() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(1.0F)), PartPose.ZERO);
        horn(head, "right", 1, 32, 48);
        horn(head, "left", -1, 40, 54);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** A small horn rising from the top of the helmet and curling back. side = +1 or -1. */
    private static void horn(PartDefinition head, String name, int side, int baseU, int tipU) {
        PartDefinition base = head.addOrReplaceChild("horn_" + name, CubeListBuilder.create()
                        .texOffs(baseU, 0).addBox(-0.8F, -2.6F, -0.8F, 1.6F, 2.6F, 1.6F),
                PartPose.offsetAndRotation(2.6F * side, -8.8F, -0.5F, -0.35F, 0.0F, 0.35F * side));
        base.addOrReplaceChild("horn_tip_" + name, CubeListBuilder.create()
                        .texOffs(tipU, 0).addBox(-0.5F, -2.4F, -0.5F, 1.0F, 2.4F, 1.0F),
                PartPose.offsetAndRotation(0.0F, -2.4F, 0.0F, -0.7F, 0.0F, -0.2F * side));
    }
}
