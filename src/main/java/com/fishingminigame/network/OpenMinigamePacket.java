package com.fishingminigame.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import com.fishingminigame.client.MinigameScreen;

import java.util.function.Supplier;

public class OpenMinigamePacket {
    private final int bobberEntityId;

    public OpenMinigamePacket(int bobberEntityId) {
        this.bobberEntityId = bobberEntityId;
    }

    public static void encode(OpenMinigamePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.bobberEntityId);
    }

    public static OpenMinigamePacket decode(FriendlyByteBuf buf) {
        return new OpenMinigamePacket(buf.readInt());
    }

    public static void handle(OpenMinigamePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Must run on client thread
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new MinigameScreen(msg.bobberEntityId));
        });
        ctx.get().setPacketHandled(true);
    }
}
