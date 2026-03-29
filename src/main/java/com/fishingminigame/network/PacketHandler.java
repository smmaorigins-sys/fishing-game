package com.fishingminigame.network;

import com.fishingminigame.FishingMinigame;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FishingMinigame.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static int id = 0;

    public static void register() {
        // Server -> Client: open minigame screen
        CHANNEL.registerMessage(id++, OpenMinigamePacket.class,
                OpenMinigamePacket::encode,
                OpenMinigamePacket::decode,
                OpenMinigamePacket::handle);

        // Client -> Server: minigame result (how many catches)
        CHANNEL.registerMessage(id++, MinigameResultPacket.class,
                MinigameResultPacket::encode,
                MinigameResultPacket::decode,
                MinigameResultPacket::handle);
    }
}
