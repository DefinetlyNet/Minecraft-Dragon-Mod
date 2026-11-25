package com.wildcreatures.network;

import com.wildcreatures.entity.AbstractDragon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Sent when a player riding a dragon presses the attack key: the dragon breathes where they're looking. */
public record RiderBreathPacket() {
    public void encode(FriendlyByteBuf buf) {}

    public static RiderBreathPacket decode(FriendlyByteBuf buf) { return new RiderBreathPacket(); }

    public static void handle(RiderBreathPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.getVehicle() instanceof AbstractDragon dragon) dragon.riderBreath(player);
    }
}
