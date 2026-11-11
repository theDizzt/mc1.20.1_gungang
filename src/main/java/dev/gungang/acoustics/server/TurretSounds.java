package dev.gungang.acoustics.server;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.*;

public final class TurretSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS=DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,"gungang");
    public static final RegistryObject<SoundEvent> SHOT=sound("turret_shot");
    /** Heal cues: start (0.7 s), a 2 s loop while a heal runs, and end (0.85 s). */
    public static final RegistryObject<SoundEvent> HEAL_START=sound("heal_start"),HEAL_LOOP=sound("heal_loop"),HEAL_END=sound("heal_end");
    public static final RegistryObject<SoundEvent> STIM_START=sound("stim_start");
    /** A secondary's cooldown is over (played on the owner's client only, client/CooldownCue). */
    public static final RegistryObject<SoundEvent> COOLDOWN_READY=sound("cooldown_ready");
    private static RegistryObject<SoundEvent> sound(String id){return SOUNDS.register(id,()->SoundEvent.createVariableRangeEvent(new ResourceLocation("gungang",id)));}
    public static void register(net.minecraftforge.eventbus.api.IEventBus bus){SOUNDS.register(bus);}
    private TurretSounds(){}
}
