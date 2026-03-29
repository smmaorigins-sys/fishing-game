package com.fishingminigame.network;

import com.fishingminigame.event.FishBiteHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MinigameResultPacket {
    private final int catches;     // 0-4
    private final int bobberEntityId;

    public MinigameResultPacket(int catches, int bobberEntityId) {
        this.catches = catches;
        this.bobberEntityId = bobberEntityId;
    }

    public static void encode(MinigameResultPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.catches);
        buf.writeInt(msg.bobberEntityId);
    }

    public static MinigameResultPacket decode(FriendlyByteBuf buf) {
        return new MinigameResultPacket(buf.readInt(), buf.readInt());
    }

    public static void handle(MinigameResultPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Entity entity = player.level().getEntity(msg.bobberEntityId);
            if (!(entity instanceof FishingHook hook)) return;
            if (hook.getPlayerOwner() != player) return;

            // Mark the hook so our event handler knows the minigame is done
            // and how many times to award loot
            FishBiteHandler.pendingCatches.put(player.getUUID(), msg.catches);

            // Now actually retrieve the hook — this fires vanilla loot logic
            // The FishBiteHandler.onRetrieve will intercept and repeat N times
            hook.retrieve(player.getMainHandItem());
        });
        ctx.get().setPacketHandled(true);
    }
}
