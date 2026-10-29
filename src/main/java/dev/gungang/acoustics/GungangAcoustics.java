package dev.gungang.acoustics;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(GungangAcoustics.ID)
public final class GungangAcoustics {
    public static final String ID = "gungang_acoustics";

    public GungangAcoustics() {
        dev.gungang.acoustics.server.PropExtraBlocks.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        dev.gungang.acoustics.server.MotionBlocks.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        dev.gungang.acoustics.server.MotionParticles.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        dev.gungang.acoustics.server.BotEntity.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        dev.gungang.acoustics.server.PropEntity.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        dev.gungang.acoustics.server.EscortBlocks.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        dev.gungang.acoustics.server.GungangCreativeTabs.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        dev.gungang.acoustics.server.GimmickBlocks.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        dev.gungang.acoustics.server.GimmickEvents.registerTacz();
        dev.gungang.acoustics.server.StationBlocks.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        dev.gungang.acoustics.server.GadgetItems.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, AcousticConfig.SPEC);
        dev.gungang.acoustics.server.GungangNetwork.register();
        dev.gungang.acoustics.server.SecondaryRuntime.registerTacz();
        dev.gungang.acoustics.server.BotManager.registerTacz();
        dev.gungang.acoustics.server.SafeZones.registerTacz();
        dev.gungang.acoustics.server.SpawnGateProtection.registerTacz();
    }
}
