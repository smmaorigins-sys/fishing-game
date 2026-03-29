package com.fishingminigame;

import com.fishingminigame.event.FishBiteHandler;
import com.fishingminigame.network.PacketHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(FishingMinigame.MOD_ID)
public class FishingMinigame {
    public static final String MOD_ID = "fishingminigame";
    public static final Logger LOGGER = LogManager.getLogger();

    public FishingMinigame() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(new FishBiteHandler());
    }

    private void setup(final FMLCommonSetupEvent event) {
        PacketHandler.register();
    }
}
