package com.wildcreatures.network;

import com.wildcreatures.WildCreatures;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** The mod's own messages between the game and the server (so far just the rider's "breathe!" key). */
public final class ModNetwork {
    private ModNetwork() {}

    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(WildCreatures.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        CHANNEL.messageBuilder(RiderBreathPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RiderBreathPacket::encode)
                .decoder(RiderBreathPacket::decode)
                .consumerMainThread(RiderBreathPacket::handle)
                .add();
    }
}
