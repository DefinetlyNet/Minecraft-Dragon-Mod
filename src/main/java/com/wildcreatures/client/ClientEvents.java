package com.wildcreatures.client;

import com.wildcreatures.WildCreatures;
import com.wildcreatures.entity.AbstractDragon;
import com.wildcreatures.network.ModNetwork;
import com.wildcreatures.network.RiderBreathPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Riding a dragon: the attack key breathes instead of punching, getting on shows the controls, and the third-person
 * camera pulls further back so it isn't stuck inside the dragon.
 */
@Mod.EventBusSubscriber(modid = WildCreatures.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    private static int lastBreathTick = -100, ridingTicks;
    private static Entity lastVehicle;
    /** The view you had before getting on a dragon, to go back to when you get off. */
    @Nullable private static CameraType viewBeforeRiding;

    /** Camera.getMaxZoom and Camera.move, which aren't public. */
    @Nullable private static final Method MAX_ZOOM = cameraMethod("m_90566_", double.class);
    @Nullable private static final Method MOVE = cameraMethod("m_90568_", double.class, double.class, double.class);

    @Nullable
    private static Method cameraMethod(String name, Class<?>... args) {
        try {
            return ObfuscationReflectionHelper.findMethod(Camera.class, name, args);
        } catch (RuntimeException e) {
            WildCreatures.LOGGER.warn("Can't move the camera back while riding a dragon", e);
            return null;
        }
    }

    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !(player.getVehicle() instanceof AbstractDragon dragon) || dragon.getControllingPassenger() != player) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (Math.abs(player.tickCount - lastBreathTick) >= 5) {
            lastBreathTick = player.tickCount;
            ModNetwork.CHANNEL.sendToServer(new RiderBreathPacket());
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        Entity vehicle = mc.player == null ? null : mc.player.getVehicle();
        ridingTicks = vehicle != null && vehicle == lastVehicle ? ridingTicks + 1 : 0;
        lastVehicle = vehicle;
        boolean riding = vehicle instanceof AbstractDragon dragon && dragon.getControllingPassenger() == mc.player;
        // replaces "Press Shift to dismount" with the dragon's controls, and keeps them up for a few seconds
        if (riding && ridingTicks <= 100 && ridingTicks % 40 == 0) {
            mc.gui.setOverlayMessage(Component.translatable("message.wildcreatures.dragon_controls",
                    mc.options.keyJump.getTranslatedKeyMessage(), mc.options.keyShift.getTranslatedKeyMessage(),
                    mc.options.keyAttack.getTranslatedKeyMessage()), false);
        }
        // the dragon's neck fills a first-person view, so getting on switches to the view from behind (F5 still changes it),
        // and getting off puts back the view you had
        if (riding && viewBeforeRiding == null) {
            viewBeforeRiding = mc.options.getCameraType();
            if (viewBeforeRiding.isFirstPerson()) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (!riding && viewBeforeRiding != null) {
            if (mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(viewBeforeRiding);
            viewBeforeRiding = null;
        }
    }

    /** In third person on a dragon, the camera sits further back (as far as the blocks around allow), so you see the whole dragon. */
    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Camera camera = event.getCamera();
        if (MAX_ZOOM == null || MOVE == null || !camera.isDetached() || !(camera.getEntity().getVehicle() instanceof AbstractDragon dragon)) return;
        Vec3 eye = camera.getEntity().getEyePosition((float) event.getPartialTick());
        if (camera.getPosition().distanceTo(eye) < 3.9) return;        // a wall already pulled it in
        try {
            double extra = (double) MAX_ZOOM.invoke(camera, 6.0 * dragon.getSizeScale());
            MOVE.invoke(camera, -extra, 0.0, 0.0);
        } catch (ReflectiveOperationException ignored) {}
    }
}
