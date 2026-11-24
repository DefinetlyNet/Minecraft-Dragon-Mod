package com.wildcreatures.client;

import com.wildcreatures.entity.RedDragon;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Pose and animation for the Red Dragon. The boxes themselves come from RedDragonLayer (generated). */
public class RedDragonModel extends HierarchicalModel<RedDragon> {
    private final ModelPart root, body, neck1, neck2, head, jaw;
    private final ModelPart tail1, tail2, tail3, tail4;
    private final ModelPart[] legs = new ModelPart[4], shins = new ModelPart[4];
    private final ModelPart wingR, wingL, wingTipR, wingTipL;

    public RedDragonModel(ModelPart root) {
        this.root = root;
        body = root.getChild("body");
        neck1 = body.getChild("neck1");
        neck2 = neck1.getChild("neck2");
        head = neck2.getChild("head");
        jaw = head.getChild("jaw");
        tail1 = body.getChild("tail1");
        tail2 = tail1.getChild("tail2");
        tail3 = tail2.getChild("tail3");
        tail4 = tail3.getChild("tail4");
        String[] names = {"front_leg_r", "front_leg_l", "hind_leg_r", "hind_leg_l"};
        String[] shinNames = {"front_shin_r", "front_shin_l", "hind_shin_r", "hind_shin_l"};
        for (int i = 0; i < 4; i++) {
            legs[i] = body.getChild(names[i]);
            shins[i] = legs[i].getChild(shinNames[i]);
        }
        wingR = body.getChild("wing_r");
        wingL = body.getChild("wing_l");
        wingTipR = wingR.getChild("wing_tip_r");
        wingTipL = wingL.getChild("wing_tip_l");
    }

    @Override
    public ModelPart root() { return root; }

    @Override
    public void setupAnim(RedDragon dragon, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float pt = age - dragon.tickCount;
        float fly = dragon.getFlyAnim(pt);
        float breath = dragon.getBreathAnim(pt);
        float bite = dragon.getBiteAnim(pt);
        float ground = 1.0F - fly;

        // ---- head and neck: looking around, spread over the neck so it bends smoothly
        float yaw = netHeadYaw * Mth.DEG_TO_RAD;
        float pitch = headPitch * Mth.DEG_TO_RAD;
        neck1.yRot += yaw * 0.3F;
        neck2.yRot += yaw * 0.3F;
        head.yRot += yaw * 0.4F;
        head.xRot += pitch;

        // ---- idle breathing
        float idle = Mth.sin(age * 0.08F);
        body.y += idle * 0.25F * ground;
        neck1.xRot += idle * 0.03F;
        jaw.xRot += (idle * 0.5F + 0.5F) * 0.05F;

        // ---- bite: lunge forward, jaws open wide then snap shut
        neck1.xRot += bite * 0.35F;
        head.xRot -= bite * 0.25F;
        jaw.xRot += bite * 0.95F;

        // ---- fire breath: jaw wide open, a slight shudder
        jaw.xRot += breath * 0.7F;
        head.zRot += breath * Mth.sin(age * 1.7F) * 0.03F;

        // ---- walking: diagonal pairs, heavy slow gait
        float w = limbSwing * 0.45F;
        float amt = Math.min(limbSwingAmount, 1.0F) * ground;
        float[] phase = {0, Mth.PI, Mth.PI, 0};
        for (int i = 0; i < 4; i++) {
            float swing = Mth.cos(w + phase[i]);
            legs[i].xRot += swing * 0.7F * amt;
            shins[i].xRot += Math.max(0.0F, -Mth.sin(w + phase[i])) * 0.6F * amt;
        }
        body.zRot += Mth.cos(w) * 0.03F * amt;

        // ---- flying: legs tucked back, neck stretched forward, tail straight
        for (int i = 0; i < 4; i++) {
            legs[i].xRot += fly * (i < 2 ? 0.7F : 1.1F);
            shins[i].xRot += fly * 0.5F;
        }
        neck1.xRot += fly * 0.35F;
        head.xRot -= fly * 0.3F;
        tail1.xRot += fly * 0.2F;
        tail2.xRot += fly * 0.1F;

        // ---- tail sway
        float sway = 0.12F + 0.06F * fly;
        tail1.yRot += Mth.sin(age * 0.1F) * sway;
        tail2.yRot += Mth.sin(age * 0.1F - 0.7F) * sway * 1.2F;
        tail3.yRot += Mth.sin(age * 0.1F - 1.4F) * sway * 1.4F;
        tail4.yRot += Mth.sin(age * 0.1F - 2.1F) * sway * 1.6F;
        tail1.yRot -= yaw * 0.15F;

        // ---- wings: folded along the back on the ground, big flaps in the air
        float flap = Mth.sin(age * 0.3F);
        float flapTip = Mth.sin(age * 0.3F - 0.9F);
        float fold = Mth.sin(age * 0.08F) * 0.03F;
        float rZ = Mth.lerp(fly, -0.55F + fold, -0.1F + flap * 0.75F);
        float rY = Mth.lerp(fly, -0.95F, 0.0F);
        float tipZ = Mth.lerp(fly, 0.75F, 0.2F + flapTip * 0.55F);
        float tipY = Mth.lerp(fly, -1.6F, 0.0F);
        wingR.zRot = rZ;
        wingR.yRot = rY;
        wingTipR.zRot = tipZ;
        wingTipR.yRot = tipY;
        wingL.zRot = -rZ;
        wingL.yRot = -rY;
        wingTipL.zRot = -tipZ;
        wingTipL.yRot = -tipY;
        // a half spread when breathing fire on the ground looks menacing
        wingR.zRot -= breath * ground * 0.4F;
        wingL.zRot += breath * ground * 0.4F;
        wingR.yRot += breath * ground * 0.6F;
        wingL.yRot -= breath * ground * 0.6F;
    }
}
