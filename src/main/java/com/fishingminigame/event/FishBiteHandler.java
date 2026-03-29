package com.fishingminigame.event;

import com.fishingminigame.FishingMinigame;
import com.fishingminigame.network.OpenMinigamePacket;
import com.fishingminigame.network.PacketHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FishBiteHandler {

    // Map: playerUUID -> pending catch count from minigame result
    // -1 means minigame hasn't been played yet (still pending)
    public static final Map<UUID, Integer> pendingCatches = new HashMap<>();

    // Tracks which hooks we've already opened a minigame for, so we don't double-trigger
    private static final Map<Integer, Boolean> openedMinigames = new HashMap<>();

    @SubscribeEvent
    public void onItemFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        FishingHook hook = event.getHookEntity();
        if (hook == null) return;

        UUID uuid = player.getUUID();
        int hookId = hook.getId();

        // Check if this is the post-minigame retrieval
        if (pendingCatches.containsKey(uuid)) {
            int catches = pendingCatches.remove(uuid);
            openedMinigames.remove(hookId);

            if (catches == 0) {
                // Player missed — cancel all drops
                event.setCanceled(true);
                return;
            }

            if (catches == 1) {
                // Normal: let vanilla handle it (1 catch) — don't cancel
                return;
            }

            // catches 2-4: let vanilla give the first one, then manually give extras
            // We get the drops vanilla already prepared, then duplicate them (catches-1) more times
            List<ItemStack> drops = event.getDrops();

            for (int extra = 1; extra < catches; extra++) {
                for (ItemStack stack : drops) {
                    ItemStack copy = stack.copy();
                    double x = player.getX();
                    double y = player.getY();
                    double z = player.getZ();
                    ItemEntity itemEntity = new ItemEntity(player.level(), x, y, z, copy);
                    itemEntity.setDeltaMovement(
                            (Math.random() - 0.5) * 0.1,
                            0.2,
                            (Math.random() - 0.5) * 0.1
                    );
                    player.level().addFreshEntity(itemEntity);
                }
            }
            return;
        }

        // First-time bite for this hook: pause it and open minigame on client
        if (!openedMinigames.containsKey(hookId)) {
            openedMinigames.put(hookId, true);
            // Cancel the normal loot — we'll re-trigger after minigame
            event.setCanceled(true);

            // Tell client to open the minigame
            PacketHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new OpenMinigamePacket(hookId)
            );

            FishingMinigame.LOGGER.debug("Sent OpenMinigamePacket to {} for hook {}", player.getName().getString(), hookId);
        }
    }
}
