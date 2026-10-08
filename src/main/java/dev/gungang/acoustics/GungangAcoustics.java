package dev.gungang.acoustics;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(GungangAcoustics.ID)
public final class GungangAcoustics {
    public static final String ID = "gungang_acoustics";

    public GungangAcoustics() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, AcousticConfig.SPEC);
    }
}
