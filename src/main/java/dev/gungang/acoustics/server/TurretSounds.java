package dev.gungang.acoustics.server;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.*;

public final class TurretSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,"gungang");
    public static final RegistryObject<SoundEvent> SHOT=SOUNDS.register("turret_shot",()->SoundEvent.createVariableRangeEvent(new ResourceLocation("gungang","turret_shot")));
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus){SOUNDS.register(bus);}
    private TurretSounds(){}
}
